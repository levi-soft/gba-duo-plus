package com.khumam.gbaduo.player.singlescreen

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.graphics.HardwareRendererCompat
import com.khumam.gbaduo.companion.CompanionController
import com.khumam.gbaduo.companion.JsonTree
import com.khumam.gbaduo.companion.PackManifest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/** Render the real embedded companion and controls; the video pattern is explicitly synthetic. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SingleScreenRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(qualifiers = "w800dp-h400dp-land-mdpi")
    fun landscape() = render("landscape")

    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    fun portrait() = render("portrait")

    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    fun portraitWithCameraCutout() = render("portrait-cutout", 32)

    @Test @Config(qualifiers = "w800dp-h600dp-land-mdpi")
    fun tabletLandscapeFourByThree() = render("tablet-landscape-4-3")

    @Test @Config(qualifiers = "w600dp-h800dp-port-mdpi")
    fun tabletPortraitThreeByFour() = render("tablet-portrait-3-4")

    @Test @Config(qualifiers = "w600dp-h600dp-land-mdpi")
    fun foldSquare() = render("fold-square")

    @Test @Config(qualifiers = "w800dp-h400dp-land-mdpi")
    fun landscapeDark() = render("landscape-dark", dark = true)

    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    fun portraitDark() = render("portrait-dark", dark = true)

    @Test @Config(qualifiers = "w997dp-h408dp-land-mdpi")
    fun widePhone() = render("phone-wide")

    @Test @Config(qualifiers = "w997dp-h408dp-land-mdpi")
    fun widePhoneDark() = render("phone-wide-dark", dark = true)

    @Test @Config(qualifiers = "w997dp-h408dp-land-mdpi")
    fun widePhoneSwapped() = render("phone-wide-swapped", swapped = true)

    @Test @Config(qualifiers = "w800dp-h600dp-land-mdpi")
    fun tabletLandscapeSwapped() = render("tablet-landscape-swapped", swapped = true)

    private fun render(name: String, cutoutTop: Int = 0, dark: Boolean = false, swapped: Boolean = false) {
        val fixture = System.getProperty("gbaduo.ss.previewPack")
        val directory = File(fixture ?: "../docs-packs/examples/classic")
        val manifest = PackManifest.parse(File(directory, "manifest.json").readText())
        lateinit var host: SingleScreenPlayerHost
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                val controller = CompanionController(context, "Example companion", "TEST", 0)
                check(controller.showPack(manifest, directory))
                controller.update(JsonTree.parseObject(File(directory, "sample-snapshot.json").readText()))
                val video = object : View(context) {
                    override fun onDraw(canvas: Canvas) {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                        canvas.drawColor(0xff192b35.toInt())
                        paint.color = 0xff314e54.toInt()
                        for (y in 0..7) for (x in 0..11) if ((x + y) % 2 == 0) {
                            canvas.drawRect(x * width / 12f, y * height / 8f, (x + 1) * width / 12f, (y + 1) * height / 8f, paint)
                        }
                        paint.color = android.graphics.Color.WHITE
                        paint.textSize = 16 * resources.displayMetrics.density
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("GBA · synthetic video", width / 2f, height / 2f, paint)
                    }
                }
                context.getSharedPreferences("single_screen_theme", Activity.MODE_PRIVATE).edit().clear().commit()
                context.getSharedPreferences("single_screen_display", Activity.MODE_PRIVATE).edit().clear().commit()
                host = SingleScreenPlayerHost(context, video, {}, {})
                if (dark) host.controlView(SingleScreenLayout.Control.THEME).performClick()
                if (swapped) host.controlView(SingleScreenLayout.Control.SWAP).performClick()
                host.showCompanion(controller)
                host.gameLoaded()
                host
            })
        }
        compose.waitForIdle()
        if (cutoutTop > 0) {
            compose.runOnUiThread {
                host.onApplyWindowInsets(android.view.WindowInsets.Builder().setDisplayCutout(
                    android.view.DisplayCutout(Rect(0, cutoutTop, 0, 0), listOf(Rect(150, 0, 210, cutoutTop)))).build())
            }
            compose.waitForIdle()
        }
        val node = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxBy { it.boundsInRoot.width * it.boundsInRoot.height }
        val view = (node.root as ViewRootForTest).view
        val bounds = node.boundsInRoot
        val rect = Rect(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt())
        val location = IntArray(2); view.getLocationInWindow(location); rect.offset(location[0], location[1])
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val image = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        val drawing = HardwareRendererCompat.isDrawingEnabled()
        try {
            HardwareRendererCompat.setDrawingEnabled(true)
            var result = -1
            compose.runOnUiThread { PixelCopy.request((context as Activity).window, rect, image, { result = it }, Handler(Looper.getMainLooper())) }
            compose.waitForIdle()
            assertEquals(PixelCopy.SUCCESS, result)
            assertEquals(4f / 3f, host.arrangement.companion.width / host.arrangement.companion.height, .001f)
            assertTrue(host.arrangement.game.y >= cutoutTop)
            // Windows image previews can retain a mapped file; never truncate an open capture.
            val file = File("build/screenshots/single-screen/$name-${System.currentTimeMillis()}.png"); file.parentFile?.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            HardwareRendererCompat.setDrawingEnabled(drawing)
            compose.runOnUiThread { host.close() }
        }
    }
}
