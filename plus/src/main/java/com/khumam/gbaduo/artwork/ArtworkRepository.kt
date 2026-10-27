package com.khumam.gbaduo.artwork

import android.content.Context
import android.graphics.Bitmap
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.library.artwork.Artwork
import com.khumam.gbaduo.storage.GameSettingsKeys
import com.khumam.gbaduo.storage.GameStorage
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/** Plus providers use the same durable cache as per-game settings and folder scraping. */
class ArtworkRepository(context: Context) {
    private val context = context.applicationContext
    private val store = Artwork.store(this.context)
    private val legacy = File(this.context.filesDir, "plus-artwork")
    private val lock = locks.getOrPut(legacy.absolutePath) { Any() }

    fun record(identity: ArtworkIdentity): ArtworkRecord? { migrateLegacy(); return read(identity) }
    fun has(identity: ArtworkIdentity): Boolean = record(identity) != null
    fun hasAnyArtwork(): Boolean { migrateLegacy(); return store.count() > 0 }
    fun loadBitmap(identity: ArtworkIdentity): Bitmap? {
        migrateLegacy()
        val file = store.file(identity.platform, identity.crc32) ?: return null
        if (file.length() !in 1..ArtworkImageStore.MAX_INPUT_BYTES.toLong()) return null
        return runCatching { ArtworkImageStore.decode(file.readBytes()) }.getOrNull()
    }
    fun applyIfMissing(identity: ArtworkIdentity, bytes: ByteArray, candidate: ArtworkCandidate? = null, claim: () -> Boolean = { true }): ArtworkRecord? =
        save(identity, bytes, candidate, false, true, claim)
    fun apply(identity: ArtworkIdentity, bytes: ByteArray, candidate: ArtworkCandidate? = null, manual: Boolean = false): ArtworkRecord =
        requireNotNull(save(identity, bytes, candidate, manual, false) { true })
    fun applyIfOwned(identity: ArtworkIdentity, bytes: ByteArray, candidate: ArtworkCandidate? = null, manual: Boolean = false, claim: () -> Boolean): ArtworkRecord? =
        save(identity, bytes, candidate, manual, false, claim)

    private fun save(identity: ArtworkIdentity, bytes: ByteArray, candidate: ArtworkCandidate?, manual: Boolean, onlyMissing: Boolean, claim: () -> Boolean): ArtworkRecord? {
        migrateLegacy()
        if (onlyMissing && read(identity) != null) return null
        if (onlyMissing && settings(identity).getString(GameSettingsKeys.ARTWORK) != null) return null
        val bitmap = ArtworkImageStore.decode(bytes)
        val now = System.currentTimeMillis()
        val record: ArtworkRecord
        val encoded: ByteArray
        try {
            encoded = ByteArrayOutputStream().use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)) { "Could not encode artwork" }
                output.toByteArray()
            }
            require(encoded.size <= ArtworkImageStore.MAX_STORED_BYTES) { "Encoded artwork is too large" }
            record = ArtworkRecord(identity, "", candidate?.provider?.takeIf { Regex("^[a-z][a-z0-9-]{0,31}$").matches(it) },
                clean(candidate?.id, 160), publicPage(candidate?.sourcePage), clean(candidate?.author, 160), manual, bitmap.width, bitmap.height, now)
        } finally { bitmap.recycle() }
        val saved = store.putIfOwned(identity.platform, identity.crc32, encoded, record.provider ?: "manual", record.artworkId.orEmpty(), now,
            details = encode(record), onlyMissing = onlyMissing,
            claim = { (!onlyMissing || settings(identity).getString(GameSettingsKeys.ARTWORK) == null) && claim() })
        if (!saved) return null
        if (!onlyMissing) settings(identity).put(GameSettingsKeys.ARTWORK, GameSettingsKeys.ARTWORK_PICKED)
        return read(identity)
    }
    fun remove(identity: ArtworkIdentity) {
        migrateLegacy()
        store.remove(identity.platform, identity.crc32)
        settings(identity).put(GameSettingsKeys.ARTWORK, GameSettingsKeys.ARTWORK_NONE)
    }
    private fun settings(identity: ArtworkIdentity) = GameStorage.forGame(context.filesDir, requireNotNull(Platform.byId(identity.platform)), "", "", identity.crc32).settings
    private fun read(identity: ArtworkIdentity): ArtworkRecord? {
        val meta = store.meta(identity.platform, identity.crc32) ?: return null
        val file = store.file(identity.platform, identity.crc32) ?: return null
        val json = meta.details?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        return ArtworkRecord(identity, file.name, optional(json, "provider") ?: meta.source.takeUnless { it == "manual" },
            optional(json, "artworkId") ?: meta.ref.takeIf { it.isNotEmpty() }, publicPage(optional(json, "sourcePage")), clean(optional(json, "author"), 160),
            json.optBoolean("manual"), json.optInt("width"), json.optInt("height"), meta.at)
    }
    /** Copy old selections once. Originals remain available for rollback to Plus.4. */
    private fun migrateLegacy() = synchronized(lock) {
        File(legacy, "records").listFiles()?.filter { it.isFile && it.extension == "json" && it.length() <= 8192 }?.forEach { file ->
            val marker = File(legacy, "migrated-018/${file.name}")
            if (marker.exists()) return@forEach
            runCatching {
                val json = JSONObject(file.readText())
                val match = Regex("(gba|gbc|gb)-([A-F0-9]{8})").matchEntire(json.getString("identity")) ?: return@runCatching
                val identity = ArtworkIdentity(match.groupValues[1], match.groupValues[2].toLong(16))
                val name = json.getString("image")
                require(name.matches(Regex("[a-f0-9-]{36}\\.webp")))
                val image = File(legacy, "images/$name")
                if (store.file(identity.platform, identity.crc32) == null && !store.wasRemoved(identity.platform, identity.crc32)) {
                    require(image.isFile && image.length() in 1..ArtworkImageStore.MAX_STORED_BYTES.toLong())
                    if (store.putIfOwned(identity.platform, identity.crc32, image.readBytes(), optional(json, "provider") ?: "manual", optional(json, "artworkId").orEmpty(),
                        json.optLong("updatedAt"), details = json.toString(), onlyMissing = true)) {
                        settings(identity).put(GameSettingsKeys.ARTWORK, GameSettingsKeys.ARTWORK_PICKED)
                    }
                }
                marker.parentFile!!.mkdirs(); marker.writeText("migrated")
            }
        }
        Unit
    }
    private fun encode(record: ArtworkRecord): String = JSONObject()
        .put("identity", record.identity.key).put("provider", record.provider ?: JSONObject.NULL).put("artworkId", record.artworkId ?: JSONObject.NULL)
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
    companion object { private val locks = ConcurrentHashMap<String, Any>() }
}
