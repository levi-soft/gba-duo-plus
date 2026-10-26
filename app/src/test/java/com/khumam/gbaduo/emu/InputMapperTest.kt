package com.khumam.gbaduo.emu

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class InputMapperTest {
    @Test fun touchFastForwardAndPhysicalR2ReleaseIndependently() {
        val mapper = InputMapper()
        mapper.setTouchFastForward(true)
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_R2, true)
        mapper.setTouchFastForward(false)
        assertTrue(mapper.fastForward())
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_R2, false)
        assertFalse(mapper.fastForward())
        mapper.setTouchFastForward(true); mapper.releaseAll(); assertFalse(mapper.fastForward())
    }

    @Test fun touchAndGamepadReleaseIndependently() {
        val mapper = InputMapper()
        mapper.onKey(KeyEvent.KEYCODE_DPAD_LEFT, true)
        mapper.setTouchKeys(GbaKeys.LEFT or GbaKeys.A)
        mapper.setTouchKeys(0)
        assertEquals(GbaKeys.LEFT, mapper.keys())
        mapper.setTouchKeys(GbaKeys.A)
        mapper.onKey(KeyEvent.KEYCODE_DPAD_LEFT, false)
        assertEquals(GbaKeys.A, mapper.keys())
        mapper.releaseAll()
        assertEquals(0, mapper.keys())
    }

    @Test fun labelledTouchButtonsIgnorePhysicalSwapAndRejectExtraBits() {
        val mapper = InputMapper().apply { swapAb = true }
        mapper.setTouchKeys(GbaKeys.A or (1 shl 15))
        assertEquals(GbaKeys.A, mapper.keys())
    }

    @Test
    fun mapsFaceButtonsToAAndB() {
        val mapper = InputMapper()
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_B, down = true)
        assertEquals(GbaKeys.A, mapper.keys())

        mapper.onKey(KeyEvent.KEYCODE_BUTTON_B, down = false)
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_A, down = true)
        assertEquals(GbaKeys.B, mapper.keys())
    }

    @Test
    fun swapAbInvertsFaceButtons() {
        val mapper = InputMapper()
        mapper.swapAb = true
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_B, down = true)
        assertEquals(GbaKeys.B, mapper.keys())
    }

    @Test
    fun mapsShouldersStartSelectAndDpad() {
        val mapper = InputMapper()
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_L1, down = true)
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_R1, down = true)
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_START, down = true)
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_SELECT, down = true)
        mapper.onKey(KeyEvent.KEYCODE_DPAD_UP, down = true)
        assertEquals(GbaKeys.L or GbaKeys.R or GbaKeys.START or GbaKeys.SELECT or GbaKeys.UP, mapper.keys())
    }

    @Test
    fun releasingAKeyClearsItsBit() {
        val mapper = InputMapper()
        mapper.onKey(KeyEvent.KEYCODE_DPAD_LEFT, down = true)
        mapper.onKey(KeyEvent.KEYCODE_DPAD_LEFT, down = false)
        assertEquals(0, mapper.keys())
    }

    @Test
    fun unmappedKeyIsIgnored() {
        val mapper = InputMapper()
        mapper.onKey(KeyEvent.KEYCODE_VOLUME_UP, down = true)
        assertEquals(0, mapper.keys())
    }

    @Test
    fun r2HeldReportsFastForward() {
        val mapper = InputMapper()
        assertEquals(false, mapper.fastForward())
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_R2, down = true)
        assertEquals(true, mapper.fastForward())
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_R2, down = false)
        assertEquals(false, mapper.fastForward())
    }

    @Test
    fun releaseAllLiftsHeldKeysAndFastForward() {
        val mapper = InputMapper()
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_B, down = true)
        mapper.onKey(KeyEvent.KEYCODE_DPAD_LEFT, down = true)
        mapper.onKey(KeyEvent.KEYCODE_BUTTON_R2, down = true)

        mapper.releaseAll()

        assertEquals(0, mapper.keys())
        assertEquals(false, mapper.fastForward())
    }
}
