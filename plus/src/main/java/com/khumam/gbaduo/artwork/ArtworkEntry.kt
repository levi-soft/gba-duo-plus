package com.khumam.gbaduo.artwork

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import com.khumam.gbaduo.library.GameTitle
import com.khumam.gbaduo.library.RomEntry
import com.khumam.gbaduo.library.RomHeader
import com.khumam.gbaduo.library.RomLibrary
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/** Cancellation never waits for worker I/O. A claim is the commit linearization point. */
internal class ArtworkRequestGate {
    private data class State(val generation: Long, val closed: Boolean)
    private val state = AtomicReference(State(0, false))

    fun begin(): Long = state.updateAndGet { it.copy(generation = it.generation + 1) }.generation
    fun isCurrent(token: Long): Boolean = state.get().let { !it.closed && token == it.generation }
    fun close() { state.updateAndGet { State(it.generation + 1, true) } }

    /**
     * Call after staging expensive work, immediately before final publication.
     * A claim won before cancellation may finish its atomic storage transaction;
     * cancellation won first rejects it. Neither case locks the UI thread.
     */
    fun claim(token: Long): Boolean {
        while (true) {
            val current = state.get()
            if (current.closed || current.generation != token) return false
            if (state.compareAndSet(current, current.copy())) return true
        }
    }

    /** For worker operations with no separate staging phase (removal/settings). */
    fun <T> commit(token: Long, action: () -> T): T? = if (claim(token)) action() else null
}

/** Bounded streaming identity: ZIP metadata and compressed archive size are not ROM identity. */
internal object ArtworkRomReader {
    data class Result(val fileName: String, val size: Long, val crc32: Long)
    private const val MAX_ROM = 32L * 1024 * 1024
    private const val MAX_SKIPPED = 1024L * 1024

    fun read(fileName: String, input: InputStream, cancelled: () -> Boolean = { false }): Result {
        fun consume(name: String, stream: InputStream): Result {
            val crc = CRC32(); var size = 0L; val buffer = ByteArray(32768)
            while (true) {
                check(!cancelled()) { "Cancelled" }
                val count = stream.read(buffer); if (count < 0) break
                size += count; require(size <= MAX_ROM) { "ROM exceeds 32 MiB" }; crc.update(buffer, 0, count)
            }
            require(size > 0) { "ROM is empty" }
            return Result(name, size, crc.value)
        }
        if (!fileName.endsWith(".zip", true)) return consume(fileName, input)
        val zip = ZipInputStream(input); var count = 0; var skipped = 0L; val buffer = ByteArray(8192)
        while (true) {
            check(!cancelled()) { "Cancelled" }
            val entry = zip.nextEntry ?: error("ZIP contains no supported ROM")
            require(++count <= 1024) { "ZIP contains too many entries" }
            val name = entry.name.substringAfterLast('/')
            if (!entry.isDirectory && !entry.name.startsWith("__MACOSX/") && !name.startsWith("._") && RomHeader.isRomFileName(name)) return consume(name, zip)
            while (true) {
                check(!cancelled()) { "Cancelled" }
                val size = zip.read(buffer); if (size < 0) break
                skipped += size; require(skipped <= MAX_SKIPPED) { "ZIP non-ROM data exceeds 1 MiB" }
            }
        }
    }
}

/** Removable Plus entry; ROM inspection runs off the library thread. */
object ArtworkEntry {
    @Suppress("UNUSED_PARAMETER")
    fun launch(activity: Activity, entry: RomEntry, library: RomLibrary) {
        val uri = entry.uri ?: return
        val cancelled = AtomicBoolean(false)
        val progress = AlertDialog.Builder(activity).setTitle("Artwork")
            .setMessage("Reading this ROM's identity…")
            .setNegativeButton("Cancel") { _, _ -> cancelled.set(true) }
            .setOnCancelListener { cancelled.set(true) }.show()
        val worker = Executors.newSingleThreadExecutor()
        worker.execute {
            val result = runCatching {
                activity.contentResolver.openInputStream(uri)?.use { ArtworkRomReader.read(entry.fileName, it, cancelled::get) }
                    ?: error("Could not read this ROM")
            }
            activity.runOnUiThread {
                runCatching { progress.dismiss() }
                if (!cancelled.get() && !activity.isDestroyed && !activity.isFinishing) {
                    result.onSuccess { rom ->
                        activity.startActivity(Intent(activity, ArtworkActivity::class.java)
                            .putExtra("artwork.platform", entry.platform.id).putExtra("artwork.crc", rom.crc32)
                            .putExtra("artwork.size", rom.size).putExtra("artwork.file", rom.fileName)
                            .putExtra("artwork.code", entry.gameCode)
                            .putExtra("artwork.title", GameTitle.display(entry.fileName, entry.title)))
                    }.onFailure { Toast.makeText(activity, it.message ?: "Could not read ROM identity", Toast.LENGTH_LONG).show() }
                }
            }
            worker.shutdown()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun showBatch(activity: Activity, entries: List<RomEntry>, library: RomLibrary) = ArtworkBatch.show(activity, entries)
}
