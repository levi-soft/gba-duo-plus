package com.khumam.gbaduo.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Private, immutable image files. Decode only on a worker thread. */
class ArtworkImageStore(private val directory: File) {
    data class StoredImage(val name: String, val width: Int, val height: Int)

    fun write(bytes: ByteArray): StoredImage {
        val bitmap = decode(bytes)
        try {
            val encoded = ByteArrayOutputStream().use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)) { "Could not encode artwork" }
                output.toByteArray()
            }
            require(encoded.size <= MAX_STORED_BYTES) { "Encoded artwork is too large" }
            require(directory.isDirectory || directory.mkdirs()) { "Could not create artwork storage" }
            val name = UUID.randomUUID().toString() + ".webp"
            val file = File(directory, name)
            try {
                FileOutputStream(file).use { output -> output.write(encoded); output.fd.sync() }
                return StoredImage(name, bitmap.width, bitmap.height)
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun load(name: String): Bitmap? {
        val file = file(name) ?: return null
        if (!file.isFile || file.length() !in 1..MAX_STORED_BYTES.toLong()) return null
        return runCatching { decode(file.readBytes()) }.getOrNull()
    }

    fun contains(name: String): Boolean = file(name)?.isFile == true
    fun remove(name: String) { file(name)?.delete() }

    private fun file(name: String): File? = if (Regex("^[a-f0-9-]{36}\\.webp$").matches(name)) File(directory, name) else null

    companion object {
        const val MAX_INPUT_BYTES = 16 * 1024 * 1024
        const val MAX_STORED_BYTES = 5 * 1024 * 1024
        const val MAX_SIDE = 1024
        private const val MAX_SOURCE_SIDE = 8192
        private const val MAX_SOURCE_PIXELS = 24L * 1024 * 1024

        fun decode(bytes: ByteArray): Bitmap {
            require(bytes.size in 1..MAX_INPUT_BYTES) { "Artwork must be a PNG, JPEG or WebP under 16 MiB" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outMimeType in setOf("image/png", "image/jpeg", "image/webp")) { "Choose a PNG, JPEG or WebP image" }
            require(bounds.outWidth in 1..MAX_SOURCE_SIDE && bounds.outHeight in 1..MAX_SOURCE_SIDE &&
                bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS) { "Artwork dimensions are too large" }
            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Could not decode artwork" }
            val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
            if (scale >= 1f) return decoded
            return try {
                Bitmap.createScaledBitmap(decoded, max(1, (decoded.width * scale).roundToInt()), max(1, (decoded.height * scale).roundToInt()), true)
            } finally {
                decoded.recycle()
            }
        }
    }
}
