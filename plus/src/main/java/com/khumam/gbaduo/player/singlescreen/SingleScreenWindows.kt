package com.khumam.gbaduo.player.singlescreen

import android.app.Activity
import android.content.res.Configuration
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.khumam.gbaduo.player.PlayerActivity

/** API 30+ immersive handling for every SS screen, including library/settings and dialog return. */
internal object SingleScreenWindows {
    fun hideBars(activity: Activity) {
        activity.window.setDecorFitsSystemWindows(false)
        activity.window.attributes = activity.window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        activity.window.decorView.windowInsetsController?.apply {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsets.Type.systemBars())
        }
    }

    fun attach(activity: Activity): ViewTreeObserver.OnWindowFocusChangeListener {
        if (activity !is PlayerActivity) {
            activity.findViewById<android.view.View>(android.R.id.content).setOnApplyWindowInsetsListener { view, insets ->
                val top = if (view.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                    insets.displayCutout?.safeInsetTop ?: 0 else 0
                view.setPadding(0, top, 0, 0)
                if (insets.isVisible(WindowInsets.Type.systemBars())) view.post {
                    if (!activity.isDestroyed && activity.hasWindowFocus()) hideBars(activity)
                }
                insets
            }
        }
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) hideBars(activity) }
        activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        hideBars(activity)
        return listener
    }
}
