# GBA Duo Plus 0.1.7-plus.3

Repeated **Find and create companion** operations previously installed a new UUID
pack on every successful build. Identically named entries accumulated without a
deletion control. This update adds replacement and management of installed packs.

## What's new

- **Update automatic companions in place.** Building the same source profile for
  the same exact ROM uses a stable ID. A selected automatic pack from older Plus
  versions, with a UUID ID, is updated in place. Manual/custom IDs remain separate.
- **Preserve the previous pack on failure.** Extraction and validation run before
  replacement. A failed installation/assignment commit restores the old pack and
  selection. Cancellation and a concurrent manual selection still prevent commit.
- **Manage installed packs.** Open a game's Companion menu, choose **Manage
  installed packs…**, select an old pack and confirm **Delete pack**. Cancel keeps
  it. Deletion removes the installed pack and clears current/legacy assignments
  from all games using it; ROMs, saves, other packs and original ZIP files remain.
- **Distinguish installed versions.** The Companion menu shows each pack's version;
  identical names and versions also show IDs. Existing variants are not silently
  deleted merely because their display names match.
- **English interface.** Management, deletion confirmation, status messages and
  Cancel buttons remain English even when Android is set to Vietnamese.
- **Keep large file operations off the menu thread.** Pack extraction and final
  cleanup of deleted files run in the background. Final assignment/replacement
  is serialized with library actions.

## Companion Builder

The separate [Companion Builder](https://levi-soft.github.io/gba-duo-companion-builder/)
and its FE7 Vietnamese, FE6 Remastered Vietnamese and FE8 Vietnamese companions
remain **1.0.0**. This APK update does not increase those versions or embed ROM
profiles/game assets. The runner cache-query compatibility fix is delivered by
the builder catalogue, separately from these installed-pack management changes.

## Validation and requirements

- 372 Debug host tests and 204 focused Release tests passed on Windows.
- Release APK build, signature/alignment checks, public-source scan and the
  upstream extension-boundary checks passed.
- Signed APK updated Thor with the same signing certificate. Device inspection
  confirmed the version labels, management entry and deletion confirmation.
- Automated regressions cover repeated replacement, legacy automatic IDs,
  assignment-write rollback, current/legacy reference removal, retained saves,
  Cancel/Delete behavior and English resources under Vietnamese Android.

No installed user pack was deleted during device inspection. Live repeated
creation and deletion across a real multi-game library have not been accepted
on-device; those behaviors are covered by the automated tests above.

Android 11/API 30+, ARM64 and a second Android display for the companion remain
required. Development device: AYN Thor. Other dual-screen devices need separate
testing. Application ID: `com.khumam.gbaduo.plus`; Android version code: 11.
Use the signed APK asset to update an existing installation with the same
signing identity. The release includes no ROMs, BIOS, saves or private companions.

## Original authors and acknowledgements

**[khumam](https://github.com/khumam) and the [GBA Duo contributors](https://github.com/khumam/gba-duo)**
created the original emulator application, dual-screen companion platform, pack
format, Lua integration and CSS/layout engine. Plus, maintained by
[levi-soft](https://github.com/levi-soft), is an independent community fork of
official **v0.1.7**, not an official or endorsed release. The original repository
is unchanged; Plus-owned implementations and its explicit host hooks are recorded
in [the extension inventory](../plus/upstream.json).

Thanks also to mGBA/endrift and contributors, Lua.org/PUC-Rio, RetroAchievements/
rcheevos and set authors, Libretro shader and thumbnail contributors, SteamGridDB
artists/uploaders, font authors and the Android development ecosystem. See
[complete credits](../CREDITS.md), [third-party notices](../THIRD-PARTY-NOTICES.md)
and [GPL v3](../LICENSE). Original notices and authorship remain intact.

For retained Plus features such as artwork, achievements extensions, shaders,
controls and Studio, see the [feature comparison](FEATURES.md) and
[Vietnamese guide](PLUS-FEATURES.vi.md).
