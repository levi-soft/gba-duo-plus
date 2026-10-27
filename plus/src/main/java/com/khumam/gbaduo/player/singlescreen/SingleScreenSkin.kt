package com.khumam.gbaduo.player.singlescreen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/** A lightweight painted shell: no bitmap skin, extra Surface or per-frame texture upload. */
internal class SingleScreenSkin(private val density: Float) {
    var darkTheme = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    fun draw(canvas: Canvas, layout: SingleScreenLayout.Arrangement) {
        canvas.drawColor(if (darkTheme) Color.rgb(25, 28, 31) else SHELL)
        frame(canvas, layout.game)
        if (!layout.gameOnly) frame(canvas, layout.companion)

        val logoSize = min(12f * density, min(layout.logo.width * .18f, layout.logo.height * .34f))
        val logoY = layout.logo.y + logoSize * 1.6f
        val logoX = layout.logo.x + layout.logo.width / 2
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = logoSize
        paint.color = if (darkTheme) Color.rgb(229, 232, 234) else INK
        canvas.drawText("GBA DUO", logoX, logoY, paint)
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.textSize = logoSize * .60f
        paint.color = if (darkTheme) Color.rgb(177, 185, 191) else Color.rgb(100, 104, 107)
        canvas.drawText("P L U S   S S", logoX, logoY + logoSize * .85f, paint)
        val swatch = logoSize * .50f
        val paletteY = logoY - logoSize * 1.55f
        for ((index, color) in listOf(BLUE, RED, GREEN, YELLOW).withIndex()) {
            paint.color = color
            val x = logoX + (index - 1.5f) * swatch * 1.35f
            canvas.drawRoundRect(x - swatch / 2, paletteY, x + swatch / 2, paletteY + swatch * .30f,
                swatch * .15f, swatch * .15f, paint)
        }
    }

    private fun frame(canvas: Canvas, rect: SingleScreenLayout.Rect) {
        val bevel = min(5f * density, min(rect.width, rect.height) * .022f)
        box.set(rect.x - bevel, rect.y - bevel, rect.right + bevel, rect.bottom + bevel)
        paint.style = Paint.Style.FILL
        paint.color = if (darkTheme) Color.rgb(10, 12, 14) else Color.rgb(164, 168, 169)
        canvas.drawRoundRect(box, bevel * 1.7f, bevel * 1.7f, paint)
        box.inset(bevel * .20f, bevel * .20f)
        paint.color = if (darkTheme) Color.rgb(96, 102, 108) else Color.WHITE
        canvas.drawRoundRect(box, bevel * 1.5f, bevel * 1.5f, paint)
        box.inset(bevel * .28f, bevel * .28f)
        paint.color = if (darkTheme) Color.rgb(47, 52, 57) else Color.rgb(111, 117, 120)
        canvas.drawRoundRect(box, bevel, bevel, paint)
        box.inset(bevel * .30f, bevel * .30f)
        paint.color = Color.rgb(27, 32, 34)
        canvas.drawRoundRect(box, bevel * .6f, bevel * .6f, paint)
    }

    companion object {
        val SHELL = Color.rgb(240, 240, 235)
        val INK = Color.rgb(49, 53, 56)
        val RED = Color.rgb(237, 72, 82)
        val YELLOW = Color.rgb(255, 213, 65)
        val BLUE = Color.rgb(57, 135, 223)
        val GREEN = Color.rgb(69, 182, 119)
    }
}
