package com.khumam.gbaduo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewTreeObserver
import com.khumam.gbaduo.player.singlescreen.SingleScreenWindows

/** The default edition uses the normal Application lifecycle; SS opts into immersive phone windows. */
class PlusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PlusUpgrade.migrate(this)
        if (!BuildConfig.SINGLE_SCREEN) return
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private val listeners = HashMap<Activity, ViewTreeObserver.OnWindowFocusChangeListener>()
            override fun onActivityPostCreated(activity: Activity, state: Bundle?) {
                listeners[activity] = SingleScreenWindows.attach(activity)
            }
            override fun onActivityPostResumed(activity: Activity) = SingleScreenWindows.hideBars(activity)
            override fun onActivityDestroyed(activity: Activity) {
                listeners.remove(activity)?.let { activity.window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(it) }
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        })
    }
}
