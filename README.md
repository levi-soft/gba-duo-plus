# GBA Duo Plus

**A community fork of [GBA Duo](https://github.com/khumam/gba-duo), created by [khumam](https://github.com/khumam).**

GBA Duo made the central idea possible: play Game Boy Advance, Game Boy Color and Game Boy games on one screen of an Android handheld, with an interactive companion on the other. Plus builds on that work rather than replacing its authorship. Thank you to khumam and every upstream contributor for publishing the original application and companion system.

Maintained by [levi-soft](https://github.com/levi-soft), Plus adds automatic companion creation, selectable library covers, additional pixel-art shaders, persistent control preferences, companion navigation for supported games and an optional GBA Duo Studio connection. It extends the official RetroAchievements client from GBA Duo 0.1.7.

**Current source version:** `0.1.7-plus.2` · **Upstream base:** `v0.1.7` · **License:** [GPL v3](LICENSE)

[Tiếng Việt](README.vi.md) · [Features](#features) · [Comparison](docs/FEATURES.md) · [Build](docs/BUILDING.md) · [Launchers](docs/LAUNCHERS.md) · [Studio](docs/STUDIO-CONNECTION.md) · [Credits](CREDITS.md)

## Project identity

| | GBA Duo Plus |
|---|---|
| Source | [levi-soft/gba-duo-plus](https://github.com/levi-soft/gba-duo-plus) |
| Original project | [khumam/gba-duo](https://github.com/khumam/gba-duo) |
| Exact upstream baseline | [`6aa3823b7c4e4c8478b74fe24cf837a19224fc23`](https://github.com/khumam/gba-duo/commit/6aa3823b7c4e4c8478b74fe24cf837a19224fc23), tag `v0.1.7` |
| Release application ID | `com.khumam.gbaduo.plus` |
| Debug application ID | `com.khumam.gbaduo.plus.debug` |
| Requirements | Android 11/API 30 or later; ARM64; a second Android display for the companion |
| Primary development device | AYN Thor; other dual-screen devices require testing |

Plus is **not an official GBA Duo release** and is not endorsed by its original author, RetroAchievements, Libretro or the other credited projects. It installs separately from the official application. Separate package IDs mean separate app-private saves, settings, login sessions and folder permissions; there is no automatic cross-edition migration.

## Features

### Automatic companions (Plus only)

The library's Companion menu includes **Find and create companion**. Plus downloads a compatible builder profile from the [Companion Builder website](https://levi-soft.github.io/gba-duo-companion-builder/), creates the companion from the player's ROM on the device, and installs and assigns it after successful validation. The ROM is never uploaded. Verified builder downloads are cached for later offline use.

Manual ZIP import remains available. A generated companion receives a separate pack ID; previously installed/custom companions remain available, and cancelling or failing a build preserves the current assignment. Only exact ROM identities listed in the catalogue are supported.

This is a **Plus extension**, not a feature claimed for official GBA Duo. Its implementation lives in `plus/`, connected through a small library-menu hook and private Activity registration. If upstream adds an equivalent complete feature, this extension can be replaced by the official implementation. See [automatic companion architecture and removal steps](docs/AUTOMATIC-COMPANIONS.md).

### Inherited from GBA Duo

- mGBA-powered GBA, GBC and GB emulation, gamepad input and dual-screen placement.
- Game-specific companion ZIP packs using a manifest, JSON layout, CSS, assets and optional Lua.
- Companion pages, tabs, lists, buttons, live values and automatic page changes.
- ROM-folder management, settings and an external `VIEW` launch activity introduced upstream in `v0.1.6`.
- In-game saves, nine save-state slots, fast-forward and the original display filters.
- Saved shader selection. This was **already present upstream**; Plus extends the available shader set.

### Added or extended in Plus

| Area | Plus behavior |
|---|---|
| Library artwork | Select square covers from a SteamGridDB gallery, import a local PNG/JPEG/WebP, or use the default no-account Libretro source for lookup and missing-artwork scraping. Covers are stored locally, retain aspect ratio and keep the original cartridge drawing as fallback. [Setup and extension boundary](docs/ARTWORK.md). |
| RetroAchievements | Retains the official 0.1.7 [rcheevos](https://github.com/RetroAchievements/rcheevos) client and adds encrypted token storage, achievement-list UI and save-state progress sidecars. Account sign-in, ROM recognition and the underlying client come from the official integration. |
| Achievement presentation | Automatic sign-in/game notices, game image and unlocked/total count; bottom-right unlock popups with a 70% background opacity. Unsupported ROMs show a no-achievements notice. |
| Pixel-art shaders | **xBRZ, HQ2x and ScaleFX**, adapted from the Libretro GLSL shader collection with original notices retained. |
| Controls | **Swap A/B is remembered** between game sessions. Existing shader persistence is retained for the expanded preset set. |
| Companion interaction | Optional `input.focusUnit(id)` Lua action support for verified Fire Emblem cursor profiles. Navigation uses bounded D-pad input and respects game state and physical input. |
| Companion rendering | Additional sizing, raster-grid/aspect-fit and retained-snapshot behavior used by complex packs. This does not make every arbitrary pack/device combination automatically compatible. |
| Daijisho custom player | Add **GBA Duo Plus** as a player to launch ROMs directly. Retains upstream's `LaunchActivity`, with additional path aliases, input validation and session/lifecycle handling. See [player setup and launch arguments](docs/LAUNCHERS.md#add-a-gba-duo-plus-player-in-daijisho). |
| Studio Connection | Per-session opt-in, read-only local RAM/snapshot and bounded ROM/VRAM/palette/OAM inspection for compatible desktop tooling. Revision-2 capability discovery retains the existing wire envelope. No LAN listener or memory-write command. The upstream permission-gated pack tap API is separate from this read-only desktop bridge. |

RetroAchievements now uses the official 0.1.7 integration as its foundation. Plus adds encrypted token storage, achievement-list UI, save-state progress and the selected popup presentation to that single official client. The former Plus RA runtime has been removed.

Inherited from 0.1.7: pixel-style library and Settings, readable game titles, external save-folder synchronization, permission-gated RAM writes, ROM sprite/tilemap decoding, responsive CSS and bounded Lua execution.

See [the detailed baseline comparison](docs/FEATURES.md). Credit for algorithms, cores and APIs belongs to their original authors; Plus contributes their Android integration and the changes documented here.

### Deliberate boundaries

- **No ROM-hash override or identity spoofing.** RetroAchievements identifies the actual loaded ROM. A modified/translated ROM must be supported by RetroAchievements to use its achievement set normally.
- **No translation engine or translation credentials.** Google/Qwen/offline translation experiments are not part of Plus.
- **No Hardcore mode.** This edition retains companion and save-state workflows and runs achievement support in the non-Hardcore mode supported by the implementation.
- No bundled ROMs, BIOS dumps, personal saves, login tokens or private companion releases.
- Cursor navigation is profile-specific, not a universal control API for every game or ROM hack.

## Getting started

1. Obtain a **Plus** build from this repository's [Releases page](https://github.com/levi-soft/gba-duo-plus/releases) when a maintainer publishes one, or [build from source](docs/BUILDING.md). Upstream APKs are the official edition, not Plus.
2. Open Plus and grant access to your own ROM folder using Android's folder picker. Supported inputs include `.gba`, `.gbc`, `.gb` and supported ZIP archives.
3. Choose a game. Open the in-game menu with L2/BACK for save states, shader selection, Swap A/B and RetroAchievements.
4. Import a compatible companion ZIP from the library menu and assign it to the game. Packs remain separate from the emulator.
5. For desktop authoring tools, enable **Studio Connection** only for the current session and connect using USB debugging/ADB.

No APK is committed into the source tree. Release signing requires the maintainer's own key; the public build no longer silently signs release APKs using a local debug key. Existing locally signed installations may require matching signatures to update. Back up your saves before changing installation/signing arrangements.

Using Daijisho? Follow [Add a GBA Duo Plus player](docs/LAUNCHERS.md#add-a-gba-duo-plus-player-in-daijisho), then select it for your GB/GBC/GBA platform. Plus does not automatically install a player into Daijisho.

## Companion creation and compatibility

The native pack format is inherited from GBA Duo. The [upstream companion collection](https://github.com/khumam/gba-duo-companions) and the format examples in [docs-packs](docs-packs) remain useful references. Each pack may depend on a particular ROM version or memory layout.

[GBA Duo Studio](https://github.com/levi-soft/gba-duo-studio) is our companion visual authoring tool, with drag-and-drop design, an Inspector, sample/live-data preview and English/Vietnamese UI. Use Studio to create or edit a pack, export ZIP, then import and assign it in Plus. For live RAM inspection, enable **Studio Connection** in Plus and connect Studio over USB/ADB. See the [English Studio guide](https://github.com/levi-soft/gba-duo-studio/blob/main/docs/USER-GUIDE.md), [Vietnamese guide](https://github.com/levi-soft/gba-duo-studio/blob/main/docs/USER-GUIDE.vi.md) and [connection protocol](docs/STUDIO-CONNECTION.md).

Studio is a separate tool, not an emulator dependency. A normal pack should remain usable by compatible official GBA Duo releases. Plus-only actions and the Studio bridge require Plus support and should be optional in a portable pack. No private game-specific companion projects are published in this repository.

## Development

Plus-only implementations live in [`plus/`](plus/README.md), with an explicit
upstream integration inventory and upgrade checker. This remains a fork with
official Git history; it is not a patch-only distribution.

```sh
git clone --recurse-submodules https://github.com/levi-soft/gba-duo-plus.git
cd gba-duo-plus
./gradlew :app:assembleRelease
```

Windows: use `gradlew.bat`. Build requirements are JDK 21, Android SDK 35, NDK `27.2.12479018` and CMake `3.22.1`. See [BUILDING](docs/BUILDING.md) for setup, signing, tests and dependency revisions.

[Validation notes](docs/VALIDATION.md) distinguish automated checks, historical device observations and behavior not freshly exercised. A successful build is not a certification of achievement awards, sustained shader performance or every dual-screen device.

Report Plus-specific problems in [this repository's issue tracker](https://github.com/levi-soft/gba-duo-plus/issues), with app version, device, Android version and reproduction steps. Do not include ROM files, credentials or private RAM captures. See [SECURITY](SECURITY.md) for sensitive reports.

## Acknowledgements and license

The original application, its architecture and companion platform are by **khumam and GBA Duo contributors**. Emulation is provided by **mGBA**, scripting by **Lua**, achievement processing by **rcheevos/RetroAchievements**, and the additional shader algorithms by their credited authors in the **Libretro GLSL shader collection**. AndroidX, Compose, Material Components, Kotlin and the Android build tools provide the application platform.

Library cover lookup is made possible by the **Libretro thumbnail community**,
**SteamGridDB**, and the artists/uploaders credited by those services. Downloaded
cover images are not bundled in this source repository; Plus retains provider
and author attribution when it is available.

Please read [CREDITS.md](CREDITS.md), [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and the retained file headers. Source history is rooted in the official `v0.1.6` history; this fork does not claim authorship of that work.

GBA Duo Plus continues under the [GNU GPL v3](LICENSE). Individual third-party components retain their original licenses and copyright notices. Trademarks and branding remain the property of their respective owners. Game Boy/Game Boy Color/Game Boy Advance are Nintendo trademarks; use of their names describes compatibility, not endorsement. This software is supplied without warranty.
