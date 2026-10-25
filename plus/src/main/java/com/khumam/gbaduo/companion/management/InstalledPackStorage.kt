package com.khumam.gbaduo.companion.management

import com.khumam.gbaduo.companion.PackManifest
import com.khumam.gbaduo.companion.builder.BuilderFiles
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Removes a pack and its current/legacy assignments, never game saves or ROMs. */
class InstalledPackStorage(private val filesDir: File) {
    fun delete(id: String, cleanup: (File) -> Unit = { it.deleteRecursively() }) {
        require(PackManifest.isValidId(id)) { "Invalid companion ID" }
        val root = File(filesDir, "packs").canonicalFile
        val target = File(root, id)
        require(target.canonicalFile == target.absoluteFile && target.parentFile == root) { "Unsafe companion folder" }
        require(target.isDirectory) { "Companion is no longer installed" }
        val changes = linkedMapOf<File, Pair<ByteArray, ByteArray>>()
        val games = File(filesDir, "games")
        for (platform in games.listFiles().orEmpty().filter { it.isDirectory }) {
            for (game in platform.listFiles().orEmpty().filter { it.isDirectory }) {
                val assignment = File(game, "pack.json")
                if (!assignment.isFile) continue
                require(assignment.canonicalPath.startsWith(games.canonicalPath + File.separator)) { "Unsafe game assignment" }
                val bytes = assignment.readBytes()
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                if (json.optString("packId") == id) {
                    json.remove("packId")
                    changes[assignment] = bytes to json.toString().toByteArray(Charsets.UTF_8)
                }
            }
        }
        val legacy = File(filesDir, "pack_assignments.json")
        if (legacy.isFile) {
            val bytes = legacy.readBytes()
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            val matches = json.keys().asSequence().filter { json.optString(it) == id }.toList()
            if (matches.isNotEmpty()) {
                matches.forEach(json::remove)
                changes[legacy] = bytes to json.toString().toByteArray(Charsets.UTF_8)
            }
        }
        val retired = File(root, ".deleted-${UUID.randomUUID()}")
        check(target.renameTo(retired)) { "Could not remove companion folder" }
        val written = mutableListOf<File>()
        try {
            for ((file, versions) in changes) {
                BuilderFiles.atomicWrite(file, versions.second)
                written += file
            }
        } catch (error: Exception) {
            written.asReversed().forEach { BuilderFiles.atomicWrite(it, changes.getValue(it).first) }
            check(retired.renameTo(target)) { "Could not restore companion folder" }
            throw error
        }
        cleanup(retired)
    }

    companion object {
        fun labels(packs: List<PackManifest>): List<String> {
            val counts = packs.groupingBy { it.name to it.version }.eachCount()
            return packs.map { pack ->
                val label = "${pack.name} · v${pack.version}"
                if (counts.getValue(pack.name to pack.version) > 1) "$label\n${pack.id}" else label
            }
        }
    }
}
