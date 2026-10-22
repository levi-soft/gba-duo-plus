package com.khumam.gbaduo.emu

import org.junit.Assert.assertEquals
import org.junit.Test

class ShaderPresetTest {

    @Test
    fun byIdFindsMatchingPreset() {
        assertEquals(ShaderPreset.GBA_COLOR, ShaderPreset.byId("gba_color"))
        assertEquals(ShaderPreset.XBRZ, ShaderPreset.byId("xbrz"))
    }

    @Test
    fun byIdFallsBackToOffForUnknownOrNull() {
        assertEquals(ShaderPreset.OFF, ShaderPreset.byId("nonsense"))
        assertEquals(ShaderPreset.OFF, ShaderPreset.byId(null))
    }

    @Test
    fun removedPresetsRestoreSupportedSelection() {
        assertEquals(ShaderPreset.GBA_COLOR, ShaderPreset.byId("gba_color_soft"))
        assertEquals(ShaderPreset.LCD, ShaderPreset.byId("lcd_soft"))
        assertEquals(ShaderPreset.OFF, ShaderPreset.byId("scale2x"))
    }

    @Test
    fun nextCyclesThroughAllPresetsAndWrapsAround() {
        var preset = ShaderPreset.OFF
        val seen = mutableListOf(preset)
        repeat(ShaderPreset.entries.size - 1) {
            preset = ShaderPreset.next(preset)
            seen += preset
        }
        assertEquals(ShaderPreset.entries.toList(), seen)
        assertEquals(ShaderPreset.OFF, ShaderPreset.next(preset))
    }
}
