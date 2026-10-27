package com.khumam.gbaduo.player.singlescreen

import kotlin.math.max
import kotlin.math.min

/** Pixel geometry. Window size selects the layout; device names and native resolutions do not. */
internal object SingleScreenLayout {
    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float) {
        val right get() = x + width
        val bottom get() = y + height
    }

    enum class Control { DPAD, FACE, L, R, SELECT, START, MENU, QUICK_SAVE, QUICK_LOAD, FAST_FORWARD, THEME, SWAP }
    enum class Mode { PHONE_LANDSCAPE, PORTRAIT_STACK, EXPANDED_LANDSCAPE_STACK, EXPANDED_PORTRAIT_STACK }
    enum class ScreenMode {
        GAME_FIRST, COMPANION_FIRST, GAME_ONLY;
        fun next() = entries[(ordinal + 1) % entries.size]
    }
    data class Arrangement(val game: Rect, val companion: Rect, val controls: Map<Control, Rect>, val mode: Mode,
        val logo: Rect, val gameOnly: Boolean = false)

    private val footerControls = listOf(Control.SWAP, Control.QUICK_SAVE, Control.SELECT, Control.MENU,
        Control.START, Control.QUICK_LOAD, Control.FAST_FORWARD)

    fun isExpandedWindow(width: Float, height: Float, density: Float): Boolean {
        val shortest = min(width, height).coerceAtLeast(1f)
        val shortestDp = shortest / density.coerceAtLeast(.1f)
        return shortestDp >= 600f || (shortestDp >= 480f && max(width, height) / shortest <= 1.6f)
    }

    fun arrange(width: Float, height: Float, density: Float, gameAspect: Float = 1.5f,
        portraitTopInset: Float = 0f, swapped: Boolean = false, gameOnly: Boolean = false): Arrangement {
        val w = width.coerceAtLeast(1f)
        val topInset = if (width < height) portraitTopInset.coerceIn(0f, (height - 1f).coerceAtLeast(0f)) else 0f
        val h = (height - topInset).coerceAtLeast(1f)
        fun shifted(rect: Rect) = rect.copy(y = rect.y + topInset)
        val expanded = isExpandedWindow(width, height, density)
        val mode = when {
            width < height && expanded -> Mode.EXPANDED_PORTRAIT_STACK
            width < height -> Mode.PORTRAIT_STACK
            expanded -> Mode.EXPANDED_LANDSCAPE_STACK
            else -> Mode.PHONE_LANDSCAPE
        }
        val gap = min(8f * density, h * .015f)
        val footer = min(56f * density, h * .14f)
        val hit = min(48f * density, min(w * .135f, h * .16f))
        val firstAspect = if (swapped) 4f / 3f else gameAspect
        val secondAspect = if (swapped) gameAspect else 4f / 3f
        val stackedRatio = 1f / gameAspect + .75f
        val controls = LinkedHashMap<Control, Rect>()
        fun centered(control: Control, cx: Float, cy: Float, cw: Float, ch: Float = cw) {
            controls[control] = Rect((cx - cw / 2).coerceIn(0f, (w - cw).coerceAtLeast(0f)),
                (cy - ch / 2).coerceIn(0f, (h - ch).coerceAtLeast(0f)), cw, ch)
        }
        fun footerRow(cx: Float, availableWidth: Float) {
            val groupWidth = min(availableWidth, 392f * density)
            val buttonWidth = min(hit, groupWidth / (footerControls.size + .8f))
            val step = (groupWidth - buttonWidth) / (footerControls.size - 1)
            val start = cx - groupWidth / 2 + buttonWidth / 2
            footerControls.forEachIndexed { i, control -> centered(control, start + i * step, h - footer / 2, buttonWidth) }
        }
        val portrait = width < height
        var logo: Rect
        if (portrait) {
            val small = min(hit, w * .105f)
            val bottom = h - small / 2 - gap * .4f
            footerControls.forEachIndexed { i, control -> centered(control,
                w * (.08f + i * .84f / (footerControls.size - 1)), bottom, small) }
            val logoHeight = min(36f * density, h * .08f)
            val logoWidth = min(88f * density, w * .30f)
            logo = Rect((w - logoWidth) / 2, 0f, logoWidth, logoHeight)
        } else {
            val rail = w * if (expanded) .20f else .12f
            val logoWidth = min(88f * density, rail * .90f)
            logo = Rect((rail - logoWidth) / 2, h * if (expanded) .34f else .29f,
                logoWidth, min(36f * density, h * .09f))
        }
        if (gameOnly) {
            val game: Rect
            if (portrait) {
                // Balance three separate zones above the fixed utility row.
                val playBottom = controls.getValue(Control.MENU).y - gap
                val controlHeight = min(260f * density, h * .46f)
                val screenBox = Rect(gap, gap, (w - gap * 2).coerceAtLeast(1f),
                    (playBottom * .48f - gap * 2).coerceAtLeast(1f))
                val fitted = fit(screenBox, gameAspect)
                game = fitted.copy(y = screenBox.y + (screenBox.height - fitted.height) / 2)
                val small = min(hit, w * .105f)
                val body = controlHeight - small - gap
                val touch = min(hit, body * .27f)
                val pad = min(w * .36f, (body - touch) * .95f)
                val face = min(w * .28f, (body - touch) / 1.1f)
                val shoulderY = playBottom * .54f
                val centre = playBottom * .76f
                centered(Control.DPAD, w * .18f, centre, pad)
                centered(Control.FACE, w * .82f, centre, face * 1.2f, face * 1.1f)
                centered(Control.L, w * .13f, shoulderY, w * .23f, touch)
                centered(Control.R, w * .87f, shoulderY, w * .23f, touch)
                centered(Control.THEME, w / 2, shoulderY, touch)
                logo = logo.copy(y = centre - logo.height / 2)
            } else {
                val rail = w * if (expanded) .18f else .16f
                val screenBox = Rect(rail + gap * 2, gap, (w - rail * 2 - gap * 4).coerceAtLeast(1f),
                    (h - footer - gap * 2).coerceAtLeast(1f))
                val fitted = fit(screenBox, gameAspect)
                game = fitted.copy(y = screenBox.y + (screenBox.height - fitted.height) / 2)
                val pad = min(rail * .90f, min(h * .38f, 144f * density))
                val faceWidth = min(rail * .90f, 132f * density)
                val faceHeight = faceWidth * 1.1f / 1.2f
                val handY = min(h * .62f, h - footer - gap - max(pad, faceHeight) / 2)
                centered(Control.DPAD, rail / 2, handY, pad)
                centered(Control.FACE, w - rail / 2, handY, faceWidth, faceHeight)
                centered(Control.THEME, rail / 2, h * .12f, hit)
                centered(Control.L, w * .06f, h - footer / 2, w * .105f, footer * .8f)
                centered(Control.R, w * .94f, h - footer / 2, w * .105f, footer * .8f)
                footerRow(w / 2, screenBox.width)
                val logoWidth = min(88f * density, rail * .90f)
                logo = Rect((rail - logoWidth) / 2, h * .29f, logoWidth, min(36f * density, h * .09f))
            }
            return Arrangement(shifted(game), Rect(0f, topInset, 0f, 0f),
                controls.mapValues { shifted(it.value) }, mode, shifted(logo), gameOnly = true)
        }
        val first: Rect
        val second: Rect
        when (mode) {
            Mode.PHONE_LANDSCAPE -> {
                val leftRail = w * .12f
                // Controls retain their positions while the swapped frames reclaim unused width.
                val smallRatio = max(.75f, 1f / gameAspect)
                val screenHeightLimit = (h - footer - gap * 3 - min(56f * density, h * .16f)).coerceAtLeast(1f)
                val controlWidth = min(w * .345f,
                    screenHeightLimit / smallRatio)
                val controlBox = Rect(w - gap - controlWidth, gap, controlWidth, controlWidth * smallRatio)
                val baselineBox = Rect(leftRail + gap, gap, controlBox.x - leftRail - gap * 3, h - footer - gap * 2)
                fun frames(primaryAspect: Float, secondaryAspect: Float): Pair<Rect, Rect> {
                    val extraWidth = (baselineBox.width - baselineBox.height * primaryAspect).coerceAtLeast(0f)
                    val secondaryWidth = min(controlWidth + extraWidth, screenHeightLimit * secondaryAspect)
                    val secondaryBox = Rect(w - gap - secondaryWidth, gap, secondaryWidth, secondaryWidth / secondaryAspect)
                    val primaryBox = Rect(leftRail + gap, gap, secondaryBox.x - leftRail - gap * 3, h - footer - gap * 2)
                    val fitted = fit(primaryBox, primaryAspect)
                    val primary = fitted.copy(x = primaryBox.right - fitted.width)
                    val centeredPrimary = primary.copy(y = primaryBox.y + (primaryBox.height - primary.height) / 2)
                    return centeredPrimary to fit(secondaryBox, secondaryAspect).copy(y = centeredPrimary.y)
                }
                val normalFrames = frames(gameAspect, 4f / 3f)
                val swappedFrames = frames(4f / 3f, gameAspect)
                val selected = if (swapped) swappedFrames else normalFrames
                first = selected.first
                second = selected.second
                // Keep one portrait-style A/B cluster in both screen orders.
                // Its lower-left button can use the empty space beside R.
                val faceTop = max(normalFrames.second.bottom, swappedFrames.second.bottom) + gap
                val faceScale = min(110f * density, leftRail * .95f)
                val faceHeight = min(faceScale * 1.1f, (h - gap - faceTop).coerceAtLeast(1f))
                val faceWidth = faceHeight * 1.2f / 1.1f
                val pad = min(h * .44f, leftRail * .90f)
                centered(Control.DPAD, leftRail / 2, h * .64f, pad)
                controls[Control.FACE] = Rect(controlBox.x + (controlBox.width - faceWidth) / 2,
                    faceTop, faceWidth, faceHeight)
                centered(Control.THEME, leftRail / 2, h * .12f, hit)
                centered(Control.L, w * .06f, h - footer / 2, w * .105f, footer * .8f)
                centered(Control.R, w * .94f, h - footer / 2, w * .105f, footer * .8f)
                footerRow(baselineBox.x + baselineBox.width / 2, baselineBox.width)
            }
            Mode.EXPANDED_LANDSCAPE_STACK -> {
                val rail = w * .20f
                val columnWidth = w - rail * 2 - gap * 4
                val screenHeight = h - footer - gap * 3
                val paneWidth = min(columnWidth, screenHeight / stackedRatio)
                val top = gap + (screenHeight - paneWidth * stackedRatio) / 2
                first = Rect((w - paneWidth) / 2, top, paneWidth, paneWidth / firstAspect)
                second = Rect(first.x, first.bottom + gap, paneWidth, paneWidth / secondAspect)
                val pad = min(rail * .90f, min(h * .28f, 144f * density))
                val face = min(rail * .42f, h * .18f)
                val handY = min(h * .68f, h - footer * .9f - gap - pad / 2)
                centered(Control.DPAD, rail / 2, handY, pad)
                val faceTop = handY - face / 2
                controls[Control.FACE] = Rect(w - rail / 2 - face, faceTop, face * 2,
                    face * 2 * 1.1f / 1.2f)
                centered(Control.THEME, w - rail / 2, h * .38f, hit)
                centered(Control.L, w * .06f, h - footer / 2, w * .105f, footer * .8f)
                centered(Control.R, w * .94f, h - footer / 2, w * .105f, footer * .8f)
                footerRow(w / 2, columnWidth)
            }
            Mode.PORTRAIT_STACK, Mode.EXPANDED_PORTRAIT_STACK -> {
                val controlHeight = min(260f * density, h * .46f)
                val paneWidth = min(w - gap * 2, (h - controlHeight - gap * 3) / stackedRatio)
                first = Rect((w - paneWidth) / 2, gap, paneWidth, paneWidth / firstAspect)
                second = Rect(first.x, first.bottom + gap, paneWidth, paneWidth / secondAspect)
                val top = second.bottom + gap
                val room = h - top
                val small = min(hit, w * .105f)
                val body = room - small - gap
                val touch = min(hit, body * .27f)
                val pad = min(w * .36f, (body - touch) * .95f)
                val face = min(w * .28f, (body - touch) / 1.1f)
                val centre = top + touch + (body - touch) / 2
                centered(Control.DPAD, w * .18f, centre, pad)
                centered(Control.FACE, w * .82f, centre, face * 1.2f, face * 1.1f)
                centered(Control.L, w * .13f, top + touch / 2, w * .23f, touch)
                centered(Control.R, w * .87f, top + touch / 2, w * .23f, touch)
                centered(Control.THEME, w / 2, top + touch / 2, touch)
                logo = logo.copy(y = centre - logo.height / 2)
            }
        }
        return Arrangement(shifted(if (swapped) second else first), shifted(if (swapped) first else second),
            controls.mapValues { shifted(it.value) }, mode, shifted(logo))
    }

    private fun fit(box: Rect, aspect: Float): Rect {
        val w = min(box.width, box.height * aspect)
        return Rect(box.x + (box.width - w) / 2, box.y, w, w / aspect)
    }
}
