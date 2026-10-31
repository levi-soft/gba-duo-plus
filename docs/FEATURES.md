# Plus 0.1.9 compared with official GBA Duo v0.1.9

Baseline: official tag `v0.1.9`, commit `61582fcb5e07d98b19675e75e9bde532a7c68c4c`.
This is a source/integration inventory. Actual build and device evidence is
recorded separately in [VALIDATION](VALIDATION.md).

| Capability | Integration decision |
|---|---|
| GB/GBC/GBA core | Adopt official 0.1.9; retain the same pinned mGBA revision. |
| Controls and hotkeys | Use the official mapper as the only physical-button configuration. Remove Plus Swap A/B UI/API; retain one-time migration without overriding a saved scheme. SS touch labels keep their semantic A/B meaning. |
| Web companion packs | Use the official catalog/downloader/update/installer path. Get from the web is the single entry and also offers exact-ROM local creation profiles on that page. |
| Local companion creation | Keep Builder 1.2.0: exact-ROM local generation is distinct from downloading prepared ZIPs. All thirty companion profiles remain 1.0.0. |
| Display | Adopt official aspect/integer/sharp scaling, tilt-shift and FPS/CPU overlay. Plus shaders draw into the official viewport/offscreen targets; color correction is applied once. |
| game.lua / Wide view | Adopt official format 4 and independent game-script VM/budgets. Preserve permission and safety-save boundaries. |
| Native FE focus | Keep existing tap-only D-pad/button API and exact-ROM cursor metadata for format-2 packs; game-write permission remains unnecessary. |
| Companion errors | Retain the last valid unit/map snapshot; current frame/error/Wide state remains live. |
| Cocoon | Reuse official VIEW/URI protocol and unchanged artwork matching. Cocoon's registry needs a Plus package entry; supply supported local platform-import profiles instead of changing application identity. |
| Artwork | Keep official folder/Cocoon and SteamGridDB flow, plus Libretro/manual import, shared storage and chosen cover shapes. |
| Studio / SS | Move Studio Connection to a saved Off/On Settings choice. Keep read-only inspection and the separate single-screen edition, using upstream display geometry within each allocated game pane. |
| RA and save identity | Keep the official client with existing encrypted-token/list/state-sidecar extensions and stable per-ROM storage. |

Original authorship and dependency notices are retained. No ROM, save, private
companion, signing credential or private device capture belongs in this source.
