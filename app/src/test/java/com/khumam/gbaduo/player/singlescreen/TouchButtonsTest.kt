package com.khumam.gbaduo.player.singlescreen

import com.khumam.gbaduo.emu.GbaKeys
import org.junit.Assert.*
import org.junit.Test

class TouchButtonsTest {
    @Test fun pointerIdsAndDuplicateHoldsSurviveLiftSlideAndCancel() {
        val events = ArrayList<Int>()
        val touch = TouchButtons(events::add)
        touch.move(19, GbaKeys.A)
        touch.move(42, GbaKeys.A or GbaKeys.B)
        touch.up(19)
        assertEquals(GbaKeys.A or GbaKeys.B, touch.keys)
        touch.move(42, GbaKeys.B)
        touch.move(5, GbaKeys.LEFT or GbaKeys.DOWN)
        touch.up(42)
        assertEquals(GbaKeys.LEFT or GbaKeys.DOWN, touch.keys)
        touch.clear()
        assertEquals(0, events.last())
    }

    @Test fun dpadSupportsEightDirectionsDeadzoneAndSlidingOut() {
        assertEquals(GbaKeys.UP or GbaKeys.RIGHT, TouchButtons.direction(.8f, -.8f))
        assertEquals(GbaKeys.LEFT, TouchButtons.direction(-.8f, 0f))
        assertEquals(0, TouchButtons.direction(.1f, -.1f))
        assertEquals(0, TouchButtons.direction(1.2f, -.8f))
    }
}
