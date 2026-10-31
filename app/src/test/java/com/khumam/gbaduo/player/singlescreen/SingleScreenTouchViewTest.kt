package com.khumam.gbaduo.player.singlescreen

import android.app.Activity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.khumam.gbaduo.emu.GbaKeys
import com.khumam.gbaduo.emu.AspectRatio
import com.khumam.gbaduo.player.singlescreen.SingleScreenLayout.Control
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import com.khumam.gbaduo.companion.CompanionController
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-mdpi")
class SingleScreenTouchViewTest {
    @Before fun resetDisplayPreference() {
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("single_screen_display", Activity.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun displayOptionsResizeTheSameSurfaceAcrossScreenModesAndRotation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val surface = View(activity)
        val host = SingleScreenPlayerHost(activity, surface, {}, {})
        activity.setContentView(host)
        host.setGameSize(240, 160)
        host.gameLoaded()
        layout(host, 1280, 360)
        val parent = surface.parent
        val swap = host.controlView(Control.SWAP)
        try {
            repeat(3) {
                host.setDisplayOptions(AspectRatio.ORIGINAL, integer = false, wide = true)
                layout(host, 1280, 360)
                assertSame(parent, surface.parent)
                host.setDisplayOptions(AspectRatio.FOUR_THREE, integer = true, wide = false)
                layout(host, 360, 800)
                assertSame(parent, surface.parent)
                assertSame(swap, host.controlView(Control.SWAP))
                swap.performClick()
                layout(host, 1280, 360)
            }
            assertEquals(SingleScreenLayout.ScreenMode.GAME_FIRST, host.screenMode)
        } finally {
            host.close()
            activity.finish()
        }
    }

    @Test fun swapRepositionsExistingViewsAndPersistsWithoutLeavingHeldInput() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var keys = 0
        val surface = View(activity)
        val host = SingleScreenPlayerHost(activity, surface, { keys = it }, {})
        activity.setContentView(host); layout(host, 800, 360); host.gameLoaded()
        host.showCompanion(CompanionController(activity, "Fixture", "TEST", 0))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        val originalParent = surface.parent
        val dpad = host.controlView(Control.DPAD)
        event(MotionEvent.ACTION_DOWN, Finger(1, dpad.width / 2f, dpad.height * .1f)).let { dpad.dispatchTouchEvent(it); it.recycle() }
        assertEquals(GbaKeys.UP, keys)
        host.controlView(Control.SWAP).performClick(); layout(host, 800, 360)
        assertEquals(0, keys); assertTrue(host.screensSwapped)
        assertTrue(host.arrangement.companion.right < host.arrangement.game.x)
        assertSame(originalParent, surface.parent)
        host.close()
        val replacement = SingleScreenPlayerHost(activity, View(activity), {}, {})
        assertTrue(replacement.screensSwapped)
        replacement.close()
        activity.getSharedPreferences("single_screen_display", Activity.MODE_PRIVATE).edit().clear().commit()
        activity.finish()
    }
    @Test fun thirdSwapModeHidesCompanionAndRestoresTheSameViewsAfterRotation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var keys = 0
        val surface = View(activity)
        val host = SingleScreenPlayerHost(activity, surface, { keys = it }, {})
        activity.setContentView(host); host.gameLoaded()
        host.showCompanion(CompanionController(activity, "Fixture", "TEST", 0))
        layout(host, 800, 360)
        val gameParent = surface.parent
        val companionFrame = host.getChildAt(1) as android.view.ViewGroup
        val companion = companionFrame.getChildAt(0)
        val swap = host.controlView(Control.SWAP)
        swap.performClick(); layout(host, 800, 360)
        assertEquals(SingleScreenLayout.ScreenMode.COMPANION_FIRST, host.screenMode)
        val companionWidth = companionFrame.measuredWidth
        val dpad = host.controlView(Control.DPAD)
        event(MotionEvent.ACTION_DOWN, Finger(1, dpad.width / 2f, dpad.height * .1f)).let { dpad.dispatchTouchEvent(it); it.recycle() }
        assertEquals(GbaKeys.UP, keys)
        swap.performClick(); layout(host, 800, 360)
        assertEquals(0, keys)
        assertEquals(SingleScreenLayout.ScreenMode.GAME_ONLY, host.screenMode)
        assertFalse(host.companionVisible)
        assertTrue(host.arrangement.gameOnly)
        assertEquals(companionWidth, companionFrame.measuredWidth)
        layout(host, 360, 800)
        assertTrue(host.arrangement.gameOnly)
        assertSame(gameParent, surface.parent)
        assertSame(companion, companionFrame.getChildAt(0))
        val restoredActivity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val restored = SingleScreenPlayerHost(restoredActivity, View(restoredActivity), {}, {})
        restoredActivity.setContentView(restored)
        assertEquals(SingleScreenLayout.ScreenMode.GAME_ONLY, restored.screenMode)
        assertFalse(restored.companionVisible)
        restored.gameLoaded(); restored.showCompanion(CompanionController(restoredActivity, "No pack fixture", "TEST", 0))
        layout(restored, 360, 800)
        assertFalse(restored.companionVisible)
        restored.close(); restoredActivity.finish()
        swap.performClick(); layout(host, 360, 800)
        assertEquals(SingleScreenLayout.ScreenMode.GAME_FIRST, host.screenMode)
        assertTrue(host.companionVisible)
        assertFalse(host.arrangement.gameOnly)
        assertSame(gameParent, surface.parent)
        assertSame(companion, companionFrame.getChildAt(0))
        host.close(); activity.finish()
    }

    @Test fun oldSwapPreferenceMigratesAndUnknownModesDoNotCrashStartup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs = activity.getSharedPreferences("single_screen_display", Activity.MODE_PRIVATE)
        prefs.edit().putBoolean("swapped", true).commit()
        val legacy = SingleScreenPlayerHost(activity, View(activity), {}, {})
        assertEquals(SingleScreenLayout.ScreenMode.COMPANION_FIRST, legacy.screenMode)
        legacy.close()
        prefs.edit().putString("screen_mode", "unknown_future_mode").commit()
        val unknown = SingleScreenPlayerHost(activity, View(activity), {}, {})
        assertEquals(SingleScreenLayout.ScreenMode.COMPANION_FIRST, unknown.screenMode)
        unknown.close(); activity.finish()
    }
    @Test fun loadingDisablesGameInputButLeavesMenuAvailableToCancel() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var saves = 0; var menus = 0; var keys = 0
        val host = SingleScreenPlayerHost(activity, View(activity), { keys = it }, { menus++ }, { saves++ })
        activity.setContentView(host)
        layout(host, 800, 360)
        for (control in Control.entries) {
            val view = host.controlView(control)
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                event(action, Finger(1, view.width / 2f, view.height / 2f)).let { view.dispatchTouchEvent(it); it.recycle() }
            }
        }
        assertEquals(0, saves); assertEquals(1, menus); assertEquals(0, keys)
        activity.getSharedPreferences("single_screen_theme", Activity.MODE_PRIVATE).edit().clear().commit()
        host.close(); activity.finish()
    }
    @Test fun menuQuickActionsAndFastForwardDoNotSendGbaButtons() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var menus = 0; var saves = 0; var loads = 0; var keys = 0; var fast = false
        val host = SingleScreenPlayerHost(activity, View(activity), { keys = it }, { menus++ }, { saves++ }, { loads++ }, { fast = it })
        activity.setContentView(host)
        layout(host, 800, 360)
        host.gameLoaded()
        host.controlView(Control.MENU).performClick()
        host.controlView(Control.QUICK_SAVE).performClick()
        host.controlView(Control.QUICK_LOAD).performClick()
        host.controlView(Control.FAST_FORWARD).performClick()
        assertEquals(1, menus); assertEquals(1, saves); assertEquals(1, loads); assertEquals(0, keys)
        assertTrue(fast)
        host.releaseControls()
        assertFalse(fast)
        host.close(); activity.finish()
    }

    @Test fun themeSwitchPersistsWithoutChangingScreenBounds() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs = activity.getSharedPreferences("single_screen_theme", Activity.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val host = SingleScreenPlayerHost(activity, View(activity), {}, {})
        activity.setContentView(host); layout(host, 800, 360)
        val bounds = host.arrangement
        host.controlView(Control.THEME).performClick()
        assertTrue(host.darkTheme); assertEquals(bounds, host.arrangement)
        host.close()
        val replacement = SingleScreenPlayerHost(activity, View(activity), {}, {})
        assertTrue(replacement.darkTheme)
        replacement.controlView(Control.THEME).performClick(); assertFalse(replacement.darkTheme)
        replacement.close(); prefs.edit().clear().commit(); activity.finish()
    }

    @Test fun hiddenSystemBarsDoNotLeavePaddingInTheFullscreenHost() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = SingleScreenPlayerHost(activity, View(activity), {}, {})
        host.onApplyWindowInsets(android.view.WindowInsets.Builder().setInsets(
            android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.mandatorySystemGestures(),
            android.graphics.Insets.of(0, 30, 0, 24)).build())
        assertEquals(0, host.paddingTop); assertEquals(0, host.paddingBottom)
        assertEquals(0, host.paddingLeft); assertEquals(0, host.paddingRight)
        host.close(); activity.finish()
    }
    @Test fun plainActivityCanAttachEmbeddedCompanionWithoutAFrameworkLifecycleOwner() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = SingleScreenPlayerHost(activity, View(activity), {}, {})
        activity.setContentView(host)
        layout(host, 800, 360)
        host.showCompanion(CompanionController(activity, "Fixture", "TEST", 0))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        host.close()
        activity.finish()
    }
    private data class Finger(val id: Int, val x: Float, val y: Float)

    private fun event(action: Int, vararg fingers: Finger): MotionEvent = MotionEvent.obtain(
        0L, 10L, action, fingers.size,
        fingers.map { finger -> MotionEvent.PointerProperties().apply { id = finger.id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray(),
        fingers.map { finger -> MotionEvent.PointerCoords().apply { x = finger.x; y = finger.y; pressure = 1f; size = 1f } }.toTypedArray(),
        0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)

    private fun layout(host: SingleScreenPlayerHost, w: Int, h: Int) {
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, w, h)
    }

    @Test fun fingersAcrossDpadAndABRemainIndependentAndRotationClearsAll() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var keys = 0
        val host = SingleScreenPlayerHost(activity, View(activity), { keys = it }, {})
        activity.setContentView(host)
        layout(host, 800, 360)
        host.gameLoaded()
        val dpad = host.controlView(Control.DPAD)
        val face = host.controlView(Control.FACE)
        val up = Finger(19, dpad.x + dpad.width * .5f, dpad.y + dpad.height * .1f)
        val a = Finger(42, face.x + face.width * .76f, face.y + face.height * .28f)
        fun send(action: Int, vararg fingers: Finger) { event(action, *fingers).let { host.dispatchTouchEvent(it); it.recycle() } }
        send(MotionEvent.ACTION_DOWN, up)
        assertEquals(GbaKeys.UP, keys)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), up, a)
        assertEquals(GbaKeys.UP or GbaKeys.A, keys)
        send(MotionEvent.ACTION_POINTER_UP, up, a)
        assertEquals(GbaKeys.A, keys)
        layout(host, 360, 800)
        assertEquals(0, keys)
        send(MotionEvent.ACTION_CANCEL, a)
        assertEquals(0, keys)
        host.close()
        activity.finish()
    }

    @Test fun faceSlidingAndCancellationDoNotLeaveAStuckButton() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var keys = 0
        val host = SingleScreenPlayerHost(activity, View(activity), { keys = it }, {})
        activity.setContentView(host)
        layout(host, 800, 360)
        host.gameLoaded()
        val face = host.controlView(Control.FACE)
        fun send(action: Int, x: Float, y: Float) {
            event(action, Finger(42, x * face.width, y * face.height)).let { face.dispatchTouchEvent(it); it.recycle() }
        }
        assertFalse(face.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, Finger(42, 0f, 0f))))
        send(MotionEvent.ACTION_DOWN, .76f, .28f)
        assertEquals(GbaKeys.A, keys)
        send(MotionEvent.ACTION_MOVE, .25f, .72f)
        assertEquals(GbaKeys.B, keys)
        send(MotionEvent.ACTION_MOVE, -1f, .72f)
        assertEquals(0, keys)
        send(MotionEvent.ACTION_MOVE, .76f, .28f)
        assertEquals(GbaKeys.A, keys)
        send(MotionEvent.ACTION_CANCEL, .76f, .28f)
        assertEquals(0, keys)
        host.close()
        activity.finish()
    }
}
