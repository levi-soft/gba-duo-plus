package com.khumam.gbaduo.achievements

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.khumam.gbaduo.R
import org.json.JSONObject

/** Presentation only: showing a popup never changes achievement state. */
class AchievementPopup(private val activity: Activity) {
    private val images = AchievementImages(java.io.File(activity.cacheDir, "ra-badges"))
    private val queue = ArrayDeque<JSONObject>()
    private var current: LinearLayout? = null
    private var closed = false
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()

    fun enqueue(event: JSONObject) {
        if (closed || activity.isDestroyed) return
        queue.addLast(event)
        if (current == null) next()
    }

    private fun next() {
        if (closed || queue.isEmpty()) return
        val event = queue.removeFirst()
        val host = activity.findViewById<FrameLayout>(android.R.id.content)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false
            setPadding(dp(12), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { setColor(Color.argb(179, 26, 32, 44)); cornerRadius = dp(10).toFloat(); setStroke(dp(1), Color.argb(153, 211, 181, 88)) }
            elevation = 0f
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val badge = ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Achievement badge" }
        card.addView(badge, LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(12) })
        images.load(badge, event.optString("badge"))
        val words = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val brand = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        brand.addView(ImageView(activity).apply { setImageResource(R.drawable.retroachievements_mark); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(22), dp(16)).apply { rightMargin = dp(6) })
        brand.addView(TextView(activity).apply { text = if (event.optString("type") == "unlock") "RetroAchievements · Achievement unlocked" else "RetroAchievements"; textSize = 12f; setTextColor(Color.rgb(226, 197, 112)) })
        words.addView(brand)
        words.addView(TextView(activity).apply { text = event.optString("title"); textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        words.addView(TextView(activity).apply { text = if (event.has("subtitle")) event.optString("subtitle") else "${event.optInt("points")} points"; textSize = 12f; setTextColor(Color.LTGRAY); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        card.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        val width = minOf(dp(420), activity.resources.displayMetrics.widthPixels - dp(32))
        host.addView(card, FrameLayout.LayoutParams(width, dp(96), Gravity.BOTTOM or Gravity.RIGHT).apply { bottomMargin = dp(16); rightMargin = dp(16) })
        current = card
        // Keep geometry stable over GLSurfaceView: translated overlays can be
        // clipped by the SurfaceView's transparent region on some Android GPUs.
        card.alpha = 0f
        card.animate().alpha(1f).setDuration(220).start()
        card.postDelayed({ if (!closed) card.animate().alpha(0f).setDuration(220).withEndAction { host.removeView(card); current = null; next() }.start() }, if (event.optString("type") == "unlock") 4500 else 2800)
    }

    fun close() { closed = true; queue.clear(); current?.animate()?.cancel(); current?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }; current = null; images.close() }
}
