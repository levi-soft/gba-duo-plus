package com.khumam.gbaduo.companion.builder

import com.khumam.gbaduo.library.RomArchive
import com.khumam.gbaduo.library.RomHeader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** Bounded streaming copy to a private input file. Never changes the source ROM. */
object BuilderRomSource {
    const val MAX_ROM_BYTES = 32L * 1024 * 1024
    fun copy(name: String, input: InputStream, output: OutputStream, maxRomBytes: Long = MAX_ROM_BYTES, cancelled: () -> Boolean = { false }) {
        val bounded = object : FilterInputStream(input) {
            var count = 0L
            override fun read(): Int { if (cancelled()) throw IOException("Cancelled"); val n = super.read(); if (n >= 0 && ++count > 64L * 1024 * 1024) throw IOException("Archive exceeds 64 MiB"); return n }
            override fun read(b: ByteArray, off: Int, len: Int): Int { if (cancelled()) throw IOException("Cancelled"); val n = `in`.read(b, off, len); if (n > 0) { count += n; if (count > 64L * 1024 * 1024) throw IOException("Archive exceeds 64 MiB") }; return n }
        }
        if (!RomArchive.isArchiveName(name)) {
            require(name.endsWith(".gba", ignoreCase = true)) { "Automatic companions currently require a GBA ROM" }
            copyRom(bounded, output, maxRomBytes, cancelled); return
        }
        val zip = ZipInputStream(bounded)
        var skippedTotal = 0L
        repeat(1024) {
            val entry = zip.nextEntry ?: throw IOException("ZIP does not contain a GBA ROM")
            val leaf = entry.name.substringAfterLast('/')
            if (!entry.isDirectory && !leaf.startsWith("._") && !entry.name.startsWith("__MACOSX/") && RomHeader.isRomFileName(leaf)) {
                require(leaf.endsWith(".gba", ignoreCase = true)) { "The first ROM in this ZIP is not a GBA game" }
                copyRom(zip, output, maxRomBytes, cancelled); return
            }
            // Skipped entries are bounded too: do not let a decompression bomb
            // hide before the selected ROM and consume unlimited CPU/time.
            val skipped = ByteArray(8192)
            while (true) { if (cancelled()) throw IOException("Cancelled"); val n = zip.read(skipped); if (n < 0) break; skippedTotal += n; if (skippedTotal > 1024 * 1024) throw IOException("ZIP contains oversized non-ROM entries") }
        }
        throw IOException("ZIP contains too many entries")
    }
    private fun copyRom(input: InputStream, output: OutputStream, limit: Long, cancelled: () -> Boolean) {
        val bytes = ByteArray(8192); var total = 0L
        while (true) { if (cancelled()) throw IOException("Cancelled"); val n = input.read(bytes); if (n < 0) break; total += n; if (total > limit) throw IOException("ROM exceeds 32 MiB"); output.write(bytes, 0, n) }
        require(total > 0) { "ROM is empty" }
    }
}
