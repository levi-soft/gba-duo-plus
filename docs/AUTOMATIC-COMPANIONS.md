# Automatic companions (Plus only)

This feature belongs to GBA Duo Plus. It is not attributed to, or advertised as part of, the official GBA Duo baseline. The existing manual ZIP picker and assignment menu remain available.

## Player flow

In the library, select a game, open its Companion menu and choose **Get from the web…**. This official catalog page also offers **Create companion** when a creation profile matches the actual ROM game code and CRC32. There is no separate creation entry in the Companion menu. Ready-made ZIPs use the official downloader; selecting a matching creation profile runs the local builder using the ROM on the device. The original ROM and its identity remain unchanged.

Opening the catalog fetches profile metadata without preparing the ROM or
downloading the builder bundle. Those steps happen after selecting Create.
Official pack browsing and creation-profile discovery report failures
independently, so one source does not hide the other.

Your ROM is never uploaded. The downloaded builder is cached and verified again before offline reuse. Only exact ROM profiles in the catalogue are supported; an unsupported ROM can still use manual ZIP import. The [website](https://levi-soft.github.io/gba-duo-companion-builder/) offers the same builder with ZIP/project downloads for manual use and [GBA Duo Studio](https://github.com/levi-soft/gba-duo-studio).

Automatic packs use a stable ID for each source profile and exact ROM. Creating
the same companion again updates its installed copy. A failed installation
commit restores the previous pack; assignment failure preserves the previous
assignment but can leave the new pack installed. A selected automatic pack made by older
Plus versions (UUID ID) is updated in place. Custom/manual IDs stay separate.
Cancelling or failing creation before commit leaves the previous assignment in
place. Creation returns its installed pack to the catalog and library; the final
assignment is accepted only for the original game/selection. If a page closes
after an installation has already committed, the pack can remain installed
without being newly assigned.

Official ZIP downloads retain the official installer. Closing the page prevents
a late result from being assigned, but installation already underway can finish
and leave its pack installed or updated.

The Companion menu shows versions and disambiguates identical names/versions by
ID. Choose **Manage installed packs…**, select a pack, then confirm **Delete pack**
to remove an old duplicate. Deletion clears all current and legacy assignments
to that pack, retaining ROMs, saves, other packs and the original imported ZIP.
Existing duplicates are not silently deleted merely because their names match.

The new assignment is written and synchronized in a sibling temporary file,
then atomically replaces `pack.json`. A partial write or failed replacement
preserves the old assignment without needing free space for a rollback write.

## Implementation boundary

- `plus/src/main/java/.../companion/builder/`: catalogue, bounded ROM staging, verified download/cache, isolated local WebView runner, cancellation and installation transaction.
- `plus/src/main/res`: the Plus entry's resources and translations.
- Host integration: the existing Get from the web action carries immutable ROM identity into the official catalog; a Plus helper supplies exact-ROM creation discovery and a result-based launch. Private Activity registration and JavaScript bridge keep rules serve the local engine. These edits are inventoried in `plus/upstream.json`.
- This feature bundles no game assets, ROM profiles or game-specific builder readers into the APK. Profiles and authored UI are delivered by the external builder catalogue; game-owned images/text are prepared locally from the player's ROM.

The native download component uses HTTPS with same-origin catalogue/bundle rules, explicit byte/file limits and SHA-256 verification. Builder execution uses a private HTTPS origin, serves only the staged bundle and a single ROM route, blocks external requests/navigation, and exposes only the bounded build protocol. Catalogue HTTPS is the publisher trust boundary; SHA-256 detects bundle mismatch but is not an independent publisher signature.

The runner and website use the same JavaScript preparation/compiler implementation. Generated pack validation and assignment run after preparation. The existing emulator companion permission model still applies; this feature does not grant memory-write permission, enable ROM hash overrides, or introduce translation services.

The Plus entry and creation screen use English consistently with the current Plus interface, including on Vietnamese Android devices. The website interface offers English or Vietnamese. The current Vietnamese game profiles preserve the authored companion UI from the private test kit; the website language selector does not translate the companion or its ROM text.

## Upstream upgrade or replacement

1. Keep the pinned official source as the baseline and use `tools/upstream-boundary.mjs compare <candidate>` when upgrading.
2. Check whether the official feature provides exact-ROM discovery, local asset generation, manual import, cancellation/error preservation and suitable offline behavior. Compare behavior before declaring equivalence.
3. If complete, route the library action to the official implementation and remove the `companion/builder` extension, Plus entry resources, private Activity registration and bridge keep rule.
4. Remove the corresponding integration reasons from the inventory. Do not replace unrelated official pack/runtime classes with Plus copies.
5. Preserve installed pack directories and the game's pack assignment. Existing generated companions are ordinary format-2 packs and do not require the builder to keep running.
6. Recheck library/game launch, manual import/assignment, auto creation, cancellation, saves, and RA identity on device.

Removing the automatic-creation entry does not remove manual ZIP import or require ROM/save migration. This is an extension source boundary, not an automatic patch/rebase system.

## Validation

Initial builder validation recorded on 2026-10-24 (before the artwork and Windows screenshot follow-ups):

- All 16 automatic-builder core and bounded-ROM-source tests pass. They cover identity/trust, archive limits, cache fallback, generated-pack transaction and bounded ROM copying.
- A separate Robolectric resource test with Vietnamese Android configuration confirms the new menu, title and Cancel label remain English. It passes after removing the unintended device-locale override. The English UI release rebuild and installation also succeeded.
- That debug JVM run contained 332 tests: 331 passed and one inherited screenshot reference mismatch remained (`PackScreenshotTest.cssPackDetailPage`, 9,090 differing pixels). That initial run was not fully green.
- Before this feature, all four screenshot tests timed out inside Compose 1.7.5's hardware frame-commit wait on Robolectric. A test-host-only capture helper follows the [AndroidX fix](https://android.googlesource.com/platform/frameworks/support/+/bcef06da086866f7389fdb8e8d5a80cff2ea2808), preserving the same root crop and PixelCopy path. Three unchanged references passed in the initial run; no reference image, comparison tolerance or production renderer was changed at that step.
- The remaining detail mismatch reproduces on a detached baseline at `f00cefda4de5c73f6efd3b9d85490b25f4212d61`, with the same capture helper. Baseline and feature detail captures have identical SHA-256: `8cf8398db018f2b16b58346e32a82222c4dfa2c9b82e880ca9060b9f33a8d268`. This establishes that the builder feature did not introduce that visual difference.
- The signed release APK builds and installs successfully. On Thor, the user confirmed the corrected English menu and successful automatic companion creation for FE8 Vietnamese. This confirms that creation flow on the real device; separate device checks of offline reuse, cancellation and the rendered generated pack have not been completed.

The website distribution also has contract/privacy and shared-core/runner tests. Device results must be recorded here when exercised, including automatic creation, cancellation, manual pack preservation and offline reuse.

The later Windows screenshot-reference fix brought the combined builder/artwork
debug suite to 357/357 passing, including all four screenshot cases, with the
original references and comparison tolerances retained. Current pre-commit
verification and subsequent fixes are recorded in [VALIDATION](VALIDATION.md).

## Plus 0.1.7-plus.3 regression checks

The full Debug host suite passed 371 tests, including duplicate-build replacement,
legacy automatic ID reuse, rollback after assignment-write failure, deletion
across current/legacy assignments, version/ID labels and the real confirmation
dialog. The confirmation test verifies Cancel preserves the pack before testing
Delete. These host checks do not establish live device acceptance.

The focused Release gate passed 203 tests and assembleRelease succeeded.
The signed 0.1.7-plus.3 APK (Android code 11) matched the installed signing
certificate and updated Thor using adb install -r. On-device inspection confirmed
version labels, the management entry and deletion confirmation. No installed
user pack was deleted during this check; a full real-device repeated build
remains separate from the automated transaction regressions above.

Pack management, deletion confirmations, status messages and Cancel buttons use
English consistently with Plus, including on Vietnamese Android. The Vietnamese
overrides were removed; BuilderLanguageTest verifies the locale fallback.
