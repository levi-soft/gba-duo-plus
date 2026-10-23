package com.khumam.gbaduo.achievements

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class AchievementPopupsTest {
    private fun texts(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun longUnlockStaysInsideNarrowScreenAndNeverTranslatesOverTheSurface() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity)
        activity.setContentView(host)
        fun layout() {
            host.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 720, 1280)
        }
        layout()
        val popup = AchievementPopups(host)
        popup.onEvent(RetroAchievements.EVENT_ACHIEVEMENT,
            "A very long achievement title with descenders: g y p j",
            "Complete a long objective without letting the title push the points outside the panel.", 100, null)
        layout()
        val card = host.getChildAt(0)
        assertEquals(0f, card.translationY, 0f)
        assertTrue(card.left >= 0 && card.right <= host.width)
        assertTrue(card.top >= 0 && card.bottom <= host.height)
        for (text in texts(card)) {
            assertTrue(text.width > 0)
            assertTrue(text.includeFontPadding)
            val lines = text.layout
            assertNotNull(lines)
            if (lines.lineCount > 0) assertTrue(lines.getLineBottom(lines.lineCount - 1) <= text.height - text.paddingBottom)
        }
        popup.detach()
    }
}
