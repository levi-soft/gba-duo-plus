package com.khumam.gbaduo.companion.builder

import com.khumam.gbaduo.companion.PackInstaller
import com.khumam.gbaduo.companion.PackManifest
import com.khumam.gbaduo.companion.PackStore
import com.khumam.gbaduo.companion.LayoutParser
import com.khumam.gbaduo.storage.GameStorage
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object GeneratedPackTransaction {
    const val MAX_ZIP_BYTES = 64 * 1024 * 1024L
    fun install(zip: File, packsDir: File, storage: GameStorage, gameCode: String, crc32: Long, expectedAssignment: String?, token: BuildCancellation, assignToGame: Boolean = true, writeAssignmentTemporary: ((File, ByteArray) -> Unit)? = null, commitOnOwnerThread: (() -> Unit) -> Unit = { it() }): PackManifest {
        token.check(); require(zip.isFile && zip.length() in 1..MAX_ZIP_BYTES) { "Invalid generated companion size" }
        require(storage.packId == expectedAssignment) { "Companion selection changed while building; nothing was replaced" }
        val work = File(zip.parentFile, ".generated-${UUID.randomUUID()}")
        var backup: File? = null
        var installedDirectory: File? = null
        val assignment = File(storage.dir, "pack.json")
        try {
            BuilderFiles.extract(zip, work, 256 * 1024 * 1024L, 5000, token)
            val manifestFile = File(work, "manifest.json"); require(manifestFile.isFile && manifestFile.length() <= 256 * 1024) { "Generated manifest is missing or too large" }
            val json = JSONObject(manifestFile.readText())
            val sourceId = json.getString("id")
            // Reuse the selected legacy automatic pack; manual/custom IDs stay separate.
            val previous = expectedAssignment?.let { PackStore(packsDir).byId(it) }
            val legacyAutomatic = expectedAssignment?.matches(Regex("auto-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) == true &&
                previous?.gameCodes == listOf(gameCode) && previous.crc32s == listOf(crc32) && previous.permissions.isEmpty()
            val identity = "$sourceId:$gameCode:${crc32.toString(16)}"
            val installedId = if (legacyAutomatic) expectedAssignment!! else "auto-${BuilderFiles.sha256(identity.toByteArray()).take(32)}"
            json.put("id", installedId)
            val manifest = PackManifest.parse(json.toString())
            require(manifest.gameCodes == listOf(gameCode) && manifest.crc32s == listOf(crc32)) { "Generated companion does not match this exact ROM" }
            require(manifest.permissions.isEmpty()) { "Automatic companions cannot request game-write permissions" }
            for (path in listOfNotNull(manifest.layoutFile, manifest.scriptFile) + manifest.styleFiles) require(BuilderFiles.safeFile(work, path).isFile) { "Generated companion file is missing" }
            val layout = BuilderFiles.safeFile(work, manifest.layoutFile)
            require(layout.length() <= 8 * 1024 * 1024) { "Generated layout exceeds size limit" }
            LayoutParser.parseWithWarnings(layout.readText())
            BuilderFiles.atomicWrite(manifestFile, json.toString().toByteArray())
            val rewritten = File(work, ".install.zip")
            ZipOutputStream(rewritten.outputStream()).use { out ->
                work.walkTopDown().filter { it.isFile && it != rewritten }.forEach { file ->
                    token.check(); out.putNextEntry(ZipEntry(file.relativeTo(work).invariantSeparatorsPath)); file.inputStream().use { it.copyTo(out) }; out.closeEntry()
                }
            }
            token.check(); require(storage.packId == expectedAssignment) { "Companion selection changed while building" }
            // Extraction/validation stays off the UI thread. Only renames and the
            // assignment commit are serialized with library import/delete actions.
            val staged = File(work, "staged")
            val result = PackInstaller.install(rewritten, staged)
            val prepared = File(staged, installedId)
            val target = File(packsDir, installedId)
            val owner = File(target, ".plus-generated-source")
            // The Android host executes this on its UI thread: library manual
            // assignments and Activity cancellation are serialized with commit.
            commitOnOwnerThread {
                token.commit {
                    require(storage.packId == expectedAssignment) { "Companion selection changed while building" }
                    require(!target.exists() || legacyAutomatic || (owner.isFile && owner.readText() == identity)) {
                        "An unrelated companion already uses this ID; nothing was replaced"
                    }
                    require(packsDir.isDirectory || packsDir.mkdirs()) { "Could not create companion folder" }
                    try {
                        if (target.exists()) {
                            val saved = File(packsDir, ".replaced-${UUID.randomUUID()}")
                            require(target.renameTo(saved)) { "Could not preserve the previous companion" }
                            backup = saved
                        }
                        require(prepared.renameTo(target)) { "Could not install generated companion" }
                        installedDirectory = target
                        File(target, ".plus-generated-source").writeText(identity)
                        // Catalog creation returns the installed ID to its parent,
                        // which alone may assign it after accepting the result.
                        if (assignToGame) BuilderFiles.atomicWrite(assignment, JSONObject().put("packId", result.id).toString().toByteArray(Charsets.UTF_8), writeAssignmentTemporary)
                    } catch (error: Exception) {
                        // Roll back before releasing the owner thread, so another
                        // import/delete cannot interleave with restoring this pack.
                        installedDirectory?.deleteRecursively()
                        backup?.let { check(it.renameTo(target)) { "Could not restore previous companion" } }
                        installedDirectory = null
                        backup = null
                        throw error
                    }
                }
            }
            check(token.committed) { "Companion installation was not committed" }
            return result
        } catch (error: Exception) {
            if (!token.committed) {
                installedDirectory?.deleteRecursively()
                backup?.let { saved ->
                    val originalId = JSONObject(File(saved, "manifest.json").readText()).getString("id")
                    check(saved.renameTo(File(packsDir, originalId))) { "Could not restore companion backup: ${saved.name}" }
                }
            }
            throw error
        } finally {
            if (token.committed) backup?.deleteRecursively()
            work.deleteRecursively()
        }
    }
}
