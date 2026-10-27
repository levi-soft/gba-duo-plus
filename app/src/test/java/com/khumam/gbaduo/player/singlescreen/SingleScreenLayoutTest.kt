package com.khumam.gbaduo.player.singlescreen

import org.junit.Assert.*
import org.junit.Test

class SingleScreenLayoutTest {
    @Test fun gameOnlyPortraitBalancesTheScreenShouldersAndThumbsAboveTheBottomUtilities() {
        for ((w, h) in listOf(360f to 800f, 412f to 915f, 408f to 997f, 320f to 480f, 600f to 800f)) {
            for (aspect in listOf(1.5f, 160f / 144f)) {
                val layout = SingleScreenLayout.arrange(w, h, 1f, aspect, portraitTopInset = 32f, gameOnly = true)
                val shoulders = layout.controls.getValue(SingleScreenLayout.Control.L)
                val theme = layout.controls.getValue(SingleScreenLayout.Control.THEME)
                val dpad = layout.controls.getValue(SingleScreenLayout.Control.DPAD)
                val face = layout.controls.getValue(SingleScreenLayout.Control.FACE)
                val menu = layout.controls.getValue(SingleScreenLayout.Control.MENU)
                assertTrue(shoulders.y >= layout.game.bottom)
                assertTrue(dpad.y >= shoulders.bottom)
                assertTrue(face.y >= shoulders.bottom)
                assertEquals(shoulders.y, theme.y, .001f)
                assertEquals(dpad.y + dpad.height / 2, face.y + face.height / 2, .001f)
                assertTrue(menu.y >= maxOf(dpad.bottom, face.bottom))
                assertTrue(menu.bottom <= h)
                val dual = SingleScreenLayout.arrange(w, h, 1f, aspect, portraitTopInset = 32f)
                assertEquals(dual.controls.getValue(SingleScreenLayout.Control.MENU), menu)
                assertEquals(w / 2, layout.logo.x + layout.logo.width / 2, .001f)
            }
        }
    }
    @Test fun gameOnlyPortraitMatchesTheAnnotatedScreenAndThumbRegions() {
        val layout = SingleScreenLayout.arrange(360f, 800f, 1f, portraitTopInset = 32f, gameOnly = true)
        val pad = layout.controls.getValue(SingleScreenLayout.Control.DPAD)
        val shoulders = layout.controls.getValue(SingleScreenLayout.Control.L)
        assertTrue(layout.game.y in 80f..100f)
        assertTrue(shoulders.y + shoulders.height / 2 in 410f..440f)
        assertTrue(pad.y + pad.height / 2 in 570f..600f)
        assertTrue(layout.controls.getValue(SingleScreenLayout.Control.MENU).y >= 740f)
    }
    @Test fun gameOnlyUsesOneLargerFrameAndKeepsAllControlsClearAcrossWindows() {
        for ((width, height) in listOf(997 to 408, 800 to 360, 640 to 360, 480 to 320, 340 to 340,
            360 to 800, 412 to 915, 320 to 480, 600 to 600, 800 to 600, 600 to 800, 1024 to 768)) {
            for (density in listOf(1f, 2f, 3f)) for (aspect in listOf(1.5f, 160f / 144f)) {
                val w = width * density; val h = height * density
                val inset = if (w < h) 32f * density else 0f
                val normal = SingleScreenLayout.arrange(w, h, density, aspect, inset)
                val layout = SingleScreenLayout.arrange(w, h, density, aspect, inset, gameOnly = true)
                assertTrue(layout.gameOnly)
                assertEquals(0f, layout.companion.width, 0f)
                assertEquals(0f, layout.companion.height, 0f)
                assertEquals(aspect, layout.game.width / layout.game.height, .001f)
                assertTrue(layout.game.width >= normal.game.width - .01f)
                assertEquals(w / 2, layout.game.x + layout.game.width / 2, .01f)
                assertEquals(SingleScreenLayout.Control.entries.toSet(), layout.controls.keys)
                val face = layout.controls.getValue(SingleScreenLayout.Control.FACE)
                val buttons = TouchButtons.faceBounds(face.width, face.height).map { (_, r) -> r.copy(x = face.x + r.x, y = face.y + r.y) }
                val boxes = listOf(layout.game, layout.logo) + layout.controls.filterKeys { it != SingleScreenLayout.Control.FACE }.values + buttons
                for (r in boxes) assertTrue("$width x $height: $r", r.x >= 0 && r.y >= inset && r.right <= w + .01f && r.bottom <= h + .01f)
                for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                    val a = boxes[i]; val b = boxes[j]
                    assertFalse("$width x $height: overlapping $a / $b",
                        minOf(a.right, b.right) - maxOf(a.x, b.x) > .1f && minOf(a.bottom, b.bottom) - maxOf(a.y, b.y) > .1f)
                }
            }
        }
    }
    @Test fun expandedWindowsStackInBothOrientationsButOuterPhoneWindowKeepsSideLayout() {
        for ((w, h) in listOf(800 to 600, 600 to 800, 600 to 600, 720 to 540, 540 to 720, 1024 to 768)) {
            for (swapped in listOf(false, true)) for (aspect in listOf(1.5f, 160f / 144f)) {
                val layout = SingleScreenLayout.arrange(w.toFloat(), h.toFloat(), 1f, aspect, swapped = swapped)
                val first = if (swapped) layout.companion else layout.game
                val second = if (swapped) layout.game else layout.companion
                assertTrue("$w x $h must stack", first.bottom < second.y)
                assertEquals("$w x $h equal screen widths", first.width, second.width, .001f)
                assertEquals("$w x $h aligned left edges", first.x, second.x, .001f)
                assertEquals(w / 2f, first.x + first.width / 2, .001f)
            }
        }
        assertEquals(SingleScreenLayout.Mode.PHONE_LANDSCAPE,
            SingleScreenLayout.arrange(2520f, 1080f, 3f).mode)
        assertEquals(SingleScreenLayout.Mode.EXPANDED_LANDSCAPE_STACK,
            SingleScreenLayout.arrange(2160f, 1920f, 3f).mode)
    }

    @Test fun swappingScreensPreservesBothAspectsAndDoesNotMoveControls() {
        for ((w, h) in listOf(997f to 408f, 408f to 997f, 800f to 600f, 600f to 800f)) {
            for (aspect in listOf(1.5f, 160f / 144f)) {
                val normal = SingleScreenLayout.arrange(w, h, 1f, aspect)
                val swapped = SingleScreenLayout.arrange(w, h, 1f, aspect, swapped = true)
                assertEquals(aspect, swapped.game.width / swapped.game.height, .001f)
                assertEquals(4f / 3f, swapped.companion.width / swapped.companion.height, .001f)
                if (normal.mode == SingleScreenLayout.Mode.PHONE_LANDSCAPE) assertTrue(swapped.companion.right < swapped.game.x)
                else assertTrue(swapped.companion.bottom < swapped.game.y)
                for ((control, rect) in normal.controls) {
                    val changed = swapped.controls.getValue(control)
                    assertEquals(rect.x, changed.x, .001f); assertEquals(rect.y, changed.y, .001f)
                    assertEquals(rect.width, changed.width, .001f); assertEquals(rect.height, changed.height, .001f)
                }
            }
        }
    }
    @Test fun portraitReservesOnlyTheHardwareCutoutAndLandscapeUsesTheFullDeck() {
        val portrait = SingleScreenLayout.arrange(408f, 997f, 1f, portraitTopInset = 38f)
        assertTrue(portrait.game.y >= 38f)
        val boxes = listOf(portrait.game, portrait.companion) + portrait.controls.values
        assertTrue(boxes.all { it.y >= 38f && it.bottom <= 997f })
        assertEquals(4f / 3f, portrait.companion.width / portrait.companion.height, .001f)
        assertEquals(SingleScreenLayout.arrange(997f, 408f, 1f),
            SingleScreenLayout.arrange(997f, 408f, 1f, portraitTopInset = 38f))
    }
    @Test fun phoneAndTabletFramesPreserveAspectAndNeverCoverControls() {
        for ((w, h) in listOf(800 to 360, 640 to 360, 960 to 432, 360 to 800, 412 to 915, 423 to 720, 800 to 600, 600 to 800,
            320 to 480, 480 to 320, 340 to 340, 400 to 470, 408 to 997, 997 to 408)) {
            for (swapped in listOf(false, true)) {
            val layout = SingleScreenLayout.arrange(w.toFloat(), h.toFloat(), 1f, swapped = swapped)
            assertEquals(1.5f, layout.game.width / layout.game.height, .0001f)
            assertEquals(4f / 3f, layout.companion.width / layout.companion.height, .0001f)
            val face = layout.controls.getValue(SingleScreenLayout.Control.FACE)
            assertTrue(face.x >= 0 && face.y >= 0 && face.right <= w + .01f && face.bottom <= h + .01f)
            val buttons = TouchButtons.faceBounds(face.width, face.height)
                .map { (_, rect) -> rect.copy(x = face.x + rect.x, y = face.y + rect.y) }
            val boxes = listOf(layout.game, layout.companion, layout.logo) +
                layout.controls.filterKeys { it != SingleScreenLayout.Control.FACE }.values + buttons
            for (rect in boxes) {
                assertTrue("$w x $h: $rect", rect.x >= 0 && rect.y >= 0 && rect.right <= w + .01 && rect.bottom <= h + .01)
            }
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val a = boxes[i]; val b = boxes[j]
                val overlap = minOf(a.right, b.right) - maxOf(a.x, b.x) > .1f &&
                    minOf(a.bottom, b.bottom) - maxOf(a.y, b.y) > .1f
                assertFalse("$w x $h: overlapping $a / $b", overlap)
            }
            }
        }
    }

    @Test fun footerKeepsSwapNextToSaveAndThemeStaysSeparateFromGameButtons() {
        for ((w, h) in listOf(997f to 408f, 360f to 800f, 800f to 600f, 600f to 800f)) {
            val layout = SingleScreenLayout.arrange(w, h, 1f)
            val swap = layout.controls.getValue(SingleScreenLayout.Control.SWAP)
            val save = layout.controls.getValue(SingleScreenLayout.Control.QUICK_SAVE)
            val theme = layout.controls.getValue(SingleScreenLayout.Control.THEME)
            val face = layout.controls.getValue(SingleScreenLayout.Control.FACE)
            assertTrue(swap.right < save.x)
            assertEquals(swap.y, save.y, .001f)
            assertEquals(theme.width, theme.height, .001f)
            if (layout.mode == SingleScreenLayout.Mode.PHONE_LANDSCAPE) assertTrue(theme.bottom < layout.logo.y)
            else if (layout.mode == SingleScreenLayout.Mode.EXPANDED_LANDSCAPE_STACK) assertTrue(theme.bottom < face.y)
            else assertTrue(theme.y < face.y)
        }
    }

    @Test fun portraitThemeSharesShoulderRowAndLogoCentersBetweenThumbControls() {
        for ((w, h) in listOf(360f to 800f, 408f to 997f, 320f to 480f, 600f to 800f)) {
            val layout = SingleScreenLayout.arrange(w, h, 1f)
            val theme = layout.controls.getValue(SingleScreenLayout.Control.THEME)
            val shoulder = layout.controls.getValue(SingleScreenLayout.Control.L)
            val dpad = layout.controls.getValue(SingleScreenLayout.Control.DPAD)
            assertEquals(w / 2, theme.x + theme.width / 2, .001f)
            assertEquals(shoulder.y + shoulder.height / 2, theme.y + theme.height / 2, .001f)
            assertEquals(w / 2, layout.logo.x + layout.logo.width / 2, .001f)
            assertEquals(dpad.y + dpad.height / 2, layout.logo.y + layout.logo.height / 2, .001f)
        }
    }

    @Test fun faceButtonsKeepPortraitAngleAndSpacingAndExpandedThemeUsesUpperSideRail() {
        for ((w, h) in listOf(800f to 400f, 997f to 408f, 600f to 600f, 800f to 600f)) {
            val layout = SingleScreenLayout.arrange(w, h, 1f)
            val face = layout.controls.getValue(SingleScreenLayout.Control.FACE)
            val buttons = TouchButtons.faceBounds(face.width, face.height).map { it.second }
            val a = buttons[0]; val b = buttons[1]
            assertEquals(a.width, b.width, .001f)
            val dx = (a.x - b.x) / a.width
            val dy = (b.y - a.y) / a.height
            val portrait = TouchButtons.faceBounds(120f, 110f).map { it.second }
            assertEquals((portrait[0].x - portrait[1].x) / portrait[0].width, dx, .001f)
            assertEquals((portrait[1].y - portrait[0].y) / portrait[0].height, dy, .001f)
            if (layout.mode == SingleScreenLayout.Mode.EXPANDED_LANDSCAPE_STACK) {
                val theme = layout.controls.getValue(SingleScreenLayout.Control.THEME)
                assertEquals(face.x + face.width / 2, theme.x + theme.width / 2, .001f)
                assertTrue(theme.bottom < face.y)
            }
        }
    }

    @Test fun phoneFramesAreVerticallyCenteredAboveFooterWithAlignedTops() {
        for ((w, h) in listOf(800f to 400f, 997f to 408f, 640f to 360f)) for (swapped in listOf(false, true)) {
            val layout = SingleScreenLayout.arrange(w, h, 1f, swapped = swapped)
            val main = if (swapped) layout.companion else layout.game
            val small = if (swapped) layout.game else layout.companion
            val footer = minOf(56f, h * .14f)
            assertEquals((h - footer) / 2, main.y + main.height / 2, .001f)
            assertEquals(main.y, small.y, .001f)
            assertEquals(minOf(8f, h * .015f) * 2, small.x - main.right, .001f)
            assertTrue("Companion must retain its touch area", layout.companion.width >= w / 3)
        }
    }

    @Test fun gbFrameAndPhoneDensityDoNotChangeCompanionAspect() {
        val layout = SingleScreenLayout.arrange(2400f, 1080f, 3f, 160f / 144f)
        assertEquals(160f / 144f, layout.game.width / layout.game.height, .0001f)
        assertEquals(4f / 3f, layout.companion.width / layout.companion.height, .0001f)
        assertTrue(layout.game.right < layout.companion.x)
        assertTrue(layout.game.width > layout.companion.width)
        assertTrue(layout.companion.bottom < layout.controls.getValue(SingleScreenLayout.Control.FACE).y)
    }
}
