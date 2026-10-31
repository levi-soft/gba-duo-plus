package com.khumam.gbaduo.debug

import android.app.Activity
import android.content.Context
import com.khumam.gbaduo.emu.AudioOut
import com.khumam.gbaduo.emu.EmuThread
import com.khumam.gbaduo.emu.GameView
import com.khumam.gbaduo.emu.InputMapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioConnectionSettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun clearSavedChoice() {
        context.getSharedPreferences("studio_connection", Activity.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun defaultIsOffAndTheChoiceSurvivesNewContextWrappers() {
        assertFalse(StudioConnectionSettings.enabled(context))
        StudioConnectionSettings.setEnabled(context, true)
        assertTrue(StudioConnectionSettings.enabled(android.content.ContextWrapper(context)))
        StudioConnectionSettings.setEnabled(context, false)
        assertFalse(StudioConnectionSettings.enabled(android.content.ContextWrapper(context)))
    }

    @Test fun newGameSessionsUseTheSavedChoiceWithoutChangingAnExistingSession() {
        val surface = GameView(context)
        val audio = AudioOut()
        fun session(): EmuThread = EmuThread(byteArrayOf(), File(context.cacheDir, "fixture.sav").path,
            surface, audio, InputMapper()).also { StudioConnectionSettings.applyTo(context, it) }
        try {
            val initial = session()
            assertFalse(initial.studioConnectionEnabled)
            StudioConnectionSettings.setEnabled(context, true)
            val enabled = session()
            assertTrue(enabled.studioConnectionEnabled)
            assertFalse(initial.studioConnectionEnabled)
            StudioConnectionSettings.setEnabled(context, false)
            assertFalse(session().studioConnectionEnabled)
            assertTrue(enabled.studioConnectionEnabled)
            assertFalse(initial.isAlive)
            assertFalse(enabled.isAlive)
        } finally {
            ReflectionHelpers.callInstanceMethod<Any?>(surface, "onDetachedFromWindow")
            audio.release()
        }
    }
}
