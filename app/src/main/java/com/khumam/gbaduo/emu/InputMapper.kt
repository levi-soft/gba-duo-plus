package com.khumam.gbaduo.emu

import android.view.KeyEvent

/**
 * Maps Android gamepad/keyboard key codes to a GBA key bitmask. Written from the
 * main thread only; read by the emu thread each frame.
 */
class InputMapper {
    var swapAb: Boolean = false

    @Volatile private var pressed = 0
    @Volatile private var touchKeys = 0
    @Volatile private var r2Held = false
    @Volatile private var touchFastForward = false

    /** True for a mapped key (caller should consume the event); false otherwise. */
    fun onKey(keyCode: Int, down: Boolean): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            r2Held = down
            return true
        }
        val bit = bitFor(keyCode) ?: return false
        pressed = if (down) pressed or bit else pressed and bit.inv()
        return true
    }

    /**
     * Lifts every held key. Call when the game window loses focus: the matching
     * key-up may then go to another window, which would leave the key stuck down.
     */
    fun releaseAll() {
        pressed = 0
        touchKeys = 0
        r2Held = false
        touchFastForward = false
    }

    /** Labelled screen controls send GBA bits directly, independently of gamepad A/B swapping. */
    fun setTouchKeys(keys: Int) {
        touchKeys = keys and 0x3ff
    }

    fun keys(): Int = pressed or touchKeys

    fun setTouchFastForward(enabled: Boolean) { touchFastForward = enabled }

    fun fastForward(): Boolean = r2Held || touchFastForward

    private fun bitFor(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_B -> if (swapAb) GbaKeys.B else GbaKeys.A
        KeyEvent.KEYCODE_BUTTON_A -> if (swapAb) GbaKeys.A else GbaKeys.B
        KeyEvent.KEYCODE_BUTTON_L1 -> GbaKeys.L
        KeyEvent.KEYCODE_BUTTON_R1 -> GbaKeys.R
        KeyEvent.KEYCODE_BUTTON_START -> GbaKeys.START
        KeyEvent.KEYCODE_BUTTON_SELECT -> GbaKeys.SELECT
        KeyEvent.KEYCODE_DPAD_UP -> GbaKeys.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> GbaKeys.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> GbaKeys.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> GbaKeys.RIGHT
        else -> null
    }
}
