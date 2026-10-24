package com.khumam.gbaduo.artwork

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ArtworkUiStateTest {
    @Test fun beginAndCloseDoNotWaitForAnAlreadyClaimedCommit() {
        val gate = ArtworkRequestGate()
        val token = gate.begin()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val storage = Executors.newSingleThreadExecutor()
        val ui = Executors.newSingleThreadExecutor()
        try {
            val committed = storage.submit<Int?> {
                gate.commit(token) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    42
                }
            }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            // Simulates the main thread cancelling/navigating while a slow disk
            // operation is already committed on a worker. Neither may wait for it.
            val next = ui.submit<Long> { gate.begin() }.get(2, TimeUnit.SECONDS)
            assertFalse(gate.isCurrent(token))
            assertTrue(gate.isCurrent(next))
            ui.submit { gate.close() }.get(2, TimeUnit.SECONDS)
            assertFalse(gate.isCurrent(next))
            release.countDown()
            assertEquals(42, committed.get(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            storage.shutdownNow()
            ui.shutdownNow()
        }
    }

    @Test fun previousRequestCannotCommitAfterAnotherScreenOrClose() {
        val gate = ArtworkRequestGate()
        val first = gate.begin()
        assertTrue(gate.isCurrent(first))
        val second = gate.begin()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
        var applied = 0
        gate.commit(first) { applied++ }
        assertEquals(0, applied)
        gate.commit(second) { applied++ }
        assertEquals(1, applied)
        gate.close()
        assertFalse(gate.isCurrent(second))
        gate.commit(second) { applied++ }
        assertEquals(1, applied)
    }

    @Test fun archiveIdentityUsesContainedRomSizeAndCrcNotArchiveBytes() {
        val rom = ByteArray(700) { it.toByte() }
        val archive = ByteArrayOutputStream().also { target ->
            ZipOutputStream(target).use { zip ->
                zip.putNextEntry(ZipEntry("readme.txt")); zip.write("notes".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("folder/Game (Vietnamese).gba")); zip.write(rom); zip.closeEntry()
            }
        }.toByteArray()
        val result = ArtworkRomReader.read("game.zip", ByteArrayInputStream(archive))
        assertEquals(rom.size.toLong(), result.size)
        assertEquals(CRC32().apply { update(rom) }.value, result.crc32)
        assertEquals("Game (Vietnamese).gba", result.fileName)
    }

    @Test fun oversizedRomAndArchiveDistractionsAreBounded() {
        assertThrows(IllegalArgumentException::class.java) {
            ArtworkRomReader.read("large.gba", ByteArrayInputStream(ByteArray(33 * 1024 * 1024)))
        }
        val archive = ByteArrayOutputStream().also { target ->
            ZipOutputStream(target).use { zip ->
                zip.putNextEntry(ZipEntry("large.txt")); zip.write(ByteArray(1024 * 1024 + 1)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("game.gba")); zip.write(byteArrayOf(1)); zip.closeEntry()
            }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { ArtworkRomReader.read("game.zip", ByteArrayInputStream(archive)) }
    }
}
