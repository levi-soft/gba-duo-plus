# GBA Duo Plus SS — Single Screen edition

SS is a separate phone edition of **0.1.8-plus.2**, built from the Plus source.
It embeds the existing companion controller in the game Activity. It does not
require a second Android display or alter the companion ZIP format.

## Layout and controls

- Phone landscape gives the game a large central viewport, with D-pad on its left and
  a companion above diagonal A/B on its right, with B below and left of A. The companion receives about
  one third of the width for touch interaction, subject to available height. L/R occupy
  the bottom corners. The D-pad sits at thumb height, with the GBA Duo logo above it
  and a round theme toggle in the upper left. The main frame is vertically centred
  in the area above the footer; the secondary frame aligns with its top edge.
  Swap, Save, Select, Menu, Start, Load and >> form the footer, with Menu centred.
- Portrait stacks the game and companion, then places all controls below them:
  shoulders and the centred theme toggle above a larger D-pad and A/B. The logo
  sits at the centre of the thumb-control area; seven utility controls run along the bottom.
- Expanded tablet/foldable windows stack two equally wide screens in both orientations,
  preserving each screen's aspect and centring the complete stack. Landscape
  places controls beside the stack, with Theme in the upper right rail and B below A.
  Every A/B cluster uses the same portrait-reference angle and spacing; the pair
  scales and moves together. Classification uses current window dp dimensions:
  a shortest side of at least 600dp, or at least 480dp with aspect no wider than 1.6:1.
  A narrow outer display or smaller window retains the phone layout.
- **Swap** exchanges the two screen positions, preserving their respective aspects
  and the existing surface/companion views. It releases held inputs, does not reload
  the ROM, and remembers the chosen order across sessions.
- The game keeps its GBA 3:2 or GB/GBC 10:9 aspect. The companion keeps a 4:3
  viewport. Neither stretches to fill the phone. A light shell and raised grey
  screen frames surround the viewports; red A, yellow B and rainbow accents
  follow the supplied Thor Rainbow/NDS skin reference without adding NDS controls.
- Game controls are D-pad, A, B, L, R, Select and Start; no X/Y or analog stick.
  **Menu opens the emulator Quick Menu**, including during loading where it cancels
  startup. There is no separate Back button or layout editor.
- **Save/Load** use quick slot 0 in the game's state folder, leaving manual slots
  1–9 in the existing menu. Feedback reports completion or a missing quick save.
  **>>** toggles fast-forward at the speed selected in the Quick Menu and lights
  while active. Pause, menus, rotation and focus loss release it.
- **Dark/Light** changes the shell and controls, retaining red A/yellow B. The
  choice persists across sessions. It does not recolour a companion's authored UI.
- Multiple fingers, diagonals and sliding A/B are supported. Touch input and
  physical gamepad input release independently. Labelled touch A/B keep their
  labels' meaning; the existing Swap A/B preference applies to the gamepad.
- Rotation resizes the existing surface and companion without recreating the
  Activity, core or loaded ROM. Auto-rotation follows Android's user setting.
  Immersive windows use the full display. In portrait, the hardware camera/notch
  safe top inset is reserved. Hiding the
  status and navigation bars does not reserve their former padding. Library branding and
  Settings adapt to narrow/wide windows instead of clipping off screen.
  A wide library header puts its wordmark, game counts and Settings on one row.
  Short landscape windows use compact labels/actions and reduced carousel padding
  to leave more height for artwork. Narrow windows retain the two-row header.

## Startup and lifecycle

ROM reads, ZIP extraction, CRC calculation, game-folder preparation, save-folder
sync and companion layout/CSS parsing run on a session-owned worker in SS.
The UI displays a loading message and animated indicator while that work runs;
Menu can cancel it. Compose state and the
live companion tree are installed on the main thread. The core retains its
existing emulation thread. No second player Activity or Presentation is launched.

Closing a session cancels queued startup and ignores late results. Pause, focus
loss, rotation and opening Menu release touch state; an interrupted gesture
cannot reassert keys until the next fresh touch. Tests cover worker failures,
slow startup, late delivery, overlapping physical/touch holds and pointer IDs.
Native core sessions are serialized: a game closed while loading cannot restart
its loop afterward, and a replacement game waits for the old core owner to exit.

## Build and installation

The regular Plus build remains the default:

```sh
./gradlew :app:assembleRelease
./gradlew -PsingleScreen=true :app:testDebugUnitTest
./gradlew -PsingleScreen=true :app:assembleRelease
```

Use JDK 21 and the SDK/NDK in [BUILDING](BUILDING.md). Release signing uses the
same existing four `PLUS_*` environment variables. Without them the APK is unsigned.

| Edition | Application ID | Version | Local APK name |
|---|---|---|---|
| Plus | `com.khumam.gbaduo.plus` | `0.1.8-plus.2` | `GBA-Duo-Plus-0.1.8-plus.2.apk` |
| Plus SS | `com.khumam.gbaduo.plus.ss` | `0.1.8-plus.2-ss` | `GBA-Duo-Plus-SS-0.1.8-plus.2.apk` |

The apps install side by side and have separate private saves, companion packs,
settings, sign-ins and folder permissions. Import an existing companion ZIP or
create one with the same automatic Builder flow. There is no automatic migration.

Audit a signed SS APK with `tools/package-apk.ps1 -Edition SS -ApkPath <apk>`.
It keeps the regular and SS checksum entries in the same local release directory.
Both editions are built from the same 0.1.8-plus.2 source and distributed together.
The older Plus.4 SS asset was added after its original tag; that historical release
retains its original APKs and separately documented SS source commit.

## Verification status

Automated geometry, input, worker and Android host-render tests are provided.
Preview video is a synthetic pattern, not a native emulation or GPU test. Host
rendering does not establish real-phone startup, audio, GPU behavior or physical
multi-touch acceptance across supported devices.

The final geometry was approved after host-render previews. The SS Debug suite
passes 424 tests, including equal-width tablet/Fold stacks, invariant A/B geometry,
multi-touch, rotation cleanup, startup cancellation and quick-action callbacks.
Release build, signature and alignment checks pass.

Earlier SS builds ran FE7 with a companion on a Motorola Razr Ultra 2025,
including repeated launches, native input-bit checks and six rotations without
recreating the core. An adaptive build also passed Menu cancellation, Swap without
reloading, light/dark and fast-forward toggles, and native quick save/load frame
restoration. The final geometry has host-render/input coverage and has not been
rerun on the phone. A ZIP source on that phone took 12–26 seconds to read. Loading
feedback and cancellation are implemented; fast ROM opening has not been established.

## Origin

SS is maintained by levi-soft on **GBA Duo Plus**, based on **GBA Duo v0.1.7 by
khumam and contributors**. It uses the existing emulator, companion engine and
Builder integration. Original authorship and GPL/third-party notices are retained;
see [credits](../CREDITS.md).
