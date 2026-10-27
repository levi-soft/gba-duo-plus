package com.khumam.gbaduo.artwork

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import com.khumam.gbaduo.library.ui.PixelButton
import com.khumam.gbaduo.library.ui.CartridgeView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkHostIntegrationTest {
    private fun view() = CartridgeView(RuntimeEnvironment.getApplication()).apply {
        title = "Test game"
        layout(0, 0, 240, 300)
    }
    private fun render(view: CartridgeView): Bitmap = Bitmap.createBitmap(240, 300, Bitmap.Config.ARGB_8888).also {
        view.draw(Canvas(it))
    }

    @Test fun officialCartridgeKeepsPortraitArtInsideItsLabelAndRetainsShell() {
        val portrait = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val cart = view().apply { art = portrait }
        val rendered = render(cart)
        val pixels = IntArray(rendered.width * rendered.height)
        rendered.getPixels(pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
        val colored = pixels.indices.filter { pixels[it] == Color.MAGENTA }
        assertTrue(colored.isNotEmpty())
        val left = colored.minOf { it % rendered.width }
        val right = colored.maxOf { it % rendered.width }
        val top = colored.minOf { it / rendered.width }
        val bottom = colored.maxOf { it / rendered.width }
        assertTrue(left > 0 && right < rendered.width - 1 && top > 0 && bottom < rendered.height - 1)
        assertEquals(0.5f, (right - left + 1f) / (bottom - top + 1f), 0.03f)
        val plain = render(view())
        assertFalse(plain.sameAs(rendered))
        assertEquals(plain.getPixel(20, 150), rendered.getPixel(20, 150))
        plain.recycle(); rendered.recycle(); portrait.recycle()
    }

    @Test @Config(qualifiers = "w320dp-h480dp")
    fun threeActionsFitNarrowScreensAndKeepExistingClicks() {
        val context = RuntimeEnvironment.getApplication()
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        var played = 0
        val play = PixelButton(context).apply { text = "Play"; minWidth = 200; setOnClickListener { played++ } }
        actions.addView(play)
        actions.addView(PixelButton(context).apply { text = "Companion pack" })
        actions.addView(PixelButton(context).apply { id = com.khumam.gbaduo.R.id.game_settings_button })
        var opened = 0
        ArtworkLibraryControls.attach(actions) { opened++ }
        val width = (320 * context.resources.displayMetrics.density).toInt()
        actions.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST))
        actions.layout(0, 0, width, actions.measuredHeight)
        assertEquals(3, actions.childCount)
        for (index in 0 until actions.childCount) {
            val button = actions.getChildAt(index)
            assertTrue(button.width > 0)
            assertTrue("Action extends outside narrow screen", button.right <= width)
        }
        play.performClick()
        actions.getChildAt(2).performClick()
        assertEquals(1, played)
        assertEquals(1, opened)
    }
}
