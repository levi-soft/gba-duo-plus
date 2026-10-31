# 0.1.9-plus.2

The Companion menu now uses **Get from the web…** for finding, downloading and
creating companions. The separate **Find and create companion** menu item is
removed in both Plus and Plus SS.

The existing official catalog/download/update implementation remains the path
for ready-made ZIPs. On the same catalog page, an exact game-code/CRC profile can
offer **Create companion** using the ROM already on the device. Opening the page
fetches profile metadata; the builder bundle and ROM preparation are needed only
after choosing Create. Official downloads and creation-profile discovery have
independent error handling.

Creation uses the existing local engine and shared generated-pack validation.
ROM bytes stay on the device. Download/creation results are accepted for the
original game and selection, with cancellation and stale-result checks before
assignment. ZIP import and installed-pack management remain available.

Both editions retain official GBA Duo v0.1.9 as their baseline and use Android
versionCode 19 with the existing package IDs/signing identity. Studio Connection
remains a saved Off/On setting. Builder web/PC and the thirty companion profiles
remain unchanged.

Compared with the previously published 0.1.8-plus.4 release, this also adopts
official v0.1.9 display settings, FPS/CPU overlay, game.lua and Wide view.
Physical controls use the official Button Mapping/Hotkeys UI; the separate
Swap A/B option is removed. Studio Connection is in Settings with its On/Off
choice saved for new game sessions. Cocoon can import the additive GB/GBC/GBA
player profiles described in [COCOON-SETUP](COCOON-SETUP.vi.md).

Validation passed 662 Debug tests for each edition, 435 focused Release tests
and five boundary/profile checks. Both signed APKs passed release build, R8,
lint and package audits and installed in place on Thor. Creation/assignment was
checked with Maiden on Plus and The Last Promise on SS, including cancellation;
recreating Maiden retained one automatic ID. Test packs were removed, original
selections restored, and all seven backed-up public save/state files were
unchanged. These checks do not establish gameplay acceptance of all thirty ROMs.

Both signed APKs are distributed in the
[GitHub release](https://github.com/levi-soft/gba-duo-plus/releases/tag/v0.1.9-plus.2).
Install over the matching Plus/SS edition using the existing signing identity;
no uninstall or data reset is needed. Detailed device observations and limits
are recorded in [VALIDATION](VALIDATION.md).
