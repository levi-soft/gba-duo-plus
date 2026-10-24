# Third-party notices

GBA Duo Plus is a modified distribution of **GBA Duo**, by khumam and contributors, under [GNU GPL v3](LICENSE). It retains the original project history and notices. Plus changes do not replace the copyrights or licenses below.

| Component/material | Location | Origin/license |
|---|---|---|
| Original GBA Duo application, companion engine, artwork/examples | Inherited source, `art/`, `docs-packs/` | [khumam/gba-duo](https://github.com/khumam/gba-duo), GPL v3; original notices retained |
| mGBA core | `engine/mgba` submodule | Vicki Pfau/endrift and contributors; [MPL-2.0](https://github.com/mgba-emu/mgba/blob/26b7884bc25a5933960f3cdcd98bac1ae14d42e2/LICENSE) and bundled file-specific notices |
| Lua 5.4.7 | `engine/lua/` | Lua.org, PUC-Rio; MIT text in `engine/lua/src/lua.h` and documentation |
| rcheevos | Vendored `engine/rcheevos` | RetroAchievements.org/contributors; [MIT](https://github.com/RetroAchievements/rcheevos/blob/1433173220a7eaede6a9ed7a18e94117be1821e0/LICENSE) |
| RetroAchievements mark | `plus/src/main/res/drawable-nodpi/retroachievements_mark.webp` | [RAWeb original asset](https://github.com/RetroAchievements/RAWeb/blob/master/public/assets/images/favicon.webp); [RAWeb GPL v3](https://github.com/RetroAchievements/RAWeb/blob/master/LICENSE); branding rights retained by its owner |
| Original simple filter adaptations | `app/src/main/assets/shaders/` | Inherited GBA Duo/mGBA work, with original notices retained |
| xBRZ shader | `plus/src/main/assets/shaders/libretro/xbrz.glsl` | Hyllian MIT vertex/mapping notice; Zenju GPL-3.0 xBRZ notice and original exception text |
| HQ2x shader | `plus/src/main/assets/shaders/libretro/hq2x.glsl` | Lior Halphon MIT; port credit to hunterk |
| ScaleFX passes | `plus/src/main/assets/shaders/libretro/scalefx_shaders_scalefx-pass*.glsl` | Sp00kyFox, MIT |
| IBM Plex Mono / Silkscreen fonts | `app/src/main/res/font/`, `app/src/main/assets/licenses/` | IBM / Silkscreen Project Authors; SIL Open Font License, inherited from upstream 0.1.7 |
| AndroidX, Jetpack Compose, Material Components, Kotlin runtime | Gradle dependency declarations | Apache-2.0, with each dependency's original metadata/notices |

The Libretro shader revision and individual upstream paths are recorded in [CREDITS](CREDITS.md) and [SHADERS](docs/SHADERS.md). Full shader permission/copyright text remains in the distributed source files. mGBA and rcheevos licenses are available after the documented recursive checkout; mGBA is a pinned Git submodule, and rcheevos 12.5.0 is vendored from official GBA Duo 0.1.7.

The vendored Lua copyright notice is **Copyright (C) 1994-2024 Lua.org, PUC-Rio**. Its permission and disclaimer must remain with redistributions. Additional third-party code within native submodules retains its own notices; do not strip those directories' license files when redistributing corresponding source.

Test/build dependencies (including JUnit, Robolectric, org.json, Gradle, CMake and Android SDK/NDK tools) retain their respective licenses. They are resolved through the build and are not relicensed as original Plus code. No ROM, BIOS dump, private companion release or private account data is supplied by this project.

If distributing an APK, provide the corresponding source for the actual build, including fork changes and native dependency revisions, and preserve applicable notices. The Plus application license does not grant trademark ownership or permission to redistribute unrelated game content. See the original license texts for the governing terms.
