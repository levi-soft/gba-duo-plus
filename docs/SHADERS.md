# Shader pipelines

Available presets: **Off, GBA Color, LCD Grid, Scanlines, xBRZ, HQ2x, ScaleFX**. Scale2x and the experimental Soft variants are not exposed in the Plus picker. Shader selection is stored in player preferences; persistence itself was already supported by official v0.1.6.

The original simple presets descend from GBA Duo/mGBA. New algorithms are from [Libretro's GLSL collection](https://github.com/libretro/glsl-shaders/tree/ce9a41242e27f41e16bc35c28943466cee3e8ff8), with their original authors' notices retained:

| UI preset | Upstream source | Execution |
|---|---|---|
| xBRZ | `xbrz/shaders/xbrz-freescale.glsl` | Freescale-derived implementation rendered at aspect-fit output size; the shorter UI name does not indicate a different algorithm |
| HQ2x | `hqx/shader-files/hq2x-halphon.glsl` | 2x intermediate render followed by nearest output scaling |
| ScaleFX | `scalefx/shaders/scalefx-pass0.glsl` through `scalefx-pass4.glsl` | Five-pass pipeline, floating-point metric targets, metadata targets and 3x final output |

Plus adapts GLSL stage/version handling to GLES3, removes UI-only pragmas, selects the VERTEX/FRAGMENT branches and sets the original sampler bindings. Dithering is disabled for metadata passes; the final output orientation is corrected once.

ScaleFX requires renderable floating-point targets. Unsupported/failed pipelines log an error and fall back to Off. Visual quality and speed depend on the game, output size and GPU; shaders can increase power consumption and heat. Historical Thor checks covered compilation, orientation, colors and selected scenes, not sustained performance across every title.

For algorithm authors and license details, see [CREDITS](../CREDITS.md) and the headers under `plus/src/main/assets/shaders/libretro/`.
