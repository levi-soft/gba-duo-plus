package com.khumam.gbaduo.achievements

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import com.khumam.gbaduo.R
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** Public badge images only. Account tokens never enter URLs or the disk cache. */
class AchievementImages(private val directory: File) {
    private val workers = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    fun load(view: ImageView, address: String) {
        view.setImageResource(R.drawable.retroachievements_mark)
        view.tag = address
        if (closed || address.isBlank()) return
        workers.execute {
            val bitmap = runCatching {
                val url = URL(address)
                require(url.protocol == "https" && url.host == "media.retroachievements.org")
                require(url.port in listOf(-1, 443) && url.userInfo == null && url.query == null)
                require((url.path.startsWith("/Badge/") || url.path.startsWith("/Images/")) && url.path.endsWith(".png"))
                directory.mkdirs()
                val key = MessageDigest.getInstance("SHA-256").digest(address.toByteArray()).joinToString("") { "%02x".format(it) }
                val file = File(directory, "$key.png")
                if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return@runCatching it }
                val connection = url.openConnection() as HttpsURLConnection
                val data = try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 8000; connection.readTimeout = 8000
                    require(connection.responseCode == 200)
                    connection.inputStream.use { it.readBytesBounded() }
                } finally { connection.disconnect() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                require(bounds.outWidth in 1..512 && bounds.outHeight in 1..512)
                val decoded = requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size))
                // Cache is disposable and capped; avoid accumulating every game's badges forever.
                if (directory.listFiles()?.sumOf { it.length() } ?: 0L > 16 * 1024 * 1024) directory.listFiles()?.forEach { it.delete() }
                file.writeBytes(data)
                decoded
            }.getOrNull()
            if (!closed && bitmap != null) main.post { if (!closed && view.tag == address) view.setImageBitmap(bitmap) }
        }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer); if (count < 0) break
            require(output.size() + count <= 512 * 1024)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun close() { closed = true; workers.shutdownNow() }
}
