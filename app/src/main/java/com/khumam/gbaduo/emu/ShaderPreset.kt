package com.khumam.gbaduo.emu

/**
 * A single-pass GLES2 shader preset loaded from `assets/<vertexAsset>` / `assets/<fragmentAsset>`.
 * Adapted from mGBA's own shader presets (third_party/mgba/res/shaders, MPL-2.0).
 * [uniforms] are applied once after linking; no runtime-configurable sliders in v1.
 */
enum class ShaderPreset(
    val id: String,
    val displayName: String,
    val vertexAsset: String,
    val fragmentAsset: String,
    val uniforms: List<Pair<String, Float>>,
) {
    OFF(
        id = "off",
        displayName = "Off",
        vertexAsset = "shaders/common_vertex.glsl",
        fragmentAsset = "shaders/off/fragment.glsl",
        uniforms = emptyList(),
    ),
    GBA_COLOR(
        id = "gba_color",
        displayName = "GBA Color",
        vertexAsset = "shaders/common_vertex.glsl",
        fragmentAsset = "shaders/gba_color/fragment.glsl",
        uniforms = listOf("darken_screen" to 0.5f),
    ),
    LCD(
        id = "lcd",
        displayName = "LCD Grid",
        vertexAsset = "shaders/common_vertex.glsl",
        fragmentAsset = "shaders/lcd/fragment.glsl",
        uniforms = listOf("boundBrightness" to 0.9f),
    ),
    SCANLINES(
        id = "scanlines",
        displayName = "Scanlines",
        vertexAsset = "shaders/common_vertex.glsl",
        fragmentAsset = "shaders/scanlines/fragment.glsl",
        uniforms = listOf("lineBrightness" to 0.5f),
    ),
    XBRZ("xbrz", "xBRZ", "", "", emptyList()),
    HQ2X("hq2x", "HQ2x", "", "", emptyList()),
    SCALEFX("scalefx", "ScaleFX", "", "", emptyList()),
    ;

    val usesPipeline: Boolean get() = this == XBRZ || this == HQ2X || this == SCALEFX

    companion object {
        fun byId(id: String?): ShaderPreset = when (id) {
            "gba_color_soft" -> GBA_COLOR
            "lcd_soft" -> LCD
            else -> entries.firstOrNull { it.id == id } ?: OFF
        }

        /** Cycles to the next preset, wrapping back to the first after the last. */
        fun next(current: ShaderPreset): ShaderPreset {
            val all = entries
            return all[(all.indexOf(current) + 1) % all.size]
        }
    }
}
