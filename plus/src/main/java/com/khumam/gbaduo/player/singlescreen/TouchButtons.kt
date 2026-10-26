package com.khumam.gbaduo.player.singlescreen

import com.khumam.gbaduo.emu.GbaKeys
import kotlin.math.abs
import kotlin.math.min

/** Pointer IDs, not MotionEvent indices: lifting one finger must not release another. */
internal class TouchButtons(private val onChanged: (Int) -> Unit) {
    private val pointers = HashMap<Int, Int>()
    var keys: Int = 0
        private set

    fun move(id: Int, mask: Int) {
        pointers[id] = mask and 0x3ff
        publish()
    }

    fun up(id: Int) {
        pointers.remove(id)
        publish()
    }

    fun clear() {
        pointers.clear()
        publish()
    }

    private fun publish() {
        val next = pointers.values.fold(0) { a, b -> a or b }
        if (keys != next) {
            keys = next
            onChanged(next)
        }
    }

    companion object {
        /** Shared drawing/hit bounds; the transparent space between A/B is not a button. */
        fun faceBounds(width: Float, height: Float): List<Pair<Int, SingleScreenLayout.Rect>> {
            // Fit the portrait reference as one rigid shape. Window orientation
            // must not change the angle, spacing or relative button diameter.
            val referenceWidth = min(width, height * 1.2f / 1.1f)
            val referenceHeight = referenceWidth * 1.1f / 1.2f
            val x = (width - referenceWidth) / 2
            val y = (height - referenceHeight) / 2
            val size = min(referenceWidth * .43f, referenceHeight * .53f)
            fun at(cx: Float, cy: Float) = SingleScreenLayout.Rect(cx - size / 2, cy - size / 2, size, size)
            return listOf(GbaKeys.A to at(x + referenceWidth * .76f, y + referenceHeight * .28f),
                GbaKeys.B to at(x + referenceWidth * .25f, y + referenceHeight * .72f))
        }

        fun direction(x: Float, y: Float): Int {
            if (abs(x) > 1 || abs(y) > 1 || (abs(x) < .22f && abs(y) < .22f)) return 0
            var mask = 0
            if (x < -.35f) mask = mask or GbaKeys.LEFT
            if (x > .35f) mask = mask or GbaKeys.RIGHT
            if (y < -.35f) mask = mask or GbaKeys.UP
            if (y > .35f) mask = mask or GbaKeys.DOWN
            return mask
        }
    }
}
