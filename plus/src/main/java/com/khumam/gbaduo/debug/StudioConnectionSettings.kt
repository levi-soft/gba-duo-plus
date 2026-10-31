package com.khumam.gbaduo.debug

import android.content.Context
import com.khumam.gbaduo.emu.EmuThread

/** Saved in this edition's private app preferences; release and debug both default to Off. */
object StudioConnectionSettings {
    private const val PREFS = "studio_connection"
    private const val ENABLED = "enabled"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    /** Capture the saved choice before this new game session starts. */
    fun applyTo(context: Context, session: EmuThread) {
        session.studioConnectionEnabled = enabled(context)
    }
}
