package com.khumam.gbaduo.player.singlescreen

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.widget.TextView
import com.khumam.gbaduo.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-port-mdpi")
class SingleScreenLibraryChromeTest {
    @Test @Config(qualifiers = "w997dp-h408dp-land-mdpi")
    fun wideHeaderSharesOneRowAndCoverKeepsUsefulHeightAfterRotation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(R.layout.activity_library)
        activity.findViewById<com.khumam.gbaduo.library.ui.OutlineText>(R.id.wordmark).text = "GBA DUO PLUS SS"
        activity.findViewById<TextView>(R.id.hud).text = "GAMES 013 · GBA 013"
        SingleScreenLibraryChrome.attach(activity)
        val root = activity.findViewById<android.view.ViewGroup>(R.id.root)
        fun size(w: Int, h: Int) { repeat(2) {
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, w, h)
        } }
        size(997, 408)
        val title = activity.findViewById<View>(R.id.wordmark)
        val hud = activity.findViewById<View>(R.id.hud)
        val settings = activity.findViewById<View>(R.id.open_settings)
        val fileName = activity.findViewById<View>(R.id.file_name)
        val metadata = fileName.parent
        assertSame(title.parent, hud.parent); assertSame(title.parent, settings.parent)
        val bounds = Rect(); settings.getDrawingRect(bounds); root.offsetDescendantRectToMyCoords(settings, bounds)
        assertTrue(bounds.right <= 997 && bounds.width() >= 40)
        assertTrue("Cover needs useful height", activity.findViewById<View>(R.id.carousel).height >= 160)
        size(360, 800)
        assertNotSame(title.parent, hud.parent)
        assertSame(activity.findViewById<View>(R.id.info), fileName.parent)
        assertTrue(fileName.width <= 360)
        size(997, 408)
        assertSame(title.parent, hud.parent)
        assertSame(metadata, fileName.parent)
        activity.finish()
    }
    @Test fun settingsKeepsATappableSlotEvenOnNarrowPortrait() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(R.layout.activity_library)
        activity.findViewById<TextView>(R.id.hud).text = "GAMES 013 · GBA 013"
        SingleScreenLibraryChrome.attach(activity)
        val root = activity.findViewById<View>(R.id.root)
        root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 320, 640)
        val settings = activity.findViewById<View>(R.id.open_settings)
        val bounds = Rect()
        settings.getDrawingRect(bounds)
        (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(settings, bounds)
        assertTrue("Settings must fit the visible screen: $bounds", bounds.left >= 0 && bounds.right <= 320)
        assertTrue(bounds.width() > 40 && bounds.height() > 20)
        assertEquals(296, activity.findViewById<View>(R.id.wordmark).width)
        var opened = false
        settings.setOnClickListener { opened = true }
        settings.performClick()
        assertTrue(opened)
        activity.finish()
    }
}
