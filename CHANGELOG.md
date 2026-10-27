# Plus changelog

## 0.1.8-plus.4

[Signed APKs and release notes](https://github.com/levi-soft/gba-duo-plus/releases/tag/v0.1.8-plus.4) · [Details](docs/RELEASE-0.1.8-plus.4.md)

- SS Swap cycles through game first, companion first and game only. Game only
  centres a larger game viewport with the existing GBA controls, including when
  no companion is assigned.
- Keep the game session and companion views when switching, release held keys
  and remember the mode across sessions. Retain portrait cutout padding and
  adaptive phone, tablet and Fold geometry.
- Publish both editions with Android versionCode 17; regular dual-screen
  behavior is unchanged. Builder remains 1.1.5.

## 0.1.7-plus.3

[Signed APK and release notes](https://github.com/levi-soft/gba-duo-plus/releases/tag/v0.1.7-plus.3) · [What's new](docs/RELEASE-0.1.7-plus.3.md)

- Rebuild the same automatic companion in place instead of adding a new UUID
  every time. Selected legacy automatic packs are updated in place; failures
  restore the prior pack and assignment. Manual/custom pack IDs stay separate.
- Add **Manage installed packs…** with explicit deletion confirmation and clear
  references from all games, preserving ROMs, saves and original ZIP files.
- Show companion versions and distinguish identical names/versions by ID.
- Keep large pack extraction and deleted-file cleanup off the library UI thread.
- Keep management, deletion confirmations, status messages and Cancel buttons
  in English, including on Vietnamese Android.

## 0.1.7-plus.2

[Download the signed APK and release notes](https://github.com/levi-soft/gba-duo-plus/releases/tag/v0.1.7-plus.2) · [Full Vietnamese feature guide](docs/PLUS-FEATURES.vi.md)

- Publish the GBA Duo Companion Builder website/source, supported exact-ROM
  profiles and Studio project workflow prominently in both READMEs, with a
  complete Plus feature guide and original-author/component acknowledgements.

- Adopt official GBA Duo v0.1.7 as the foundation and retain Plus's dual-display
  player, achievements extensions, shaders, cursor support and Studio bridge.
- Create and assign companions locally from verified exact-ROM builder profiles,
  with cached offline reuse and the existing manual ZIP import retained.
- Add durable library artwork: no-account Libretro lookup/scraping, optional
  SteamGridDB gallery and local image import, preserving image proportions.
- Preserve prior companion assignments when generated-pack writes fail. Keep
  artwork cancellation responsive and stage replacements before final ownership
  checks; load artwork credentials off the UI thread.
- Include the builder/artwork regressions in CI and account for Windows native
  text rendering in screenshot tests without changing original references or
  comparison tolerances. See [validation details](docs/VALIDATION.md).

## 0.1.6

- Align the displayed Plus version with its official v0.1.6 baseline; Android version code increases to 4. Earlier Plus numbers below are local development snapshots, not intervening upstream releases.
- Document adding a Daijisho custom player, including launch arguments, file filters and release/debug package IDs. This documents the existing launch integration rather than adding automatic frontend registration.

## 0.1.2

- Optional per-session Studio Connection in release game sessions.
- Read-only local RAM capture and latest companion-data protocol.
- Source publication documentation, complete upstream/component attribution and explicit release-signing setup.
- About screen identifies the Plus fork, links the original project and acknowledges the core dependencies.

## 0.1.1

- Extended validated companion cursor focus to the inspected ROM profiles used during development.
- Retained generic companion rendering and input/snapshot fixes.

## 0.1.0

- Selected Plus edition based on official GBA Duo v0.1.6 and local fork development.
- RetroAchievements/rcheevos integration and notification presentation.
- xBRZ, HQ2x and ScaleFX pipelines; persistent Swap A/B.
- Separate Plus application identity.
- Excluded experimental ROM-hash overrides and translation implementations from the source.

For exact behavior and evidence boundaries, use the source, [feature comparison](docs/FEATURES.md) and [validation notes](docs/VALIDATION.md). This is a release summary, not a reconstructed claim that every change was individually device-tested in each numbered snapshot.
