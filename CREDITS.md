# Credits and thanks

## GBA Duo: the project Plus is built on

Our first and most important thanks go to **[khumam](https://github.com/khumam)** and the contributors to **[GBA Duo](https://github.com/khumam/gba-duo)**. They created the Android application, dual-screen companion concept, native pack format, Lua integration, CSS/layout engine, controller handling, library and much of the workflow that Plus extends.

Plus is a derivative community fork, not an independently invented replacement. The public history is based on upstream tag **v0.1.6**, commit **0c201e9faa224eb1527651ac0ca3e12a3ce0fe5c**. The current 0.1.7 upgrade also merges official tag v0.1.7, commit 6aa3823b7c4e4c8478b74fe24cf837a19224fc23, including the new library, Settings, graphics/CSS APIs, sandbox limits and save synchronization. Original commits and source notices are retained. The inherited `art/banner.png`, original application artwork and format examples come from the upstream project; they are not presented as new Plus artwork.

Plus development is maintained by **[levi-soft](https://github.com/levi-soft)**. Its contributions include the Android RetroAchievements integration/presentation, integration of additional shader pipelines, persistent Swap A/B, companion navigation and rendering changes, opt-in Studio connectivity, and the fork's packaging/documentation. The shader algorithms and achievement rules themselves are not claimed as Plus inventions.

## Emulation

**[mGBA](https://mgba.io/)** by **Vicki Pfau (endrift) and the mGBA contributors** supplies the emulation core. Source: [mgba-emu/mgba](https://github.com/mgba-emu/mgba), pinned to `26b7884bc25a5933960f3cdcd98bac1ae14d42e2` (`0.10.5`). The core and its existing filter adaptations retain their **MPL-2.0** terms and original file notices. Thank you for making accurate, portable emulation available to projects like this one.

## Scripting

**[Lua](https://www.lua.org/)** is designed and maintained by the Lua team at **Lua.org / PUC-Rio**. Vendored version: **5.4.7**. Its MIT permission and copyright notice are retained in `engine/lua/src/lua.h` and its documentation. The bundled version's notice reads **Copyright (C) 1994-2024 Lua.org, PUC-Rio**.

## RetroAchievements

- **[RetroAchievements](https://retroachievements.org/)** and its community provide the service, achievement definitions, games database, badges and account infrastructure. Achievement-set authors retain credit for their individual work.
- **[rcheevos](https://github.com/RetroAchievements/rcheevos)** provides the client/runtime/hash implementation under MIT. Pinned source: `1433173220a7eaede6a9ed7a18e94117be1821e0` (`v12.5.0`); its notice credits RetroAchievements.org.
- The bundled RetroAchievements mark comes from **[RAWeb](https://github.com/RetroAchievements/RAWeb)**, `public/assets/images/favicon.webp`. The RAWeb repository carries GPL v3; the name and logo remain RetroAchievements branding. Their presence identifies the integration and does not imply endorsement.
- RetroArch's [achievement popup implementation](https://github.com/libretro/RetroArch/blob/master/gfx/widgets/gfx_widget_achievement_popup.c) was consulted as a **behavior/design reference** for queued notifications. Its implementation was not copied into the Android popup code.

## Additional shaders

Source collection: **[Libretro GLSL shaders](https://github.com/libretro/glsl-shaders)**, revision `ce9a41242e27f41e16bc35c28943466cee3e8ff8`.

| Bundled implementation | Authors and acknowledgements | License retained |
|---|---|---|
| xBRZ / Freescale-derived shader | **Hyllian** for xBR vertex/texel mapping; **Zenju** for xBRZ code/concepts, as described by the source header | Hyllian MIT notice plus Zenju GPL-3.0 notice and its original exception text |
| HQ2x | **Lior Halphon**; RetroArch shader-format port credited to **hunterk**; source also references pkh's hqx article | MIT |
| ScaleFX passes 0-4 | **Sp00kyFox** | MIT |

Plus adapts loading and execution to Android GLES3, including shader-stage selection, pipeline targets and output orientation. The original algorithm bodies/notices are retained. Source filenames and the UI's shorter display names do not change authorship.

## Android and development platform

Thanks to the Android, AndroidX, Jetpack Compose, Material Components, Kotlin, Gradle, CMake and LLVM/NDK projects and contributors. Runtime AndroidX/Compose/Material/Kotlin components retain Apache-2.0 licensing; tests also use JUnit, Robolectric and org.json under their own terms. Dependencies are declared in the Gradle build files. SDK/build tools and test frameworks are not claimed as Plus code.

## Users and contributors

Thank you to the dual-screen handheld community and testers for reporting input, rendering, launcher and companion issues. Please preserve these acknowledgements when redistributing this fork, and credit any additional assets or algorithms you introduce.

Project names, logos, game titles and device names are used for attribution or compatibility descriptions. Their owners retain their rights; no upstream project or hardware vendor is represented as endorsing Plus.

## Fonts inherited in 0.1.7

**IBM Plex Mono** by IBM and contributors and **Silkscreen** by the Silkscreen Project Authors are used by the new upstream pixel UI. Their SIL Open Font License texts are retained under `app/src/main/assets/licenses/`; their font files remain under `app/src/main/res/font/`.
