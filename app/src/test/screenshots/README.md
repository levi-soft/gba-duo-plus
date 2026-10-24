# Companion screenshot references

The four root PNGs are retained upstream references. Their recording host is
unknown. Do not replace them to accept a rendering difference on another host.

`windows/party-css-detail.png` is the Windows native-graphics reference for the
detail-page case. All other cases use the root references on every host. A
missing selected reference fails the test; there is no fallback to the actual
image. Both profiles use the unchanged channel tolerance of 40 and changed-pixel
tolerance of 0.002.

## Windows provenance (2026-10-24)

- Windows x86-64, Temurin JDK 21.0.12+8, Robolectric 4.14.1, native runtime
  `nativeruntime-dist-compat` 1.0.16, Android SDK 34, Compose BOM 2024.10.01.
- The original detail reference differs in 9,090 pixels (668 allowed). Text
  rasterization differs across the page; text advances also change the width
  and placement of the weakness/resistance chips. Other layout geometry and
  the three other screenshot cases match within the existing tolerance.
- Explicit `en-rUS` configuration leaves the difference unchanged.
- To distinguish a renderer regression from a host baseline difference, the
  original `CompanionScreen.kt` from the reference-producing commit
  `5adb992b670b52b1035e3398a7301449d3be1287` was compiled as a temporary test-only
  renderer. Only its entry point/paint helper were renamed and exhaustive
  `Length.Calc` branches were added to compile against the current model; the
  example uses none of those branches. The current layout/model and the same
  Robolectric root capture were used. That original renderer produced exactly
  the same 9,090-pixel difference and an identical actual PNG SHA-256. The
  temporary renderer was removed after the comparison.
- Windows reference/actual SHA-256:
  `8cf8398db018f2b16b58346e32a82222c4dfa2c9b82e880ca9060b9f33a8d268`.

This rules out the changes to `CompanionScreen.kt` as the source of this
difference under the tested current layout/model/capture setup. A host text
rendering difference is the supported inference, not a comparison of every
historical renderer/model combination. It does not establish which OS recorded
the upstream golden or certify rendering on a physical device. No production
renderer, root reference, or tolerance changed.

## Running and recording

Run all four cases from the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*PackScreenshotTest'
```

After reviewing an intentional visual change, `-PrecordScreenshots` records
into the same reference path selected by verification. Record and review the
other-host profile separately when its rendering changes; a Windows recording
must not overwrite the upstream detail reference.
