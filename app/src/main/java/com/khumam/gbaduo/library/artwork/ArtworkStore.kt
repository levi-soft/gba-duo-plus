package com.khumam.gbaduo.library.artwork

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ArtworkMeta(val source: String, val ref: String, val at: Long, val details: String? = null)

/** Immutable images and one atomically published index shared by all artwork providers. */
class ArtworkStore(private val root: File) {
    private val lock = locks.getOrPut(root.absolutePath) { Any() }
    private val indexFile = File(root, "index.json")
    private fun read(): JSONObject = if (indexFile.isFile) runCatching { JSONObject(indexFile.readText()) }.getOrElse {
        // Keep the damaged original available for recovery before starting a fresh index.
        val backup = File(root, "index.corrupt.json")
        if (!backup.exists()) indexFile.copyTo(backup)
        JSONObject()
    } else JSONObject()
    private fun key(platform: String, crc: Long): String {
        require(platform.matches(Regex("[a-z0-9_-]{1,32}")) && crc in 0..0xffffffffL)
        return "$platform/${ArtworkNames.crcName(crc)}"
    }
    private fun selected(json: JSONObject, platform: String, crc: Long): File? {
        val entry = json.optJSONObject("items")?.optJSONObject(key(platform, crc)) ?: return null
        val name = entry.optString("image").ifEmpty { "${ArtworkNames.crcName(crc)}.img" }
        require(name.matches(Regex("[A-F0-9]{8}(?:-[a-f0-9-]{36})?\\.img")))
        return File(File(root, platform), name).takeIf { it.isFile }
    }
    fun meta(platform: String, crc32: Long): ArtworkMeta? = synchronized(lock) {
        val entry = read().optJSONObject("items")?.optJSONObject(key(platform, crc32)) ?: return@synchronized null
        ArtworkMeta(entry.optString("source"), entry.optString("ref"), entry.optLong("at"), entry.optString("details").takeIf { it.isNotEmpty() })
    }
    fun file(platform: String, crc32: Long): File? = synchronized(lock) { selected(read(), platform, crc32) }
    fun wasRemoved(platform: String, crc32: Long): Boolean = synchronized(lock) {
        val json = read()
        json.optBoolean("clearedAll") || json.optJSONObject("removed")?.optBoolean(key(platform, crc32)) == true
    }
    fun put(platform: String, crc32: Long, bytes: ByteArray, source: String, ref: String, now: Long = System.currentTimeMillis()) {
        check(putIfOwned(platform, crc32, bytes, source, ref, now))
    }
    fun putIfOwned(platform: String, crc32: Long, bytes: ByteArray, source: String, ref: String,
        now: Long = System.currentTimeMillis(), details: String? = null, onlyMissing: Boolean = false,
        claim: () -> Boolean = { true }): Boolean {
        val id = key(platform, crc32)
        synchronized(lock) { if (onlyMissing && selected(read(), platform, crc32) != null) return false }
        val image = File(File(root, platform), "${ArtworkNames.crcName(crc32)}-${UUID.randomUUID()}.img")
        var staged: File? = null
        var committed = false
        try {
            image.parentFile!!.mkdirs()
            FileOutputStream(image).use { it.write(bytes); it.fd.sync() }
            return synchronized(lock) {
                val json = read()
                val old = selected(json, platform, crc32)
                if (onlyMissing && old != null) return@synchronized false
                val items = json.optJSONObject("items") ?: JSONObject().also { json.put("items", it) }
                items.put(id, JSONObject().put("image", image.name).put("source", source).put("ref", ref).put("at", now).put("details", details))
                json.optJSONObject("removed")?.remove(id)
                json.put("generation", json.optLong("generation") + 1)
                staged = stage(json)
                if (!claim()) return@synchronized false
                publish(requireNotNull(staged))
                committed = true
                old?.let { runCatching { it.delete() } }
                true
            }
        } finally {
            staged?.delete()
            if (!committed) image.delete()
        }
    }
    fun remove(platform: String, crc32: Long) = synchronized(lock) {
        val json = read(); val id = key(platform, crc32); val old = selected(json, platform, crc32)
        json.optJSONObject("items")?.remove(id)
        val removed = json.optJSONObject("removed") ?: JSONObject().also { json.put("removed", it) }
        removed.put(id, true)
        json.put("generation", json.optLong("generation") + 1)
        write(json)
        old?.let { runCatching { it.delete() } }
        Unit
    }
    fun removeAll() = synchronized(lock) {
        val json = read()
        val items = json.optJSONObject("items")
        val files = items?.keys()?.asSequence()?.mapNotNull { id ->
            val parts = id.split('/')
            runCatching { selected(json, parts[0], parts[1].toLong(16)) }.getOrNull()
        }?.toList().orEmpty()
        write(JSONObject().put("generation", json.optLong("generation") + 1).put("clearedAll", true))
        files.forEach { runCatching { it.delete() } }
    }
    fun count(): Int = synchronized(lock) { read().optJSONObject("items")?.length() ?: 0 }
    fun generation(): Long = synchronized(lock) { read().optLong("generation") }
    private fun stage(json: JSONObject): File {
        root.mkdirs()
        val tmp = File(root, "index-${UUID.randomUUID()}.tmp")
        try { FileOutputStream(tmp).use { it.write(json.toString().toByteArray()); it.fd.sync() } }
        catch (e: Exception) { tmp.delete(); throw e }
        return tmp
    }
    private fun publish(tmp: File) { Files.move(tmp.toPath(), indexFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
    private fun write(json: JSONObject) { val tmp = stage(json); try { publish(tmp) } finally { tmp.delete() } }
    companion object { private val locks = ConcurrentHashMap<String, Any>() }
}
