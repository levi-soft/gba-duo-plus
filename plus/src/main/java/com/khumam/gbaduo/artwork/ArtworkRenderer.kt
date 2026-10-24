package com.khumam.gbaduo.artwork

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.khumam.gbaduo.library.ui.Pixel
import kotlin.math.max
import kotlin.math.min

/** A square cover card using the original library palette and hard shadow. No file or network I/O. */
object ArtworkRenderer {
    fun squareBounds(bounds: RectF, padding: Float): RectF {
        val side = max(0f, min(bounds.width(), bounds.height()) - max(0f, padding) * 2f)
        return RectF(bounds.centerX() - side / 2f, bounds.centerY() - side / 2f, bounds.centerX() + side / 2f, bounds.centerY() + side / 2f)
    }

    fun fitRect(bounds: RectF, imageWidth: Int, imageHeight: Int): RectF {
        require(imageWidth > 0 && imageHeight > 0)
        val scale = min(bounds.width() / imageWidth, bounds.height() / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        return RectF(bounds.centerX() - width / 2f, bounds.centerY() - height / 2f, bounds.centerX() + width / 2f, bounds.centerY() + height / 2f)
    }

    fun draw(canvas: Canvas, bounds: RectF, bitmap: Bitmap, selected: Boolean, density: Float): Boolean {
        if (bitmap.isRecycled || bounds.isEmpty) return false
        val card = squareBounds(bounds, 14f * density)
        if (card.isEmpty) return false
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val shadow = if (selected) 8f * density else 5f * density
        paint.color = Pixel.INK
        canvas.drawRect(RectF(card).apply { offset(shadow, shadow) }, paint)
        if (selected) {
            paint.color = Pixel.YELLOW
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 10f * density
            canvas.drawRect(card, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Pixel.CREAM
        canvas.drawRect(card, paint)
        val inner = RectF(card).apply { inset(3f * density, 3f * density) }
        canvas.drawBitmap(bitmap, null, fitRect(inner, bitmap.width, bitmap.height), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * density
        paint.color = Pixel.INK
        canvas.drawRect(card, paint)
        return true
    }
}
