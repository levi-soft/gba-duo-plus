# Plus 0.1.8 compared with official GBA Duo v0.1.8

Baseline: official tag `v0.1.8`, commit `8832d19ebc833da2ab3303d2d1ff48e5720ea68c`.
This is a source comparison; runtime validation limits are in [VALIDATION](VALIDATION.md).

| Capability | Official 0.1.8 | Plus integration |
|---|---|---|
| GB/GBC/GBA emulation | mGBA, dual-screen player | Same pinned mGBA revision; retain the normal player and optional SS edition. |
| Controllers and hotkeys | Full remapping, hold/toggle fast-forward, quick states and slot selection | Adopt upstream. Migrate existing Plus A/B preferences; SS touch labels remain independent of physical mappings. |
| Shader and color settings | Global and per-game shader selection; independent Off/GBA/GBC color correction | Adopt upstream settings; retain xBRZ, HQ2x and ScaleFX. Apply correction once at their final output. Legacy GBA Color selection becomes GBA correction. Scale2x remains hidden as in Plus. |
| Game artwork | SteamGridDB, artwork folder, scraping and per-game locks | Use one shared artwork cache and encrypted SGDB key. Retain Libretro without an API key, the Plus SGDB gallery, manual import and square covers. Copy legacy selections without deleting rollback data. |
| Save synchronization and import | External save folder; import existing `.sav` | Adopt upstream import handling; retain RA state sidecar synchronization and stable per-ROM storage identity. |
| RetroAchievements | Official Softcore client, queued popups, transparent-region and unknown-game fixes | Adopt fixes; retain Keystore token protection, achievement list, state progress and bottom-right popups with 70% panel opacity. |
| Companion JSON/CSS/Lua and graphics | Official format, responsive layout, bounded scripts, permission-gated RAM writes | Retain Plus selector/rendering fixes, last valid snapshot retention and standard button requests. FE navigation uses exact-ROM pack metadata with physical-input cancellation. |
| Automatic companions | No bundled automatic builder | Retain verified Builder integration, local generation, installation and pack management. Builder remains 1.1.5; companion versions do not change. |
| Studio Connection | No Plus inspection bridge | Retain optional per-session read-only RAM/ROM/graphics inspection through ADB. |
| Single Screen | No Plus SS edition | Preserve the approved phone, foldable and tablet layouts, touch controls, quick states, swap and theme switching. |
| Launcher integration | External launch entry point | Retain Plus path aliases and existing-session handling. |
| Translation, hash overrides, Hardcore | Outside this Plus release | Remain excluded. ROM identity is not replaced. |

Original authorship and dependency licenses remain in Git history and source notices.
Private ROMs, saves, companion packs, captures and signing credentials are not shipped.
