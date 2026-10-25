# GBA Duo Plus 0.1.7-plus.4 — companion input and stable views

Companions can now control matching games through standard button pulses, and
compatible FE packs can supply their own exact-ROM cursor metadata. Failed Lua
ticks retain the last complete companion view instead of replacing it with an
empty snapshot. Based on official GBA Duo **v0.1.7**; Android version code **13**.

## Added and fixed

- **Companion-owned standard input:** `input.press` accepts A, B, Select, Start,
  D-pad, L and R, including short combinations. It works beyond Fire Emblem for
  matching packs. Requests come from explicit tap actions and last at most 30
  emulated frames. Physical input, pause, state changes and pack changes cancel
  pending input; this feature does not require RAM-write permission.
- **Pack-owned FE map navigation:** versioned, bounded exact-ROM metadata replaces
  the need to add every compatible hack to an APK whitelist. FE6's 16-bit state
  and different coordinate offsets are handled separately from FE7/FE8. Navigation
  uses game D-pad input, validates occupancy and cancels outside the free cursor.
  Legacy packs retain the previous host profiles. Arbitrary games use standard
  button actions; FE cursor metadata is not a universal memory-layout detector.
- **Stable companion view on failed ticks:** retain the last successful data,
  omit already consumed UI commands and expose `_scriptStale`, `_scriptError`
  and `_scriptDataFrame` diagnostics. Recovery or a confirmed empty selection
  replaces the retained view. Pack changes and save-state requests clear it.
  The existing 1M-work/25-ms per-call sandbox limits remain unchanged.
- **Builder 1.1.0:** the separate builder publishes ten exact-ROM profiles, with
  seven English companions finalized as 1.0.0 alongside three Vietnamese packs.
  Units, Preparations, chapter names and Forecast popup behavior live in the
  builder's authored Lua, not in APK game-specific UI code. Void adds six skill
  slots and Talk. See [the supported-game list](https://levi-soft.github.io/gba-duo-companion-builder/supported-games.html)
  and [builder scope/limits](https://github.com/levi-soft/gba-duo-companion-builder/blob/main/docs/RELEASE-1.1.0.md).

Automatic build/install, in-place pack updates, confirmed pack deletion, library
artwork, additional shaders, persistent Swap A/B, launcher compatibility, Studio
inspection and the inherited RetroAchievements client remain available. Management
and application messages use English for the international release. See the
[complete feature comparison](FEATURES.md) and [Vietnamese guide](PLUS-FEATURES.vi.md).

## Validation and requirements

Publication checks cover the full Debug host suite, focused Release gate,
native sandbox/input harness, source publication scan, extension-boundary check,
Release APK build and signature/alignment verification. The APK uses the existing
release certificate and can update the preceding signed Plus build. Native fixture
tests do not replace device acceptance for every new pack or game.

Local results: **380 Debug tests, 212 focused Release tests and 176 native harness
checks**, all passed. The four Compose screenshot cases run in Debug, where the
test Activity is available; they are intentionally outside the Release gate.

FE6 cursor movement and English FE7/FE8/Ember companions were tested during the
development cycle. The user confirmed the revised Void pack stopped continuous
flicker; monitoring still found isolated timeout ticks, covered by the new
retention regression. The production retention APK has automated coverage; it
does not claim the sandbox error itself can never recur.

Android 11/API 30+, ARM64, and a second Android display for the companion are
required. Primary device: AYN Thor. Other devices need separate testing. No ROM,
BIOS, save, private companion or signing key is included in this release.

## Authors and acknowledgements

**[khumam](https://github.com/khumam) and the [GBA Duo contributors](https://github.com/khumam/gba-duo)**
created the original emulator, dual-screen companion platform, pack format, Lua
host and CSS/layout engine. Thank you for the foundation of Plus. Maintained by
**[levi-soft](https://github.com/levi-soft)**, this is an independent community fork,
not an official or endorsed GBA Duo release. Original history and notices remain.

Thanks also to mGBA/endrift, Lua.org/PUC-Rio, RetroAchievements/rcheevos and set
authors, Libretro shader/thumbnail contributors, SteamGridDB artists, font authors
and the Android ecosystem. See [full credits](../CREDITS.md), [third-party notices](../THIRD-PARTY-NOTICES.md),
[upstream integration inventory](../plus/upstream.json) and [GPL v3](../LICENSE).
