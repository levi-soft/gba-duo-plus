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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PlusUpgradeTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        for (name in listOf("plus_upgrades", "player", "controls", "shader", "color"))
            context.getSharedPreferences(name, 0).edit().clear().commit()
    }
    @Test fun freshInstallUsesUpstreamLabelsAndDefaults() {
        PlusUpgrade.migrate(context)
        assertEquals(ControlScheme.DEFAULT, ControlPrefs.load(context))
        assertEquals(ShaderPreset.OFF, ShaderSettings.default(context))
        assertEquals(ColorCorrection.OFF, ColorSettings.default(context))
    }
    @Test fun existingPlusKeepsOldPhysicalMappingAndGbaColorsWithoutTouchingSaves() {
        val old = context.getSharedPreferences("player", 0)
        old.edit().putBoolean("swap_ab", false).putString("shader_id", "gba_color").commit()
        val save = File(context.filesDir, "games/gba/Fixture-00000001/game.sav")
        save.parentFile!!.mkdirs(); save.writeBytes(byteArrayOf(9, 2, 7))
        PlusUpgrade.migrate(context)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, ControlPrefs.load(context).buttons[GbaKeys.A])
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, ControlPrefs.load(context).buttons[GbaKeys.B])
        assertEquals(ShaderPreset.OFF, ShaderSettings.default(context))
        assertEquals(ColorCorrection.GBA, ColorSettings.default(context))
        assertArrayEquals(byteArrayOf(9, 2, 7), save.readBytes())
        assertEquals("gba_color", old.getString("shader_id", null))
        ShaderSettings.setDefault(context, ShaderPreset.XBRZ)
        PlusUpgrade.migrate(context)
        assertEquals(ShaderPreset.XBRZ, ShaderSettings.default(context))
    }
    @Test fun swappedMappingAndPlusShaderSurvive() {
        context.getSharedPreferences("player", 0).edit().putBoolean("swap_ab", true).putString("shader_id", "scalefx").commit()
        PlusUpgrade.migrate(context)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, ControlPrefs.load(context).buttons[GbaKeys.A])
        assertEquals(ShaderPreset.SCALEFX, ShaderSettings.default(context))
    }
    @Test fun existingNewChoicesWinOverLegacyPreferences() {
        context.getSharedPreferences("player", 0).edit().putString("shader_id", "gba_color").commit()
        ControlPrefs.save(context, ControlScheme.DEFAULT)
        ShaderSettings.setDefault(context, ShaderPreset.HQ2X)
        ColorSettings.setDefault(context, ColorCorrection.GBC)
        PlusUpgrade.migrate(context)
        assertEquals(ControlScheme.DEFAULT, ControlPrefs.load(context))
        assertEquals(ShaderPreset.HQ2X, ShaderSettings.default(context))
        assertEquals(ColorCorrection.GBC, ColorSettings.default(context))
    }
}
