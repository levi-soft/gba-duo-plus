package com.khumam.gbaduo.player.singlescreen

import android.app.Activity
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.khumam.gbaduo.R
import com.khumam.gbaduo.library.ui.OutlineText
import com.khumam.gbaduo.library.ui.PixelButton

/** SS-only library sizing. A short landscape window gives its middle to the cover. */
internal object SingleScreenLibraryChrome {
    fun attach(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(R.id.root)
        val title = activity.findViewById<OutlineText>(R.id.wordmark)
        val settings = activity.findViewById<View>(R.id.open_settings)
        val hud = activity.findViewById<TextView>(R.id.hud)
        val header = title.parent as LinearLayout
        val actions = activity.findViewById<LinearLayout>(R.id.actions)
        val info = activity.findViewById<LinearLayout>(R.id.info)
        val fileName = activity.findViewById<TextView>(R.id.file_name)
        val metadata = fileName.parent as LinearLayout
        val content = actions.parent as LinearLayout
        val captionSpace = View(activity)
        content.addView(captionSpace, content.indexOfChild(actions), LinearLayout.LayoutParams(-1, 0))
        val carousel = activity.findViewById<View>(R.id.carousel)
        val gameTitle = activity.findViewById<OutlineText>(R.id.game_title)
        var lastWidth = -1
        var lastHeight = -1
        var lastDensity = -1f
        var lastShelfVisible: Boolean? = null

        fun configure(width: Int, height: Int) {
            val density = activity.resources.displayMetrics.density
            val shelfVisible = info.visibility == View.VISIBLE && actions.visibility == View.VISIBLE
            if (width <= 0 || height <= 0 || (width == lastWidth && height == lastHeight && density == lastDensity && shelfVisible == lastShelfVisible)) return
            lastWidth = width; lastHeight = height; lastDensity = density; lastShelfVisible = shelfVisible
            fun dp(value: Float) = (value * density).toInt()
            val narrow = width / density < 600
            val compactLandscape = width >= height && height / density < 600
            title.minTextSizePx = 10 * activity.resources.displayMetrics.scaledDensity
            title.setTextSizeSp(if (narrow) 22f else 30f)
            hud.setSingleLine(true)
            hud.ellipsize = TextUtils.TruncateAt.END

            // Keep the filename readable without squeezing the platform/code/pack tags.
            if (narrow) {
                if (fileName.parent !== info) {
                    (fileName.parent as? ViewGroup)?.removeView(fileName)
                    info.addView(fileName, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f) })
                }
                fileName.gravity = Gravity.CENTER
                fileName.maxWidth = (width - dp(32f)).coerceAtLeast(1)
            } else {
                if (fileName.parent !== metadata) {
                    (fileName.parent as? ViewGroup)?.removeView(fileName)
                    metadata.addView(fileName, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(14f) })
                }
                fileName.gravity = Gravity.START
                fileName.maxWidth = dp(320f)
            }
            captionSpace.layoutParams = captionSpace.layoutParams.apply {
                this.height = if (narrow && height / density >= 700 && shelfVisible) dp(24f) else 0
            }

            // These views may belong to the old header row after a window resize.
            for (view in listOf(title, hud, settings)) (view.parent as? ViewGroup)?.removeView(view)
            header.removeAllViews()
            header.orientation = LinearLayout.VERTICAL
            header.gravity = Gravity.START
            header.setPadding(dp(12f), dp(8f), dp(12f), dp(4f))
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (narrow) {
                row.addView(hud, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8f) })
                row.addView(settings, LinearLayout.LayoutParams(-2, -2))
                header.addView(row, LinearLayout.LayoutParams(-1, -2))
                header.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })
            } else {
                row.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8f) })
                val hudWidth = minOf(dp(280f), ((width - dp(24f)) * .34f).toInt())
                row.addView(hud, LinearLayout.LayoutParams(hudWidth, -2).apply { marginEnd = dp(12f) })
                row.addView(settings, LinearLayout.LayoutParams(-2, -2))
                header.addView(row, LinearLayout.LayoutParams(-1, -2))
            }

            carousel.setPadding(carousel.paddingLeft, dp(4f), carousel.paddingRight, dp(2f))
            gameTitle.setTextSizeSp(if (compactLandscape || width / density < 700) 20f else 26f)
            (actions.layoutParams as LinearLayout.LayoutParams).apply {
                topMargin = dp(if (compactLandscape) 6f else 12f)
                bottomMargin = dp(if (compactLandscape) 6f else 12f)
            }.also { actions.layoutParams = it }
            val narrowActions = width / density < 700
            val actionGap = dp(if (narrowActions) 8f else 12f)
            actions.setPadding(actionGap, 0, actionGap, 0)
            for (index in 0 until actions.childCount) {
                val child = actions.getChildAt(index) as? PixelButton ?: continue
                child.minWidth = if (!narrowActions && child.id == R.id.play) dp(160f) else 0
                child.minimumWidth = 0
                child.maxLines = if (narrowActions) 2 else 1
                child.textSize = if (width / density < 480) 12f else 14f
                val buttonHeight = dp(if (compactLandscape) 44f else if (narrowActions) 56f else 48f)
                child.minHeight = buttonHeight
                child.setPadding(actionGap, 0, actionGap + child.box.shadow.toInt(), child.box.shadow.toInt())
                child.layoutParams = LinearLayout.LayoutParams(if (narrowActions) 0 else -2, buttonHeight,
                    if (narrowActions) 1f else 0f).apply { marginStart = if (index == 0) 0 else actionGap }
            }
            activity.findViewById<View>(R.id.hints_bar).let {
                it.layoutParams = it.layoutParams.apply { this.height = dp(if (compactLandscape) 28f else 34f) }
            }
            activity.findViewById<View>(R.id.empty).let {
                it.layoutParams = it.layoutParams.apply { this.width = minOf(dp(560f), width - dp(48f)).coerceAtLeast(1) }
            }
        }

        val metrics = activity.resources.displayMetrics
        configure(metrics.widthPixels, metrics.heightPixels)
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> configure(r - l, b - t) }
        root.viewTreeObserver.addOnGlobalLayoutListener { configure(root.width, root.height) }
    }
}
