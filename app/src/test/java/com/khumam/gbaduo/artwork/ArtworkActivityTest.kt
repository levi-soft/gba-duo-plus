package com.khumam.gbaduo.artwork

import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import android.widget.TextView
import com.khumam.gbaduo.library.Platform
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
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

    @Test fun credentialSourceLoadingAndSearchRunOnWorkerThenPublishOnUi() {
        val controller = Robolectric.buildActivity(ArtworkActivity::class.java, intent()).setup()
        val activity = controller.get()
        var sourceLoaded = false
        var searched = false
        try {
            activity.providerFactory = { id ->
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                assertEquals("steamgriddb", id)
                sourceLoaded = true
                object : ArtworkProvider {
                    override val id = "steamgriddb"
                    override fun search(query: String, platform: Platform): List<ArtworkGame> {
                        assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                        searched = true
                        return listOf(ArtworkGame(id, "1", "Worker result"))
                    }
                    override fun images(game: ArtworkGame, platform: Platform, region: String?) = emptyList<ArtworkCandidate>()
                }
            }
            val root = activity.window.decorView
            descendants(root).filterIsInstance<Spinner>().single().setSelection(1)
            descendants(root).filterIsInstance<TextView>().single { it.text.toString() == "Find artwork" }.performClick()
            drain(activity)
            assertTrue(sourceLoaded)
            assertTrue(searched)
            assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Worker result" })
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun missingSteamKeyStillShowsSetupNoticeOnUiAfterWorkerLookup() {
        val controller = Robolectric.buildActivity(ArtworkActivity::class.java, intent()).setup()
        val activity = controller.get()
        var sourceLoaded = false
        try {
            activity.providerFactory = {
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                sourceLoaded = true
                null
            }
            val root = activity.window.decorView
            descendants(root).filterIsInstance<Spinner>().single().setSelection(1)
            descendants(root).filterIsInstance<TextView>().single { it.text.toString() == "Find artwork" }.performClick()
            drain(activity)
            assertTrue(sourceLoaded)
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(dialog)
            assertTrue(dialog.isShowing)
            assertEquals("Set up artwork source", shadowOf(dialog).title.toString())
            dialog.dismiss()
        } finally { controller.pause().stop().destroy() }
    }
}
