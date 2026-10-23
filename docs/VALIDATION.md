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
