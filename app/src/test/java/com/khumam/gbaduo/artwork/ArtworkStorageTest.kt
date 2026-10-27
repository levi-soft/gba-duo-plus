package com.khumam.gbaduo.artwork

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Looper
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.library.RomEntry
import com.khumam.gbaduo.library.RomLibrary
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ArtworkStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun isolatedContext(): Context {
        val files = temporary.newFolder()
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = files
            override fun getApplicationContext(): Context = this
        }
    }
    private fun png(width: Int = 64, height: Int = 32): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return ByteArrayOutputStream().use { out ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun identity(crc: Long = 0x32cd990d) = ArtworkIdentity("gba", crc)

    @Test fun legacySelectionIsCopiedToSharedCacheAndRemovalDoesNotResurrectIt() {
        val context = isolatedContext()
        val legacy = File(context.filesDir, "plus-artwork")
        val image = File(legacy, "images/12345678-1234-1234-1234-123456789abc.webp")
        image.parentFile!!.mkdirs(); image.writeBytes(png())
        val metadata = File(legacy, "records/${identity().key}.json")
        metadata.parentFile!!.mkdirs()
        metadata.writeText(org.json.JSONObject().put("identity", identity().key).put("image", image.name)
            .put("manual", true).put("width", 64).put("height", 32).put("updatedAt", 12).toString())
        val repository = ArtworkRepository(context)
        assertEquals(64, repository.loadBitmap(identity())!!.width)
        val shared = com.khumam.gbaduo.library.artwork.Artwork.store(context)
        assertNotNull(shared.file("gba", identity().crc32))
        assertTrue(image.isFile); assertTrue(metadata.isFile)
        shared.removeAll()
        assertFalse(ArtworkRepository(context).has(identity()))
        assertTrue(image.isFile)
    }

    @Test fun sharedFolderArtworkAppearsInPlusAndManualPlusSelectionAppearsInSharedStore() {
        val context = isolatedContext()
        val shared = com.khumam.gbaduo.library.artwork.Artwork.store(context)
        shared.put("gba", identity().crc32, png(), "folder", "cover.png")
        val repository = ArtworkRepository(context)
        assertEquals(64, repository.loadBitmap(identity())!!.width)
        repository.apply(identity(), png(80, 40), manual = true)
        assertEquals("manual", shared.meta("gba", identity().crc32)!!.source)
        assertEquals(80, ArtworkImageStore.decode(shared.file("gba", identity().crc32)!!.readBytes()).width)
    }

    @Test fun identityUsesActualRomPlatformAndCrcRatherThanTitle() {
        assertEquals("gba-32CD990D", identity().key)
        assertNotEquals(identity().key, identity(0x32cd990e).key)
        assertNotEquals(identity().key, ArtworkIdentity("gb", 0x32cd990d).key)
        assertThrows(IllegalArgumentException::class.java) { ArtworkIdentity("../gba", 1) }
        assertThrows(IllegalArgumentException::class.java) { ArtworkIdentity("gba", -1) }
    }

    @Test fun failedReplacementPreservesSelectedCoverAndOfflineMetadata() {
        val context = isolatedContext()
        val repository = ArtworkRepository(context)
        repository.remove(identity())
        val selected = repository.apply(identity(), png(), ArtworkCandidate(
            "steamgriddb", "12", "https://cdn.steamgriddb.com/grid/12.png",
            author = "Artist", sourcePage = "https://www.steamgriddb.com/grid/12?token=private"
        ), manual = true)
        assertTrue(repository.has(identity()))
        assertEquals(64, selected.width)
        assertEquals(32, selected.height)
        assertEquals("Artist", repository.record(identity())?.author)
        assertEquals("https://www.steamgriddb.com/grid/12", repository.record(identity())?.sourcePage)
        assertTrue(repository.record(identity())!!.manual)
        assertNull(ArtworkRepository(context).applyIfMissing(identity(), "invalid download".toByteArray()))
        assertEquals(selected.imageFile, repository.record(identity())?.imageFile)
        assertThrows(IllegalArgumentException::class.java) {
            repository.apply(identity(), "not an image".toByteArray())
        }
        assertEquals(selected.imageFile, repository.record(identity())?.imageFile)
        assertEquals(64, repository.loadBitmap(identity())?.width)
        assertFalse(repository.has(identity(0x32cd990e)))
        repository.remove(identity())
        assertNull(repository.record(identity()))
        assertNull(repository.loadBitmap(identity()))
    }

    @Test fun largeImagesAreDownsampledAndInvalidInputsFailBeforeAssignment() {
        val decoded = ArtworkImageStore.decode(png(2048, 1024))
        assertEquals(1024, decoded.width)
        assertEquals(512, decoded.height)
        decoded.recycle()
        assertThrows(IllegalArgumentException::class.java) {
            ArtworkImageStore.decode(ByteArray(16 * 1024 * 1024 + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArtworkImageStore.decode("<svg width='100'/>".toByteArray())
        }
    }

    @Test fun cancellationBeforeFinalClaimPreservesTheCoverAndCleansStagedFiles() {
        val context = isolatedContext()
        val repository = ArtworkRepository(context)
        val original = repository.apply(identity(), png(), manual = true)
        val gate = ArtworkRequestGate()
        val token = gate.begin()
        val root = File(context.filesDir, "artwork")
        var staged = false
        val result = repository.applyIfOwned(identity(), png(80, 40)) {
            // Both expensive files exist before the final ownership decision.
            staged = File(root, "gba").listFiles()!!.size == 2 &&
                root.listFiles()!!.any { it.extension == "tmp" }
            gate.close()
            gate.claim(token)
        }
        assertTrue(staged)
        assertNull(result)
        assertEquals(original, repository.record(identity()))
        assertEquals(1, File(root, "gba").listFiles()!!.size)
        assertEquals(1, root.listFiles()!!.count { it.isFile })
        assertEquals(64, repository.loadBitmap(identity())!!.width)
    }

    @Test fun claimWonBeforeCancellationFinishesOneCoherentReplacement() {
        val context = isolatedContext()
        val repository = ArtworkRepository(context)
        repository.apply(identity(), png(), manual = true)
        val gate = ArtworkRequestGate()
        val token = gate.begin()
        val saved = repository.applyIfOwned(identity(), png(80, 40)) {
            val claimed = gate.claim(token)
            gate.close()
            claimed
        }
        assertNotNull(saved)
        assertEquals(saved, repository.record(identity()))
        assertEquals(80, repository.loadBitmap(identity())!!.width)
        assertEquals(1, File(context.filesDir, "artwork/gba").listFiles()!!.size)
        assertEquals(1, File(context.filesDir, "artwork").listFiles()!!.count { it.isFile })
    }

    @Test fun failedAtomicPointerMoveKeepsThePreviousCoverAndRemovesTheNewImage() {
        val context = isolatedContext()
        val repository = ArtworkRepository(context)
        val original = repository.apply(identity(), png(), manual = true)
        val root = File(context.filesDir, "artwork")
        assertThrows(java.io.IOException::class.java) {
            repository.applyIfOwned(identity(), png(80, 40)) {
                // A deterministic filesystem fault at the publication boundary.
                val stagedMetadata = root.listFiles()!!.single { it.extension == "tmp" }
                assertTrue(stagedMetadata.delete())
                true
            }
        }
        assertEquals(original, repository.record(identity()))
        assertEquals(64, repository.loadBitmap(identity())!!.width)
        assertEquals(1, File(root, "gba").listFiles()!!.size)
        assertEquals(1, root.listFiles()!!.count { it.isFile })
    }

    @Test fun attributionStripsQueriesFragmentsAndNeverStoresDownloadSecrets() {
        val repository = ArtworkRepository(isolatedContext())
        val candidate = ArtworkCandidate("libretro", "42-box", "https://thumbnails.libretro.com/image.png?token=secret",
            sourcePage = "https://github.com/libretro-thumbnails/example?token=secret#details")
        val selected = repository.apply(identity(), png(), candidate)
        assertEquals("https://github.com/libretro-thumbnails/example", selected.sourcePage)
        assertEquals(selected.sourcePage, repository.record(identity())?.sourcePage)
        assertFalse(candidate.toString().contains("secret"))
        assertFalse(candidate.toString().contains("https://"))
        val ambiguous = candidate.copy(sourcePage = "https://github.com/libretro-thumbnails/example?token=secret&token=other")
        assertEquals("https://github.com/libretro-thumbnails/example", repository.apply(identity(), png(), ambiguous).sourcePage)
    }

}
