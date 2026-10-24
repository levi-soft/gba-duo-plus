package com.khumam.gbaduo.companion.builder

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class BuilderRomSourceTest {
    @Test fun `raw ROM is copied without changing bytes`() {
        val data = ByteArray(1024) { it.toByte() }; val out = ByteArrayOutputStream()
        BuilderRomSource.copy("game.gba", data.inputStream(), out)
        assertArrayEquals(data, out.toByteArray())
    }
    @Test fun `zip ROM follows existing first-ROM selection and ignores resource forks`() {
        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { z ->
            for ((name, bytes) in listOf("__MACOSX/._game.gba" to byteArrayOf(9), "readme.txt" to byteArrayOf(8), "folder/game.gba" to byteArrayOf(1, 2, 3))) {
                z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry()
            }
        }
        val out = ByteArrayOutputStream(); BuilderRomSource.copy("game.zip", zipped.toByteArray().inputStream(), out)
        assertArrayEquals(byteArrayOf(1,2,3), out.toByteArray())
    }
    @Test fun `decompressed ROM limits and cancellation stop streaming`() {
        assertThrows(Exception::class.java) { BuilderRomSource.copy("big.gba", ByteArrayInputStream(ByteArray(20)), ByteArrayOutputStream(), maxRomBytes = 10) }
        assertThrows(Exception::class.java) { BuilderRomSource.copy("game.gba", ByteArrayInputStream(ByteArray(20)), ByteArrayOutputStream(), cancelled = { true }) }
    }
    @Test fun `no ROM and non-GBA first ROM are rejected`() {
        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { z -> z.putNextEntry(ZipEntry("game.gb")); z.write(1); z.closeEntry() }
        assertThrows(Exception::class.java) { BuilderRomSource.copy("game.zip", zipped.toByteArray().inputStream(), ByteArrayOutputStream()) }
    }
}
