package com.khumam.gbaduo.companion.builder

import com.khumam.gbaduo.companion.PackInstaller
import com.khumam.gbaduo.companion.PackManifest
import com.khumam.gbaduo.companion.LayoutParser
import com.khumam.gbaduo.storage.GameStorage
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object GeneratedPackTransaction {
    const val MAX_ZIP_BYTES = 64 * 1024 * 1024L
    fun install(zip: File, packsDir: File, storage: GameStorage, gameCode: String, crc32: Long, expectedAssignment: String?, token: BuildCancellation, writeAssignmentTemporary: ((File, ByteArray) -> Unit)? = null, commitOnOwnerThread: (() -> Unit) -> Unit = { it() }): PackManifest {
        token.check(); require(zip.isFile && zip.length() in 1..MAX_ZIP_BYTES) { "Invalid generated companion size" }
        require(storage.packId == expectedAssignment) { "Companion selection changed while building; nothing was replaced" }
        val work = File(zip.parentFile, ".generated-${UUID.randomUUID()}")
        val installedId = "auto-${UUID.randomUUID()}"; var installed = false
        val assignment = File(storage.dir, "pack.json")
        try {
            BuilderFiles.extract(zip, work, 256 * 1024 * 1024L, 5000, token)
            val manifestFile = File(work, "manifest.json"); require(manifestFile.isFile && manifestFile.length() <= 256 * 1024) { "Generated manifest is missing or too large" }
            val json = JSONObject(manifestFile.readText()); json.put("id", installedId)
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
            require(!File(packsDir, installedId).exists()) { "Generated companion id already exists" }
            val result = PackInstaller.install(rewritten, packsDir); installed = true
            // The Android host executes this on its UI thread: library manual
            // assignments and Activity cancellation are serialized with commit.
            commitOnOwnerThread {
                token.commit {
                    require(storage.packId == expectedAssignment) { "Companion selection changed while building" }
                    // The upstream setter truncates the live file. Complete and sync
                    // a sibling first, so failed writes need no allocation for rollback.
                    BuilderFiles.atomicWrite(assignment, JSONObject().put("packId", result.id).toString().toByteArray(Charsets.UTF_8), writeAssignmentTemporary)
                }
            }
            return result
        } catch (error: Exception) {
            if (installed && !token.committed) File(packsDir, installedId).deleteRecursively()
            throw error
        } finally { work.deleteRecursively() }
    }
}
