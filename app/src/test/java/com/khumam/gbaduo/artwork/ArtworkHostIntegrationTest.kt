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

    @Test fun optionalArtworkDelegateCanHandleDrawingAndReceivesSelection() {
        val cart = view().apply { selectedCart = true }
        var called = false
        cart.artworkRenderer = { canvas, area, selected ->
            called = true
            assertTrue(selected)
            assertEquals(240f, area.width(), 0f)
            assertEquals(300f, area.height(), 0f)
            canvas.drawColor(Color.MAGENTA)
            true
        }
        assertEquals(Color.MAGENTA, render(cart).getPixel(120, 150))
        assertTrue(called)
    }

    @Test fun DecliningArtworkDelegatePreservesOriginalCartridgeDrawing() {
        val original = render(view())
        val fallback = render(view().apply { artworkRenderer = { _, _, _ -> false } })
        assertTrue("No artwork must preserve the official cartridge rendering", original.sameAs(fallback))
    }

    @Test @Config(qualifiers = "w320dp-h480dp")
    fun threeActionsFitNarrowScreensAndKeepExistingClicks() {
        val context = RuntimeEnvironment.getApplication()
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        var played = 0
        val play = PixelButton(context).apply { text = "Play"; minWidth = 200; setOnClickListener { played++ } }
        actions.addView(play)
        actions.addView(PixelButton(context).apply { text = "Companion pack" })
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
