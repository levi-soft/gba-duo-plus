package com.khumam.gbaduo.player.singlescreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.khumam.gbaduo.emu.GbaKeys
import com.khumam.gbaduo.player.singlescreen.SingleScreenLayout.Control
import kotlin.math.min

/** Small independent touch regions leave every companion pixel available to Compose. */
internal class TouchControlView(
    context: Context,
    val control: Control,
    private val changed: (Int) -> Unit,
    private val menu: () -> Unit = {},
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointers = TouchButtons { keys -> changed(keys); invalidate() }
    private var menuArmed = false
    private var activeGesture = false
    private val density get() = resources.displayMetrics.density
    var darkTheme = false
        set(value) {
            field = value
            if (control == Control.THEME) contentDescription = if (value) "Switch to light theme" else "Switch to dark theme"
            invalidate()
        }
    private val utility get() = when (control) {
        Control.MENU, Control.QUICK_SAVE, Control.QUICK_LOAD, Control.FAST_FORWARD, Control.THEME, Control.SWAP -> true
        else -> false
    }

    init {
        isClickable = true
        isFocusable = false
        contentDescription = when (control) {
            Control.DPAD -> "D-pad"
            Control.FACE -> "A and B"
            Control.QUICK_SAVE -> "Quick save"
            Control.QUICK_LOAD -> "Quick load"
            Control.FAST_FORWARD -> "Fast-forward"
            Control.SWAP -> "Swap game and companion screens"
            else -> control.name.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    fun release() {
        menuArmed = false
        activeGesture = false
        pointers.clear()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (control == Control.FACE && hit(event.x, event.y) == 0) return false
            activeGesture = true
        }
        // Rotation, focus loss and menus invalidate the old gesture until a fresh down.
        if (!activeGesture && event.actionMasked != MotionEvent.ACTION_CANCEL) return true
        if (utility) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> menuArmed = true
                MotionEvent.ACTION_CANCEL -> menuArmed = false
                MotionEvent.ACTION_UP -> {
                    val activate = menuArmed && event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                    menuArmed = false
                    activeGesture = false
                    if (activate) performClick()
                }
            }
            invalidate()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> release()
            MotionEvent.ACTION_UP -> { activeGesture = false; pointers.clear(); performClick() }
            MotionEvent.ACTION_POINTER_UP -> {
                val removed = event.actionIndex
                pointers.up(event.getPointerId(removed))
                for (i in 0 until event.pointerCount) if (i != removed) {
                    pointers.move(event.getPointerId(i), hit(event.getX(i), event.getY(i)))
                }
            }
            else -> for (i in 0 until event.pointerCount) {
                pointers.move(event.getPointerId(i), hit(event.getX(i), event.getY(i)))
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (utility) menu()
        return true
    }

    internal fun hit(x: Float, y: Float): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        return when (control) {
            Control.DPAD -> TouchButtons.direction(x / width * 2 - 1, y / height * 2 - 1)
            Control.FACE -> faceButtons().filter { (_, rect) -> rect.contains(x, y) }
                .fold(0) { mask, (bit, _) -> mask or bit }
            Control.L -> GbaKeys.L
            Control.R -> GbaKeys.R
            Control.SELECT -> GbaKeys.SELECT
            Control.START -> GbaKeys.START
            Control.MENU, Control.QUICK_SAVE, Control.QUICK_LOAD, Control.FAST_FORWARD, Control.THEME, Control.SWAP -> 0
        }
    }

    private fun faceButtons(): List<Pair<Int, RectF>> {
        return TouchButtons.faceBounds(width.toFloat(), height.toFloat()).map { (key, rect) ->
            key to RectF(rect.x, rect.y, rect.right, rect.bottom)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = min(width.toFloat(), height.toFloat())
        when (control) {
            Control.DPAD -> {
                dpad(canvas, unit)
            }
            Control.FACE -> for ((bit, rect) in faceButtons()) {
                button(canvas, rect, if (bit == GbaKeys.A) "A" else "B", pointers.keys and bit != 0, true,
                    rect.height() * .43f, if (bit == GbaKeys.A) SingleScreenSkin.RED else SingleScreenSkin.YELLOW)
            }
            Control.L, Control.R -> button(canvas, RectF(2f, height * .15f, width - 2f, height * .85f),
                control.name, pointers.keys != 0, false, unit * .43f)
            Control.THEME -> {
                val cx = width / 2f
                val cy = height / 2f
                val r = unit * .43f
                button(canvas, RectF(cx - r, cy - r, cx + r, cy + r), "", menuArmed, true, unit * .2f)
                paint.color = if (darkTheme) Color.rgb(235, 238, 240) else SingleScreenSkin.INK
                if (darkTheme) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = unit * .035f
                    canvas.drawCircle(cx, cy, unit * .11f, paint)
                    repeat(8) { i ->
                        canvas.save(); canvas.rotate(i * 45f, cx, cy)
                        canvas.drawLine(cx, cy - unit * .17f, cx, cy - unit * .24f, paint)
                        canvas.restore()
                    }
                } else {
                    paint.style = Paint.Style.FILL
                    val moon = Path().apply {
                        moveTo(cx + unit * .10f, cy - unit * .21f)
                        cubicTo(cx - unit * .28f, cy - unit * .21f, cx - unit * .28f, cy + unit * .21f, cx + unit * .10f, cy + unit * .21f)
                        cubicTo(cx - unit * .11f, cy + unit * .08f, cx - unit * .11f, cy - unit * .08f, cx + unit * .10f, cy - unit * .21f)
                        close()
                    }
                    canvas.drawPath(moon, paint)
                }
            }
            else -> {
                val label = when (control) {
                    Control.QUICK_SAVE -> "Save"
                    Control.QUICK_LOAD -> "Load"
                    Control.FAST_FORWARD -> ">>"
                    Control.THEME -> if (darkTheme) "Light" else "Dark"
                    else -> control.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                button(canvas, RectF(width * .02f, height * .22f, width * .98f, height * .78f), label,
                    pointers.keys != 0 || menuArmed || isActivated, false, unit * .25f)
            }
        }
    }

    private fun dpad(canvas: Canvas, unit: Float) {
        val margin = unit * .045f
        val lo = unit * .34f
        val hi = unit * .66f
        val path = Path().apply {
            moveTo(lo, margin); lineTo(hi, margin); lineTo(hi, lo); lineTo(unit - margin, lo)
            lineTo(unit - margin, hi); lineTo(hi, hi); lineTo(hi, unit - margin); lineTo(lo, unit - margin)
            lineTo(lo, hi); lineTo(margin, hi); lineTo(margin, lo); lineTo(lo, lo); close()
        }
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = if (darkTheme) Color.rgb(13, 15, 17) else Color.rgb(166, 170, 171)
        canvas.save(); canvas.translate(0f, unit * .025f); canvas.drawPath(path, paint); canvas.restore()
        paint.shader = LinearGradient(0f, 0f, 0f, unit,
            if (darkTheme) Color.rgb(67, 71, 75) else Color.WHITE,
            if (darkTheme) Color.rgb(39, 42, 45) else Color.rgb(211, 214, 212), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, unit * .013f)
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = if (darkTheme) Color.rgb(100, 105, 110) else Color.rgb(146, 151, 152)
        canvas.drawPath(path, paint)
        for ((bit, angle) in listOf(GbaKeys.UP to 0f, GbaKeys.RIGHT to 90f, GbaKeys.DOWN to 180f, GbaKeys.LEFT to 270f)) {
            canvas.save(); canvas.rotate(angle, unit / 2, unit / 2)
            paint.style = Paint.Style.FILL
            paint.color = if (pointers.keys and bit != 0) SingleScreenSkin.BLUE else if (darkTheme) Color.rgb(187, 193, 198) else Color.rgb(120, 126, 130)
            val triangle = Path().apply {
                moveTo(unit * .5f, unit * .125f); lineTo(unit * .415f, unit * .255f)
                lineTo(unit * .585f, unit * .255f); close()
            }
            canvas.drawPath(triangle, paint); canvas.restore()
        }
        paint.style = Paint.Style.STROKE
        paint.color = if (darkTheme) Color.rgb(105, 111, 116) else Color.rgb(194, 198, 197)
        paint.strokeWidth = unit * .012f
        canvas.drawCircle(unit / 2, unit / 2, unit * .075f, paint)
    }

    private fun button(canvas: Canvas, rect: RectF, label: String, held: Boolean, circle: Boolean, font: Float,
        color: Int = if (darkTheme) Color.rgb(62, 67, 72) else Color.rgb(220, 223, 221)) {
        val edge = min(rect.width(), rect.height()) * .055f
        val radius = min(9 * density, rect.height() * .27f)
        val inner = RectF(rect)
        fun shape(box: RectF) {
            if (circle) canvas.drawOval(box, paint) else canvas.drawRoundRect(box, radius, radius, paint)
        }
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = if (held) SingleScreenSkin.BLUE else if (darkTheme) Color.rgb(105, 111, 117) else Color.rgb(152, 157, 158)
        shape(rect)
        inner.inset(edge, edge)
        paint.color = if (darkTheme) Color.rgb(29, 32, 35) else Color.WHITE
        shape(inner)
        inner.inset(edge * .55f, edge * .55f)
        fun tint(amount: Float) = Color.rgb(
            (Color.red(color) * amount).toInt().coerceIn(0, 255),
            (Color.green(color) * amount).toInt().coerceIn(0, 255),
            (Color.blue(color) * amount).toInt().coerceIn(0, 255))
        paint.shader = LinearGradient(0f, inner.top, 0f, inner.bottom,
            tint(if (held) .80f else 1.10f), tint(if (held) .90f else .94f), Shader.TileMode.CLAMP)
        shape(inner)
        paint.shader = null
        text(canvas, label, rect.centerX(), rect.centerY() + if (held) edge else 0f, font)
    }

    private fun text(canvas: Canvas, label: String, x: Float, y: Float, size: Float) {
        paint.style = Paint.Style.FILL
        paint.color = if (darkTheme && control != Control.FACE) Color.rgb(232, 235, 237) else SingleScreenSkin.INK
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        canvas.drawText(label, x, y - (paint.ascent() + paint.descent()) / 2, paint)
    }
}
