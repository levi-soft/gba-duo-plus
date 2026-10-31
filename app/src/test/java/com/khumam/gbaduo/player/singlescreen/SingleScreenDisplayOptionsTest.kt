package com.khumam.gbaduo.player.singlescreen

import com.khumam.gbaduo.emu.AspectRatio
import com.khumam.gbaduo.emu.WideView
import org.junit.Assert.*
import org.junit.Test

class SingleScreenDisplayOptionsTest {
    @Test fun defaultSettingsKeepTheApprovedArrangementExactly() {
        for ((w, h) in listOf(360f to 800f, 1280f to 360f, 800f to 600f)) {
            for (swapped in listOf(false, true)) for (only in listOf(false, true)) {
                val base = SingleScreenLayout.arrange(w, h, 1f, swapped = swapped, gameOnly = only)
                assertSame(base, SingleScreenLayout.withDisplayOptions(base, 240, 160, AspectRatio.ORIGINAL, false, false))
            }
        }
    }

    @Test fun widePrimaryPaneUsesSpareWidthWithoutShrinkingThePictureHeight() {
        val base = SingleScreenLayout.arrange(1280f, 360f, 1f)
        val wide = SingleScreenLayout.withDisplayOptions(base, 240, 160, AspectRatio.ORIGINAL, false, true)
        assertTrue(wide.game.width > base.game.width)
        assertEquals(base.game.height, wide.game.height, 1f)
        assertTrue(wide.game.width / wide.game.height <= WideView.MAX_WIDTH.toFloat() / WideView.FRAME_HEIGHT + .01f)
        assertEquals(base.companion, wide.companion)
        assertEquals(base.controls, wide.controls)
    }

    @Test fun narrowPortraitDoesNotForceTheMaximumWideRatio() {
        val base = SingleScreenLayout.arrange(360f, 800f, 1f)
        val wide = SingleScreenLayout.withDisplayOptions(base, 240, 160, AspectRatio.ORIGINAL, false, true)
        assertEquals(base.game.width, wide.game.width, 1f)
        assertEquals(base.game.height, wide.game.height, 1f)
        assertEquals(1.5f, wide.game.width / wide.game.height, .01f)
    }

    @Test fun expandedWidePictureUsesSquarePixelsInsteadOfTheSelectedAspectRatio() {
        val base = SingleScreenLayout.arrange(1280f, 360f, 1f)
        val original = SingleScreenLayout.withDisplayOptions(base, 240, 160, AspectRatio.ORIGINAL, false, true)
        val fourThree = SingleScreenLayout.withDisplayOptions(base, 240, 160, AspectRatio.FOUR_THREE, false, true)
        assertTrue(original.game.width > base.game.width)
        assertEquals(original.game, fourThree.game)
    }

    @Test fun selectedRatiosAndIntegerScalingStayInsideTheirOwnGameArea() {
        val windows = listOf(320f to 480f, 360f to 800f, 1280f to 360f, 800f to 360f, 800f to 600f, 600f to 800f)
        for ((w, h) in windows) for (swapped in listOf(false, true)) for (only in listOf(false, true)) {
            val base = SingleScreenLayout.arrange(w, h, 1f, swapped = swapped, gameOnly = only)
            for (aspect in AspectRatio.entries) for (integer in listOf(false, true)) for (wide in listOf(false, true)) {
                val changed = SingleScreenLayout.withDisplayOptions(base, 240, 160, aspect, integer, wide)
                val frame = changed.game
                assertTrue("$w x $h: $frame", frame.width > 0 && frame.height > 0)
                assertTrue(frame.x >= base.gameArea.x - .01f && frame.y >= base.gameArea.y - .01f)
                assertTrue(frame.right <= base.gameArea.right + .01f && frame.bottom <= base.gameArea.bottom + .01f)
                assertEquals(base.companion, changed.companion)
                assertEquals(base.controls, changed.controls)
                assertEquals(base.logo, changed.logo)
                for (other in listOf(changed.companion, changed.logo) + changed.controls.values) {
                    if (other.width == 0f || other.height == 0f) continue
                    assertFalse("$w x $h: game overlaps $other",
                        minOf(frame.right, other.right) - maxOf(frame.x, other.x) > .1f &&
                            minOf(frame.bottom, other.bottom) - maxOf(frame.y, other.y) > .1f)
                }
            }
        }
        val pane = SingleScreenLayout.arrange(1280f, 360f, 1f, gameOnly = true)
        val ratio = SingleScreenLayout.withDisplayOptions(pane, 240, 160, AspectRatio.FOUR_THREE, false, false)
        assertEquals(4f / 3f, ratio.game.width / ratio.game.height, .01f)
        val integer = SingleScreenLayout.withDisplayOptions(pane, 240, 160, AspectRatio.ORIGINAL, true, false)
        assertEquals(0f, integer.game.width % 240, .001f)
        assertEquals(0f, integer.game.height % 160, .001f)
    }

    @Test fun gbGamesKeepTheirOriginalRatioWhenTheGbaWideSettingIsOn() {
        val base = SingleScreenLayout.arrange(1280f, 360f, 1f, gameAspect = 160f / 144f)
        assertSame(base, SingleScreenLayout.withDisplayOptions(base, 160, 144, AspectRatio.ORIGINAL, false, true))
    }
}
