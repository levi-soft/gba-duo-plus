package com.khumam.gbaduo.player.singlescreen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.khumam.gbaduo.BuildConfig
import com.khumam.gbaduo.R
import com.khumam.gbaduo.artwork.ArtworkRenderer
import com.khumam.gbaduo.library.LibraryActivity
import com.khumam.gbaduo.library.ui.CartridgeView
import com.khumam.gbaduo.library.ui.GraphPaperDrawable
import com.khumam.gbaduo.library.ui.OutlineText
import com.khumam.gbaduo.library.ui.Pixel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual library styles with a local generated cover; no folder, ROM or network fixture. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SingleScreenLibraryRenderTest {
    @Test @Config(qualifiers = "w997dp-h408dp-land-mdpi")
    fun landscape() = render("library-landscape", 997, 408)

    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    fun portrait() = render("library-portrait", 360, 800)

    private fun render(name: String, width: Int, height: Int) {
        // Do not resume: library scanning and blinking are outside this deterministic capture.
        val controller = Robolectric.buildActivity(LibraryActivity::class.java).create().start().visible()
        val activity = controller.get()
        val root = activity.findViewById<ViewGroup>(R.id.root)
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        try {
            activity.findViewById<OutlineText>(R.id.wordmark).text = "GBA DUO PLUS SS"
            if (!BuildConfig.SINGLE_SCREEN) SingleScreenLibraryChrome.attach(activity)
            activity.findViewById<TextView>(R.id.hud).text = "GAMES 001   GBA 001"
            activity.findViewById<OutlineText>(R.id.game_title).text = "FIRE EMBLEM"
            activity.findViewById<TextView>(R.id.tag_platform).text = "GBA"
            activity.findViewById<TextView>(R.id.tag_code).text = "AE7E"
            activity.findViewById<TextView>(R.id.tag_pack).text = "PACK"
            activity.findViewById<TextView>(R.id.file_name).text = "Fire Emblem (Vietnamese).zip"
            activity.findViewById<View>(R.id.empty).visibility = View.GONE
            activity.findViewById<View>(R.id.info).visibility = View.VISIBLE
            activity.findViewById<View>(R.id.actions).visibility = View.VISIBLE
            Canvas(bitmap).let { canvas ->
                canvas.drawColor(Pixel.PURPLE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Pixel.YELLOW; textSize = 25f; textAlign = Paint.Align.CENTER }
                canvas.drawText("COVER", 80f, 85f, paint)
            }
            val shelf = activity.findViewById<RecyclerView>(R.id.carousel)
            shelf.visibility = View.VISIBLE
            shelf.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 1
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val cart = CartridgeView(activity).apply {
                        layoutParams = RecyclerView.LayoutParams((236 * resources.displayMetrics.density).toInt(), -1)
                        artworkRenderer = { canvas, bounds, _ -> ArtworkRenderer.draw(canvas, bounds, bitmap, true, resources.displayMetrics.density) }
                    }
                    return object : RecyclerView.ViewHolder(cart) {}
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            }
            repeat(3) {
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                shadowOf(android.os.Looper.getMainLooper()).idle()
            }
            val cart = shelf.getChildAt(0)
            assertNotNull(cart)
            val cover = ArtworkRenderer.squareBounds(RectF(0f, 0f, cart.width.toFloat(), cart.height.toFloat()), 14f)
            if (width >= height) assertTrue("Cover too small: $cover", cover.height() >= 140f)
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            GraphPaperDrawable(activity).apply { setBounds(0, 0, width, height); draw(canvas) }
            root.draw(canvas)
            val output = File("build/screenshots/single-screen/$name-${System.currentTimeMillis()}.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            controller.stop().destroy()
            bitmap.recycle()
        }
    }
}
