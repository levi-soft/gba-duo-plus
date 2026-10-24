package com.khumam.gbaduo.artwork

import android.view.ViewGroup
import android.widget.LinearLayout
import com.khumam.gbaduo.library.ui.Pixel
import com.khumam.gbaduo.library.ui.PixelButton

/** The added action belongs to Plus; the original library layout stays reusable. */
object ArtworkLibraryControls {
    fun attach(actions: LinearLayout, open: () -> Unit) {
        val context = actions.context
        val button = PixelButton(context).apply {
            text = "Artwork"
            contentDescription = "Artwork (X)"
            style(Pixel.CREAM)
            setOnClickListener { open() }
        }
        actions.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = Pixel.dp(context, 16f).toInt()
        })
        if (context.resources.configuration.screenWidthDp < 700) {
            // Three usable actions also fit narrow secondary displays.
            val gap = Pixel.dp(context, 8f).toInt()
            actions.setPadding(gap, actions.paddingTop, gap, actions.paddingBottom)
            for (index in 0 until actions.childCount) {
                val child = actions.getChildAt(index) as? PixelButton ?: continue
                child.minWidth = 0
                child.minimumWidth = 0
                child.maxLines = 2
                child.textSize = if (context.resources.configuration.screenWidthDp < 480) 12f else 14f
                child.minHeight = Pixel.dp(context, 52f).toInt()
                child.setPadding(gap, 0, gap + child.box.shadow.toInt(), child.box.shadow.toInt())
                child.layoutParams = LinearLayout.LayoutParams(0, Pixel.dp(context, 56f).toInt(), 1f).apply {
                    marginStart = if (index == 0) 0 else gap
                }
            }
        }
    }
}
