# Source publication validation

Publication preparation: **2026-10-22**, Plus source version **0.1.2**, based on official **v0.1.6**.

## Current local checks

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
