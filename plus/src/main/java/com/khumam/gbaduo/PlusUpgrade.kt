package com.khumam.gbaduo

import android.content.Context
import android.view.KeyEvent
import com.khumam.gbaduo.controls.ControlPrefs
import com.khumam.gbaduo.controls.ControlScheme
import com.khumam.gbaduo.emu.ColorCorrection
import com.khumam.gbaduo.emu.ColorSettings
import com.khumam.gbaduo.emu.GbaKeys
import com.khumam.gbaduo.emu.ShaderPreset
import com.khumam.gbaduo.emu.ShaderSettings
import java.io.File

/** One-time, non-destructive migration; the old preferences remain available for rollback. */
object PlusUpgrade {
    fun migrate(context: Context) {
        val state = context.getSharedPreferences("plus_upgrades", Context.MODE_PRIVATE)
        if (state.getBoolean("upstream_018", false)) return
        val old = context.getSharedPreferences("player", Context.MODE_PRIVATE)
        val controls = context.getSharedPreferences("controls", Context.MODE_PRIVATE)
        val existingInstall = old.contains("swap_ab") || old.contains("shader_id") || File(context.filesDir, "games").isDirectory
        if (existingInstall && !controls.contains("scheme")) {
            // Retired quick-menu setting: read once into upstream's controller mapping.
            // A saved controller scheme always wins; the old flag is never an active setting.
            val swap = old.getBoolean("swap_ab", false)
            ControlPrefs.save(context, ControlScheme.DEFAULT.copy(buttons = ControlScheme.DEFAULT.buttons + mapOf(
                GbaKeys.A to if (swap) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B,
                GbaKeys.B to if (swap) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A)))
        }
        if (old.contains("shader_id") && !context.getSharedPreferences("shader", Context.MODE_PRIVATE).contains("default")) {
            when (val id = old.getString("shader_id", null)) {
                "gba_color", "gba_color_soft" -> {
                    ShaderSettings.setDefault(context, ShaderPreset.OFF)
                    if (!context.getSharedPreferences("color", Context.MODE_PRIVATE).contains("default"))
                        ColorSettings.setDefault(context, ColorCorrection.GBA)
                }
                else -> ShaderSettings.setDefault(context, ShaderPreset.byId(id))
            }
        }
        state.edit().putBoolean("upstream_018", true).apply()
    }
}
