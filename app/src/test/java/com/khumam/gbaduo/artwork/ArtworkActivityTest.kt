package com.khumam.gbaduo.artwork

import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.khumam.gbaduo.R
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.library.artwork.Artwork
import com.khumam.gbaduo.library.artwork.Http
import com.khumam.gbaduo.library.artwork.HttpResponse
import com.khumam.gbaduo.library.artwork.SteamGridDb
import com.khumam.gbaduo.settings.ArtworkPickerActivity
import com.khumam.gbaduo.settings.GameSettingsActivity
import com.khumam.gbaduo.settings.Providers
import com.khumam.gbaduo.settings.SteamGridDbActivity
import com.khumam.gbaduo.storage.GameStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkActivityTest {
    private fun intent() = Intent(RuntimeEnvironment.getApplication(), ArtworkActivity::class.java)
        .putExtra("artwork.crc", 7L).putExtra("artwork.size", 1024L)
        .putExtra("artwork.platform", "gba").putExtra("artwork.title", "Test game")

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
    private fun drain(activity: ArtworkActivity) {
        val field = ArtworkActivity::class.java.getDeclaredField("io").apply { isAccessible = true }
        (field.get(activity) as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() } }
        finally { bitmap.recycle() }
    }
    private fun settingsIntent(): Intent {
        val context = RuntimeEnvironment.getApplication()
        val storage = GameStorage.forGame(context.filesDir, Platform.GBA, "Test game", "TEST", 7L).ensureDirs()
        return Intent(context, GameSettingsActivity::class.java)
            .putExtra(GameSettingsActivity.EXTRA_GAME_DIR, storage.dir.absolutePath)
            .putExtra(GameSettingsActivity.EXTRA_TITLE, "Test game")
            .putExtra(GameSettingsActivity.EXTRA_PLATFORM_ID, "gba")
            .putExtra(GameSettingsActivity.EXTRA_CRC, 7L)
    }
    private fun launch(label: String): Intent {
        val settings = Robolectric.buildActivity(GameSettingsActivity::class.java, settingsIntent()).setup()
        return try {
            val title = descendants(settings.get().window.decorView).filterIsInstance<TextView>()
                .single { it.text.toString().equals(label, ignoreCase = true) }
            assertTrue(((title.parent as View).parent as View).performClick())
            shadowOf(settings.get()).nextStartedActivity
        } finally { settings.pause().stop().destroy() }
    }

    @Test fun gameSettingsLibretroEntrySearchesPreviewsAndAppliesWithoutRomSize() {
        val launched = launch("Libretro / local image")
        assertEquals(ArtworkActivity::class.java.name, launched.component!!.className)
        assertFalse(launched.hasExtra("artwork.size"))
        val picker = Robolectric.buildActivity(ArtworkActivity::class.java, launched).setup()
        try {
            assertFalse("Artwork must stay open from Game settings", picker.get().isFinishing)
            picker.get().imageDownloader = {
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                png()
            }
            picker.get().providerFactory = {
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                object : ArtworkProvider {
                    override val id = "libretro"
                    override fun search(query: String, platform: Platform) = listOf(ArtworkGame(id, "1", "Found through Libretro"))
                    override fun images(game: ArtworkGame, platform: Platform, region: String?) =
                        listOf(ArtworkCandidate(id, "1", "https://thumbnails.libretro.com/test.png"))
                }
            }
            val root = picker.get().window.decorView
            fun click(text: String) = descendants(root).filterIsInstance<TextView>().single { it.text.toString() == text }.performClick()
            click("Find artwork"); drain(picker.get())
            click("Found through Libretro"); drain(picker.get())
            // Injected generic provider uses the gallery; real single Libretro art opens directly.
            descendants(root).single { it.contentDescription?.toString() == "Preview artwork by libretro" }.performClick()
            drain(picker.get())
            click("Apply artwork"); drain(picker.get())
            assertTrue(picker.get().isFinishing)
            val repo = ArtworkRepository(picker.get())
            assertEquals("libretro", repo.record(ArtworkIdentity("gba", 7))!!.provider)
            repo.loadBitmap(ArtworkIdentity("gba", 7))!!.also { assertEquals(128, it.width); it.recycle() }
        } finally { picker.pause().stop().destroy() }
    }

    @Test fun steamGridDbEntryUsesOfficialPickerAndSharedProviderSettings() {
        val context = RuntimeEnvironment.getApplication()
        val launched = launch(context.getString(R.string.artwork_find))
        assertEquals(ArtworkPickerActivity::class.java.name, launched.component!!.className)
        assertEquals("gba", launched.getStringExtra(GameSettingsActivity.EXTRA_PLATFORM_ID))
        assertEquals(7L, launched.getLongExtra(GameSettingsActivity.EXTRA_CRC, -1))
        assertNotNull(launched.getStringExtra(GameSettingsActivity.EXTRA_GAME_DIR))
        assertEquals(SteamGridDbActivity::class.java, Providers.all.single { it.titleRes == R.string.provider_sgdb }.settings)
        val picker = Robolectric.buildActivity(ArtworkPickerActivity::class.java, launched)
        val bytes = png()
        picker.get().apiFactory = {
            SteamGridDb("fixture", object : Http {
                override fun get(url: String, headers: Map<String, String>): HttpResponse {
                    assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                    if (url.startsWith("https://cdn2.steamgriddb.com/")) {
                        assertTrue(headers.isEmpty())
                        return HttpResponse(200, bytes)
                    }
                    assertEquals("Bearer fixture", headers["Authorization"])
                    val body = if ("/grids/" in url)
                        """{"success":true,"data":[{"id":2,"url":"https://cdn2.steamgriddb.com/grid/test.png","thumb":"https://cdn2.steamgriddb.com/thumb/test.png","width":32,"height":16}]}"""
                    else """{"success":true,"data":[{"id":1,"name":"Official result"}]}"""
                    return HttpResponse(200, body.toByteArray())
                }
            })
        }
        picker.setup()
        val root = picker.get().window.decorView
        fun awaitUi(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!condition() && System.nanoTime() < deadline) {
                Thread.sleep(10)
                shadowOf(Looper.getMainLooper()).idle()
            }
            assertTrue("Official artwork worker did not publish the expected result", condition())
        }
        try {
            awaitUi { descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "OFFICIAL RESULT" } }
            val title = descendants(root).filterIsInstance<TextView>().single { it.text.toString() == "OFFICIAL RESULT" }
            ((title.parent as View).parent as View).performClick()
            awaitUi { descendants(root).filterIsInstance<ImageView>().any { it.isClickable } }
            descendants(root).filterIsInstance<ImageView>().single { it.isClickable }.performClick()
            awaitUi { picker.get().isFinishing }
            assertEquals("steamgriddb", Artwork.store(context).meta("gba", 7L)!!.source)
            assertNotNull(Artwork.store(context).file("gba", 7L))
        } finally { picker.pause().stop().destroy() }
    }

    @Test fun missingSteamKeyRoutesToSingleOfficialKeyEditor() {
        val launched = launch(RuntimeEnvironment.getApplication().getString(R.string.artwork_find))
        val picker = Robolectric.buildActivity(ArtworkPickerActivity::class.java, launched)
        picker.get().apiFactory = { null }
        picker.setup()
        try {
            assertTrue(picker.get().isFinishing)
            assertEquals(SteamGridDbActivity::class.java.name, shadowOf(picker.get()).nextStartedActivity.component!!.className)
        } finally { picker.destroy() }
    }

    @Test fun invalidIdentityOrExplicitInvalidSizeStillClosesLibretroPicker() {
        for (bad in listOf(intent().putExtra("artwork.crc", -1L), intent().putExtra("artwork.platform", "invalid"),
            intent().putExtra("artwork.size", -1L), intent().putExtra("artwork.size", 32L * 1024 * 1024 + 1))) {
            val controller = Robolectric.buildActivity(ArtworkActivity::class.java, bad).setup()
            try { assertTrue(controller.get().isFinishing) } finally { controller.pause().stop().destroy() }
        }
    }
}
