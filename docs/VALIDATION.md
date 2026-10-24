# Pre-commit review of Plus 0.1.7-plus.2 (2026-10-24)

## Publication follow-up

The reviewed code was published through [PR #1](https://github.com/levi-soft/gba-duo-plus/pull/1),
merged as `025e111bde6aad947f7839af2aa2447ccf29c273`.
Both PR/push checks passed, and the resulting main-branch
[CI run](https://github.com/levi-soft/gba-duo-plus/actions/runs/40114281819)
also passed, including the Linux native sandbox/graphics checks, focused Release
tests and unsigned release build. The publication follow-up changes documentation
only and rebuilds the signed APK from the published source; the previously
archived APK remains a historical artifact. There has been no additional device
installation or device acceptance run for that rebuild.

## Pre-commit evidence

Current source: `0.1.7-plus.2`, Android code 10, based on official `v0.1.7`
(`6aa3823b7c4e4c8478b74fe24cf837a19224fc23`). The pristine official reference
worktree remains clean. The phone/single-screen extension is absent; player,
input mapper, quick menu and companion controller match the pre-phone baseline.

Independent code, security, test and documentation review found and corrected:

- Artwork request locking could make UI cancellation/navigation wait for image
  encoding and disk writes. Preparation now precedes a nonblocking final claim;
  cancelled/stale requests before that point retain the old cover and clean up
  staged files. A claim accepted before cancellation may finish atomically.
- SteamGridDB key/provider loading ran on the UI thread; it now runs with the
  request on the artwork worker, with missing-key setup delivered on the UI thread.
- A failed generated-pack assignment could truncate the old `pack.json` and also
  fail its rollback write. The replacement now uses a synced sibling file and
  atomic move; the injected partial-write failure leaves prior bytes intact and
  removes the new generated pack.
- CI omitted the new builder/artwork suites. Its Release filters now include
  them. Historical test failures, screenshot inference boundaries and moved
  third-party asset paths are documented accurately.

Final local gates:

- **364 debug tests in 49 suites passed**, including all four screenshot cases;
  zero failures, errors or skips.
- **197 Release tests in 27 suites passed**, using the exact 23 test filters
  extracted from the checked-in CI workflow; zero failures, errors or skips.
- The combined builder/artwork gate passed **48 tests**, including seven new
  regressions for the review fixes.
- Unsigned `:app:assembleRelease` and Release lint passed without signing
  environment variables, matching the public-source build arrangement.
- Upstream-boundary checker and its fixture, staged whitespace checks and the
  source-publication pattern scan passed. The scan includes all newly staged
  artwork sources/tests, documentation and the Windows reference image.
- At preparation time, origin was fetched and the branch contained `origin/main`
  as an ancestor, with 11 existing unpublished commits including the official
  0.1.7 merge before the newly staged changes. No commit, push, PR or release
  upload was performed at that preparation stage.

The archived signed `.2` APK was preserved and predates these review fixes; the
fresh unsigned APK under `app/build/outputs/apk/release/` is built from the
reviewed source. No device installation or new device acceptance run occurred.
Linux ASan/UBSan harness execution and hosted GitHub CI remain separate from
this Windows verification; earlier native/device observations below are historical.

# Windows screenshot validation (2026-10-24)

- The full `:app:testDebugUnitTest` run passed **357 tests in 48 suites**, with
  zero failures, errors or skipped tests, including all four pack screenshots.
- The former `cssPackDetailPage` mismatch was reproduced with the original
  `CompanionScreen.kt` composable from the reference-producing commit `5adb992`,
  retaining the current layout/model: its Windows capture was byte-identical to
  the current composable's capture. Text rasterization and
  advances differ from the original reference; its recording host is unknown.
- The detail case now selects an explicitly Windows-specific reference on
  Windows. All four original references and both comparison tolerances remain
  unchanged. Recording uses the same host-specific path as verification.
- See [reference provenance and recording instructions](../app/src/test/screenshots/README.md).
  Independent review found no blocking issues. Production rendering was not
  changed; no new APK installation, Linux run or physical-device rendering
  validation was performed for this test fix.

The earlier failing screenshot results below are historical observations,
superseded by this Windows run.

# Plus 0.1.7 upgrade validation

Date: **2026-10-23**. Upstream: tag **v0.1.7**, commit `6aa3823b7c4e4c8478b74fe24cf837a19224fc23`.
The RA foundation is now the official 0.1.7 implementation; the former Plus session/native integration is removed.

## Current upgrade checks

- Signed Release APK built with the existing Plus release identity/key, version 0.1.7, code 8.
- **311 JVM tests in 40 suites passed**, zero failures/errors. This includes all JVM suites except `PackScreenshotTest`.
- Coverage includes upstream CSS/media/math/grid, game-title cleanup, save conflicts/synchronization, pack write consent/fingerprints, Plus input/cursor/bridge/shaders/launchers and popup geometry.
- The image-model test now runs with Robolectric and a supported Bitmap factory; it no longer accidentally exercises Android stub methods returning null.
- **141 native harness checks passed on ARM64 Thor**: work RAM write boundaries/permissions, byte limits, action-window closure, time/work budgets, LZ77, sprite/tilemap composition and Plus cursor tap scoping. This was an NDK-built device executable over a fake bus, without ASan/UBSan. CI's Linux harness additionally requests those sanitizers; its remote result is not assumed here.
- A private FE7 adapter replayed an archived battle RAM snapshot on Thor's new Lua runtime for 20 ticks, with no writes: approximately 0.8-3.4ms/tick and 61.6K-228.2K charged instructions, below the unchanged 25ms/1M per-call cap. This does not establish every map/chapter's worst case. No private files enter this source tree.
- Existing signatures matched, and in-place APK updates succeeded without uninstalling.
- On Thor: new library/Settings and RA account page opened; ScaleFX and saved Swap A/B remained selected; Studio returned the expected ROM identity/live data; tapping an FE7 Map roster unit moved the game cursor to it.
- Official RA client automatically authenticated with the existing encrypted token and recognized the supported original FE7 ROM as game 554. A modified Vietnamese ROM remained unsupported by its actual hash, as expected; no hash override was added.
- Popup clipping reproduced on Thor during translated GL overlays. Opacity-only animation and bounded/weighted content fixed it: both sign-in and game-summary screenshots showed their full text and bottom border. A narrow-screen long-title geometry regression test also passed.

## Fixes found during integration/audit

- Preserve Plus cursor action scope/error cleanup when adopting the refactored upstream Lua host.
- Keep indexed/cached CSS resolution while applying the new media-query predicates.
- Sync RA progress sidecars with numbered save states.
- Refuse pack writes when the required safety-state save fails.
- Extend the official RA client, rather than run both clients; encrypted credentials, queued gameplay native commands, state progress and achievement list use that one client.
- Reset the RA load-request flag on logout so a later sign-in reloads the running game.
- Keep local artwork as an image placeholder and use bounded cached badge/game-image downloads.
- Correct a few library hint glyphs damaged by text encoding during the merge.

## Remaining validation boundaries

No new gameplay unlock plus server-confirmed award was deliberately exercised. Account switching during a live game, sustained shader heat/performance, every private pack/chapter, and non-Thor devices still need broader testing.
External save sync has automated conflict/failure coverage; an actual two-device sync was not performed.
`PackScreenshotTest`'s four Compose golden-image cases were not part of this 311-test Windows run; native/UI device observations do not turn them into passing tests.
The public-source pattern scan passed. Whitespace checks passed for Plus changes relative to the v0.1.7 tag; untouched vendored sources/license files retain upstream whitespace.
No GitHub publication/release upload is implied by a local signed build.

## Earlier publication record

# Source publication validation

Publication preparation: **2026-10-22**, Plus source version **0.1.2**, based on official **v0.1.6**.

## Current local checks

Version alignment follow-up on **2026-10-22**: Plus **0.1.6** (Android version code 4) passed `assembleRelease` and 18 focused tests across `PlusFeatureContractTest`, `LaunchIntentResolverTest` and `ExternalRomPathTest`, with zero failures/errors. The public-source scan and whitespace checks also passed. The expanded Daijisho player guide was checked against Daijisho's configuration documentation and Plus's launch resolver; no new Daijisho device test was performed for this documentation update.

- JDK 21, configured Android SDK/NDK/CMake: release native/Android build passed.
- Focused release JVM gate: **51 tests passed in 10 suites, zero failures/errors**.
- Coverage: Plus identity/removed features; read-only bridge bounds; cursor-state validation/cancellation; input mapper; shader presets; launch intent/path/archive handling; retained companion snapshots; raster-grid behavior.
- Release signing changed to explicit environment configuration. With no signing environment variables, public-source builds produce an unsigned release APK.
- Source publication excludes local SDK paths, signing keys, app preferences, account credentials, ROMs/saves, local APK archives and private game companion projects.

These are **targeted tests**, not a claim that the complete host suite or every device scenario was rerun. Earlier full-suite runs recorded Compose screenshot timeouts and a plain Android-stub bitmap-allocation limitation; those results are not recast as passing.

## Historical device observations

Earlier development used AYN Thor for selected launcher intent cases, restored Swap A/B/shader preferences, shader orientation/visual checks, companion navigation and RetroAchievements login/detection/presentation. Some observations predate the final Plus-only bridge/signing/publication changes.

No device was installed, modified or freshly tested as part of this GitHub source-publication step. In particular, a genuine gameplay unlock plus server-confirmed achievement award, sustained shader thermals/performance, every cursor profile and non-Thor dual-screen devices remain separate validation work.

## GitHub CI

The workflow builds an unsigned release and runs the focused gate from a recursive checkout using pinned dependencies. Its remote result is not assumed before GitHub executes it. CI artifacts, if downloaded, are unsigned development output rather than a maintainer-signed release.

Build/test success establishes source/build behavior within that environment. It does not promise RetroAchievements server availability, recognition of arbitrary patched ROMs or universal companion compatibility.
# Plus extension boundary refactor (2026-10-23)

- Moved 18 existing Plus-only source/resource files to `plus/src/main` and extracted
  the Studio queue/socket lifecycle into a nineteenth extension file.
- Release assembly and 311 JVM tests passed (the same four Compose golden cases
  remain excluded on this host). Native code was unchanged in this refactor.
- The boundary checker and its temporary-repository regression fixture passed;
  31 upstream application/build integration files have explicit reasons.
- Created a clean detached official v0.1.7 reference worktree outside the Plus
  repository. It is not another bundled copy of the emulator.
- This refactor build was not installed on the device; the previously validated
  signed APK remains installed. No new real-device regression claim is made here.

# Studio ROM inspection revision 2 (2026-10-23)

- Added bounded, opt-in, read-only ROM/graphics inspection in Plus-owned Kotlin/C
  modules. Revision-1 response envelopes remain compatible with existing clients.
- 312 JVM tests passed (four Compose golden tests still excluded on Windows).
  The native sandbox harness plus new range checks passed 155 checks on Thor.
- Release assembly, native JNI loading, in-place signing/update and revision-2
  live capability handshake succeeded. A known ROM icon matched the same local
  ROM bytes and displayed through a generated asset-free companion on the device.
- New range checks reject I/O, BIOS, save memory, mirrors beyond actual ROM size,
  overflow, truncated reads and oversized requests. No write or execution command
  was added to the bridge.
- This is a graphics authoring foundation, not automatic game asset recognition
  or a claim that Studio now covers all companion logic visually.
