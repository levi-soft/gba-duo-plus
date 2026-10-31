# 0.1.9-plus.1 test candidate

This candidate adopts official GBA Duo **v0.1.9**, commit
`61582fcb5e07d98b19675e75e9bde532a7c68c4c`. Both editions use Android
versionCode **18** and their existing package IDs and signing certificate.
It has not been published as a GitHub release.

- Adopt the official web-pack catalog, download progress and update badges.
- Adopt aspect ratio, integer/sharp scaling, tilt-shift and the FPS/CPU overlay.
- Adopt format-4 `game.lua`, its separate VM and Wide view. Keep the existing
  format-2 companion cursor contract and permission boundaries.
- Remove the separate Plus Swap A/B option. Official Button Mapping and Hotkeys
  are the physical-control configuration; existing saved mapping takes priority.
  The SS screen-layout Swap button remains available.
- Move **Studio Connection** to **Settings**, with a saved **Off/On** choice for
  each edition. It defaults to Off and applies when a game opens, including in
  Debug builds. See [Studio Connection](STUDIO-CONNECTION.md).
- Keep local Builder 1.2.0, the thirty companion profiles at 1.0.0, native FE
  D-pad focus with game writes Off, retained snapshots, the Plus shaders,
  artwork additions, read-only Studio and the SS edition.
- Supply additive GB/GBC/GBA player profiles for Cocoon's supported custom
  platform import. Package IDs and user data stay in place. See the
  [Cocoon setup guide](COCOON-SETUP.vi.md).

The integration also checks actual ZIP extraction budgets, declared script-path
containment, executable-file consent fingerprints, safety-save success before
game-script writes and minimum-app-version checks for Plus/SS version suffixes.

Automated validation passed **622/622 regular Debug**, **622/622 SS Debug** and
**395/395 focused Release** tests, with zero failures, errors or skips. The native
ASan/UBSan harness passed **326/326** checks. The boundary checker and Cocoon
profile contracts passed **5/5** checks; 49 upstream integration files and 64
Plus extension files are inventoried.

Device observations and remaining limits are recorded in
[VALIDATION](VALIDATION.md). Host tests do not establish all-ROM gameplay
acceptance, every display/orientation, authenticated RetroAchievements events or
every shader/color combination.
