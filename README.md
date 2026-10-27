# GBA Duo Plus

**A community fork of [GBA Duo](https://github.com/khumam/gba-duo), created by [khumam](https://github.com/khumam).**

GBA Duo made the central idea possible: play Game Boy Advance, Game Boy Color and Game Boy games on one screen of an Android handheld, with an interactive companion on the other. Plus builds on that work rather than replacing its authorship. Thank you to khumam and every upstream contributor for publishing the original application and companion system.

Maintained by [levi-soft](https://github.com/levi-soft), Plus adds automatic companion creation, selectable library covers, additional pixel-art shaders, persistent control preferences, companion navigation for supported games and an optional GBA Duo Studio connection. It extends the official RetroAchievements client and adopts controller mapping, per-game settings, color correction and artwork settings from GBA Duo 0.1.8.

**Current source version:** `0.1.8-plus.3` · **Upstream base:** `v0.1.8` · **License:** [GPL v3](LICENSE)

**[Download GBA Duo Plus 0.1.8-plus.3 — APK and release notes](https://github.com/levi-soft/gba-duo-plus/releases/tag/v0.1.8-plus.3)**

**Companion tools:** [GBA Duo Companion Builder — build from your ROM](https://levi-soft.github.io/gba-duo-companion-builder/) · [Builder source](https://github.com/levi-soft/gba-duo-companion-builder) · [GBA Duo Studio — visual pack editor](https://github.com/levi-soft/gba-duo-studio)

**[Full Plus feature guide in Vietnamese](docs/PLUS-FEATURES.vi.md)** · [0.1.8-plus.3 release details](docs/RELEASE-0.1.8-plus.3.md)

**Website:** [GBA Duo Plus](https://levi-soft.github.io/gba-duo-plus/) ·
[GBA Duo Plus SS for phones, tablets and foldables](https://levi-soft.github.io/gba-duo-plus/single-screen/) ·
[GBA Duo Companion Builder](https://levi-soft.github.io/gba-duo-companion-builder/)

**Single Screen edition:** [GBA Duo Plus SS](docs/SINGLE-SCREEN.md)
embeds game and companion on one display, with portrait/landscape touch controls.
It is a separate installable build in the same [Plus.3 release](https://github.com/levi-soft/gba-duo-plus/releases/tag/v0.1.8-plus.3).
The default Plus build retains dual-screen behavior. See the SS guide for device
validation and known startup limits.

[Tiếng Việt](README.vi.md) · [Features](#features) · [Comparison](docs/FEATURES.md) · [Build](docs/BUILDING.md) · [Launchers](docs/LAUNCHERS.md) · [Studio](docs/STUDIO-CONNECTION.md) · [Credits](CREDITS.md)

## New in 0.1.8-plus.3

SteamGridDB defaults to square covers, with explicit Square / Portrait / All shapes choices in the existing picker. Thumbnails keep their proportions; automatic SteamGridDB scraping also defaults to square. [Release details](docs/RELEASE-0.1.8-plus.3.md).

## New in 0.1.8-plus.2

Fixes artwork selection from Game settings and removes the duplicate Plus SteamGridDB client/settings page. SteamGridDB uses the official picker and one shared key editor; Plus retains Libretro and local image import. [Hotfix details](docs/RELEASE-0.1.8-plus.2.md).

## New in 0.1.8-plus.1

The source now includes official 0.1.8 while keeping Plus and SS extensions. Existing controls, color/shader choices and selected covers are migrated without clearing saves or companion assignments. See [upgrade notes](docs/RELEASE-0.1.8-plus.1.md).

## New in 0.1.7-plus.4

Companions can request standard GB/GBA button pulses for matching games. FE Map focus reads exact-ROM navigation metadata from the pack, allowing compatible new profiles without adding an APK whitelist entry. Transient Lua errors retain the last complete companion view and expose stale/error diagnostics instead of blanking the screen. [Release notes](docs/RELEASE-0.1.7-plus.4.md).

## Project identity

| | GBA Duo Plus |
|---|---|
| Source | [levi-soft/gba-duo-plus](https://github.com/levi-soft/gba-duo-plus) |
| Original project | [khumam/gba-duo](https://github.com/khumam/gba-duo) |
| Exact upstream baseline | [`8832d19ebc833da2ab3303d2d1ff48e5720ea68c`](https://github.com/khumam/gba-duo/commit/8832d19ebc833da2ab3303d2d1ff48e5720ea68c), tag `v0.1.8` |
| Release application ID | `com.khumam.gbaduo.plus` |
| Debug application ID | `com.khumam.gbaduo.plus.debug` |
| Requirements | Android 11/API 30 or later; ARM64; a second Android display for the companion |
| Primary development device | AYN Thor; other dual-screen devices require testing |

Plus is **not an official GBA Duo release** and is not endorsed by its original author, RetroAchievements, Libretro or the other credited projects. It installs separately from the official application. Separate package IDs mean separate app-private saves, settings, login sessions and folder permissions; there is no automatic cross-edition migration.

## Features

### GBA Duo Companion Builder and automatic companions (Plus only)

The library's Companion menu includes **Find and create companion**. Plus downloads a compatible builder profile from the [Companion Builder website](https://levi-soft.github.io/gba-duo-companion-builder/), creates the companion from the player's ROM on the device, and installs and assigns it after successful validation. The ROM is never uploaded. Verified builder downloads are cached for later offline use.

Manual ZIP import remains available. Rebuilding the same automatic companion updates its existing copy; a failed update restores the previous pack and assignment. Custom/manual IDs stay separate. Use **Manage installed packs…** to delete old duplicates. Only exact ROM identities listed in the catalogue are supported.

**GBA Duo Companion Builder is also a separate [website](https://levi-soft.github.io/gba-duo-companion-builder/) and [open-source project](https://github.com/levi-soft/gba-duo-companion-builder).** Choose a ROM in Chrome/Edge, build locally, and download a companion ZIP for manual import or a project JSON for GBA Duo Studio. The website offers English and Vietnamese. Both the website and Plus use the same builder core; ROM bytes, extracted images and game text stay on your device, and the original ROM is unchanged.

### Builder 1.1.0 — ten exact ROM profiles

Seven English companions are now finalized at **1.0.0**, alongside the three Vietnamese companions at **1.0.0**. Builder **1.1.0** adds the seven profiles to both the website and Plus automatic creation. Future companion fixes keep the builder version; only adding profiles advances it.

| Profile | Game code | CRC32 | Companion |
|---|---|---|---|
| FE7 Vietnamese | `AE7E` | `32CD990D` | 1.0.0 |
| FE6 Vietnamese | `BE8E` | `BD53D5F3` | 1.0.0 |
| FE8 Vietnamese | `BE8E` | `3CD932D7` | 1.0.0 |
| FE7 English | `AE7E` | `2A524221` | 1.0.0 |
| FE8 Europe | `BE8P` | `B3005195` | 1.0.0 |
| FE8 English - USA/Australia | `BE8E` | `A47246AE` | 1.0.0 |
| FE6 T-EN | `AFEJ` | `197DE10B` | 1.0.0 |
| Project Ember | `AFEJ` | `636F7B05` | 1.0.0 |
| Project Ember Reverse Recruitment | `AFEJ` | `29BC2257` | 1.0.0 |
| Void's Blitzarre Adventure | `BE8E` | `9D22107C` | 1.0.0 |

FE6 T-EN is original FE6; Vietnamese Remastered is an FE8-based hack. Other ROM revisions and patches need independent profiles. English profiles use English Growth/Analysis and shared UI. Void includes six skill slots and Talk; its Map range overlays still use base movement/weapon ranges and do not include range-changing skills. See [release scope and validation](https://github.com/levi-soft/gba-duo-companion-builder/blob/main/docs/RELEASE-1.1.0.md).

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
| Library artwork | Uses the official SteamGridDB picker, image loader and cartridge cover layout. Plus adds local PNG/JPEG/WebP import and no-key Libretro lookup/missing-artwork scraping. Selected covers remain in the shared local cache. [Setup and extension boundary](docs/ARTWORK.md). |
| RetroAchievements | Retains the official 0.1.8 [rcheevos](https://github.com/RetroAchievements/rcheevos) client and adds encrypted token storage, achievement-list UI and save-state progress sidecars. Account sign-in, ROM recognition and the underlying client come from the official integration. |
| Achievement presentation | Automatic sign-in/game notices, game image and unlocked/total count; bottom-right unlock popups with a 70% background opacity. Unsupported ROMs show a no-achievements notice. |
| Pixel-art shaders | **xBRZ, HQ2x and ScaleFX**, adapted from the Libretro GLSL shader collection with original notices retained. |
| Controls | **Swap A/B is remembered** between game sessions. Existing shader persistence is retained for the expanded preset set. |
| Companion interaction | Tap-scoped `input.press` sends standard buttons for any matching game. Optional `input.focusUnit(id)` uses pack-owned exact-ROM FE map contracts, with legacy-pack fallback. Input is bounded and physical input cancels it. [Authoring and compatibility](docs/CURSOR-FOCUS.md). |
| Companion rendering | Additional sizing, raster-grid/aspect-fit and retained-snapshot behavior used by complex packs. This does not make every arbitrary pack/device combination automatically compatible. |
| Daijisho custom player | Add **GBA Duo Plus** as a player to launch ROMs directly. Retains upstream's `LaunchActivity`, with additional path aliases, input validation and session/lifecycle handling. See [player setup and launch arguments](docs/LAUNCHERS.md#add-a-gba-duo-plus-player-in-daijisho). |
| Studio Connection | Per-session opt-in, read-only local RAM/snapshot and bounded ROM/VRAM/palette/OAM inspection for compatible desktop tooling. Revision-2 capability discovery retains the existing wire envelope. No LAN listener or memory-write command. The upstream permission-gated pack tap API is separate from this read-only desktop bridge. |

RetroAchievements uses the official integration, including the 0.1.8 popup and ROM-recognition fixes, as its foundation. Plus adds encrypted token storage, achievement-list UI, save-state progress and the selected popup presentation to that single official client. The former Plus RA runtime has been removed.

Inherited from 0.1.7: pixel-style library and Settings, readable game titles, external save-folder synchronization, permission-gated RAM writes, ROM sprite/tilemap decoding, responsive CSS and bounded Lua execution.

See [the detailed baseline comparison](docs/FEATURES.md). Credit for algorithms, cores and APIs belongs to their original authors; Plus contributes their Android integration and the changes documented here.

### Deliberate boundaries

- **No ROM-hash override or identity spoofing.** RetroAchievements identifies the actual loaded ROM. A modified/translated ROM must be supported by RetroAchievements to use its achievement set normally.
- **No translation engine or translation credentials.** Google/Qwen/offline translation experiments are not part of Plus.
- **No Hardcore mode.** This edition retains companion and save-state workflows and runs achievement support in the non-Hardcore mode supported by the implementation.
- No bundled ROMs, BIOS dumps, personal saves, login tokens or private companion releases.
- Cursor navigation is profile-specific, not a universal control API for every game or ROM hack.

## Getting started

1. Download the signed **[0.1.8-plus.3 APK](https://github.com/levi-soft/gba-duo-plus/releases/tag/v0.1.8-plus.3)**, or [build from source](docs/BUILDING.md). Upstream APKs are the official edition, not Plus.
2. Open Plus and grant access to your own ROM folder using Android's folder picker. Supported inputs include `.gba`, `.gbc`, `.gb` and supported ZIP archives.
3. Choose a game. Open the in-game menu with L2/BACK for save states, shader selection, Swap A/B and RetroAchievements.
4. Open the game's Companion menu and choose **Find and create companion**, or import and assign a compatible ZIP. The [Builder website](https://levi-soft.github.io/gba-duo-companion-builder/) can also create ZIPs locally. Packs remain separate from the emulator.
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
