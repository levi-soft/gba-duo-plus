# 0.1.8-plus.1 upgrade candidate

Both Plus and Plus SS now use official GBA Duo v0.1.8 (`8832d19`). The mGBA revision is unchanged. Android versionCode is 14; SS retains its separate application ID and `-ss` version suffix.

- Adopt controller/button/hotkey mapping, independent color correction, per-game settings, artwork folders/scraping, save import and the upstream RetroAchievements fixes.
- Keep Builder 1.1.5, all companion integrations, Studio Connection, Plus shader pipelines, Libretro/manual artwork, encrypted credentials, RA lists/state progress and the approved SS layouts.
- Preserve old A/B mapping on existing Plus installs. Fresh installs use upstream's labelled A/B defaults. Existing shader selections migrate; GBA Color becomes Shader Off plus GBA color correction.
- Copy legacy selected covers into the shared artwork cache. Keep their original files for rollback. All artwork sources use the same selection; removing a cover or clearing the cache prevents legacy artwork from reappearing.
- Library X now opens Game settings, where shader, color and artwork belong to the selected game. Settings > Scraping includes folder/SGDB batch lookup and the retained Libretro missing-cover workflow.
- SS screen Save/Load retain their dedicated slot 0. Configurable physical quick-state hotkeys use the upstream selected slot 1-9. Swap only changes screen placement.

Local verification: 320 focused Release tests, 537 full SS Debug tests and 176 native checks passed. Both signed APKs build and pass package/signature audits. Thor verified an in-place regular upgrade, old FE7 save loading, the assigned companion and map-focus navigation, plus SS startup, 10 touch buttons, quick states, fast-forward, swap and four rotations without reloading the core. All three Plus shader pipelines rendered with GBA correction. Online RA account/achievement events and exhaustive GBC/color combinations were not exercised in this device pass.

This is a local upgrade candidate. The public download remains 0.1.7-plus.4 until a new public release is published. See [validation](VALIDATION.md) and [feature comparison](FEATURES.md).

Upgrade in place with the APK for the same edition and signing certificate; no uninstall or data clearing is required. Keep a backup before testing a downgrade: source migration leaves legacy preferences and covers, but an older APK does not understand the new controller/per-game settings and artwork chosen after the upgrade.
