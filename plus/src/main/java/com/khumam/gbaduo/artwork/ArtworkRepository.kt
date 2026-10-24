package com.khumam.gbaduo.artwork

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Selected artwork is durable user data; thumbnails and provider credentials live elsewhere. */
class ArtworkRepository(context: Context) {
    private val root = File(context.applicationContext.filesDir, "plus-artwork")
    private val records = File(root, "records")
    private val imageDirectory = File(root, "images")
    private val images = ArtworkImageStore(imageDirectory)
    private val lock = locks.getOrPut(root.absolutePath) { Any() }

    fun record(identity: ArtworkIdentity): ArtworkRecord? = synchronized(lock) { read(identity) }
    fun has(identity: ArtworkIdentity): Boolean = synchronized(lock) { read(identity) != null }
    fun hasAnyArtwork(): Boolean = synchronized(lock) { records.listFiles()?.any { it.isFile && it.extension == "json" } == true }
    fun loadBitmap(identity: ArtworkIdentity) = synchronized(lock) { read(identity)?.let { images.load(it.imageFile) } }

    /** Batch scraping must not replace a cover selected while its download was in flight. */
    fun applyIfMissing(identity: ArtworkIdentity, bytes: ByteArray, candidate: ArtworkCandidate? = null, claim: () -> Boolean = { true }): ArtworkRecord? =
        save(identity, bytes, candidate, manual = false, onlyMissing = true, claim)

    fun apply(identity: ArtworkIdentity, bytes: ByteArray, candidate: ArtworkCandidate? = null, manual: Boolean = false): ArtworkRecord =
        requireNotNull(save(identity, bytes, candidate, manual, onlyMissing = false) { true })

    /** Stages and fsyncs both files before asking whether this request still owns the commit. */
    fun applyIfOwned(identity: ArtworkIdentity, bytes: ByteArray, candidate: ArtworkCandidate? = null, manual: Boolean = false, claim: () -> Boolean): ArtworkRecord? =
        save(identity, bytes, candidate, manual, onlyMissing = false, claim)

    private fun save(identity: ArtworkIdentity, bytes: ByteArray, candidate: ArtworkCandidate?, manual: Boolean, onlyMissing: Boolean, claim: () -> Boolean): ArtworkRecord? {
        synchronized(lock) {
            // Keep the existing fast path, including not decoding a batch image
            // when a manual choice already exists. Directory creation is serialized.
            if (onlyMissing && read(identity) != null) return null
            require(imageDirectory.isDirectory || imageDirectory.mkdirs()) { "Could not create artwork storage" }
        }
        val image = images.write(bytes)
        var temporary: File? = null
        var committed = false
        try {
            val record = ArtworkRecord(identity, image.name,
                candidate?.provider?.takeIf { Regex("^[a-z][a-z0-9-]{0,31}$").matches(it) },
                clean(candidate?.id, 160), publicPage(candidate?.sourcePage), clean(candidate?.author, 160),
                manual, image.width, image.height, System.currentTimeMillis())
            require(records.isDirectory || records.mkdirs() || records.isDirectory) { "Could not create artwork metadata storage" }
            val destination = File(records, identity.key + ".json")
            val staged = File(records, identity.key + "." + UUID.randomUUID() + ".tmp")
            temporary = staged
            FileOutputStream(staged).use { output -> output.write(encode(record).toByteArray(Charsets.UTF_8)); output.fd.sync() }
            return synchronized(lock) {
                val old = read(identity)
                // Manual artwork committed during preparation takes precedence.
                if (onlyMissing && old != null) return@synchronized null
                if (!claim()) return@synchronized null
                // Both paths are in private storage on the same filesystem. If atomic
                // replacement is unavailable, fail instead of losing the selected image.
                // Claim precedes publication: a later cancellation cannot split this transaction.
                Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                committed = true
                // The pointer is committed. Cleanup must never undo a valid assignment.
                old?.let { if (it.imageFile != image.name) runCatching { images.remove(it.imageFile) } }
                record
            }
        } finally {
            temporary?.delete()
            if (!committed) images.remove(image.name)
        }
    }

    fun remove(identity: ArtworkIdentity) = synchronized(lock) {
        val old = read(identity)
        val file = File(records, identity.key + ".json")
        if (file.exists()) check(file.delete()) { "Could not remove selected artwork" }
        old?.let { images.remove(it.imageFile) }
        Unit
    }

    private fun read(identity: ArtworkIdentity): ArtworkRecord? = runCatching {
        val file = File(records, identity.key + ".json")
        if (!file.isFile || file.length() > 8192) return@runCatching null
        val json = JSONObject(file.readText())
        require(json.getString("identity") == identity.key)
        val image = json.getString("image")
        require(images.contains(image))
        ArtworkRecord(identity, image, optional(json, "provider"), optional(json, "artworkId"),
            publicPage(optional(json, "sourcePage")), clean(optional(json, "author"), 160),
            json.optBoolean("manual"), json.getInt("width"), json.getInt("height"), json.optLong("updatedAt"))
    }.getOrNull()

    private fun encode(record: ArtworkRecord): String = JSONObject()
        .put("identity", record.identity.key).put("image", record.imageFile)
        .put("provider", record.provider ?: JSONObject.NULL).put("artworkId", record.artworkId ?: JSONObject.NULL)
        .put("sourcePage", record.sourcePage ?: JSONObject.NULL).put("author", record.author ?: JSONObject.NULL)
        .put("manual", record.manual).put("width", record.width).put("height", record.height).put("updatedAt", record.updatedAt).toString()

    private fun optional(json: JSONObject, name: String): String? = if (json.isNull(name)) null else json.optString(name).takeIf(String::isNotBlank)
    private fun clean(text: String?, limit: Int): String? = text?.filter { it.code >= 32 && it.code != 127 }?.take(limit)?.trim()?.takeIf(String::isNotEmpty)
    private fun publicPage(text: String?): String? = runCatching {
        if (text == null) return@runCatching null
        val uri = URI(text)
        require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null && uri.port in listOf(-1, 443))
        URI("https", null, uri.host, -1, uri.path, null, null).toString().take(1024)
    }.getOrNull()

    companion object {
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
