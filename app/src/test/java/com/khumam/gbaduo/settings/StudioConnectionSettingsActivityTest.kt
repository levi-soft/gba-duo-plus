package com.khumam.gbaduo.settings

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.khumam.gbaduo.R
import com.khumam.gbaduo.debug.StudioConnectionSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioConnectionSettingsActivityTest {
    @Before fun clearSavedChoice() {
        RuntimeEnvironment.getApplication().getSharedPreferences("studio_connection", Activity.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private fun openChoice(activity: SettingsActivity) {
        val title = descendants(activity.window.decorView).filterIsInstance<TextView>()
            .single { it.text.toString() == activity.getString(R.string.studio_connection_title).uppercase() }
        generateSequence(title as View?) { it.parent as? View }.first { it.isClickable }.performClick()
    }

    private fun hasSummary(activity: SettingsActivity, string: Int): Boolean =
        descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(string) }

    @Test fun settingsEntrySavesOnAndOffAcrossActivityRecreation() {
        val first = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            assertTrue(hasSummary(first.get(), R.string.studio_connection_summary_off))
            openChoice(first.get())
            val picker = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(0, picker.listView.checkedItemPosition)
            shadowOf(picker).clickOnItem(1)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(StudioConnectionSettings.enabled(first.get()))
            assertTrue(hasSummary(first.get(), R.string.studio_connection_summary_on))
        } finally { first.pause().stop().destroy() }

        val reopened = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            assertTrue(hasSummary(reopened.get(), R.string.studio_connection_summary_on))
            openChoice(reopened.get())
            val picker = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(1, picker.listView.checkedItemPosition)
            shadowOf(picker).clickOnItem(0)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(StudioConnectionSettings.enabled(reopened.get()))
            assertTrue(hasSummary(reopened.get(), R.string.studio_connection_summary_off))
        } finally { reopened.pause().stop().destroy() }
    }
}
