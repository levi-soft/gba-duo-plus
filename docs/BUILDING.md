# Building GBA Duo Plus

## Prerequisites

- Git with submodule support.
- **JDK 21** to run the Gradle wrapper. Source/JVM bytecode targets Java 17, which is separate from the build runtime requirement.
- Android SDK platform **35** and Android command-line tools.
- NDK **27.2.12479018** and CMake **3.22.1**.
- Internet access to resolve Gradle/Android dependencies and clone submodules on the first build.

The repository pins Gradle **8.10.2**, Android Gradle Plugin **8.7.3**, Kotlin/Compose plugin **2.0.21** and the native dependency revisions below.

```sh
git clone --recurse-submodules https://github.com/levi-soft/gba-duo-plus.git
cd gba-duo-plus
git submodule update --init --recursive
```

Set `JAVA_HOME` to your JDK 21 installation. Configure the SDK through `ANDROID_HOME`/`ANDROID_SDK_ROOT` or an untracked `local.properties` with `sdk.dir`. Do not copy another developer's machine paths.

Install missing SDK packages with Android Studio or `sdkmanager`:

```sh
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018" "cmake;3.22.1"
```

## Build variants

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Use `gradlew.bat` on Windows. Debug builds install as `com.khumam.gbaduo.plus.debug`; release builds use `com.khumam.gbaduo.plus`.

With no signing configuration, `assembleRelease` produces an **unsigned** APK, normally `app/build/outputs/apk/release/app-release-unsigned.apk`. It is not ready to install until signed. The project does not silently use a maintainer's local Android debug key for public releases.

## Release signing

Provide all four environment variables to the build process:

| Variable | Meaning |
|---|---|
| `PLUS_KEYSTORE_PATH` | Path to your private signing keystore |
| `PLUS_STORE_PASSWORD` | Keystore password |
| `PLUS_KEY_ALIAS` | Signing alias |
| `PLUS_KEY_PASSWORD` | Key password |

If only some are set, configuration fails with a message naming the requirement, never the values. Store keys and credentials outside the repository. Do not put passwords in command-line examples, committed Gradle properties or issue reports.

An existing APK can update another installation only when Android's package/signature requirements match. Historical local Plus builds used a local debug key; this source publication does not distribute that key or automatically convert those installations. Back up app data before uninstalling or switching signing identities.

The optional `tools/package-apk.ps1` audits and copies an already signed APK into an ignored local release folder. It does not upload a GitHub release.

## Tests

The full publication gate is defined in the [CI workflow](../.github/workflows/android.yml).
This core/features smoke command includes the automatic-builder and artwork suites:

```sh
./gradlew :app:testReleaseUnitTest --tests '*PlusFeatureContractTest' --tests '*BridgeProtocolTest' --tests '*Fe7CursorNavigatorTest' --tests '*InputMapperTest' --tests '*ShaderPresetTest' --tests '*LaunchIntentResolverTest' --tests '*ExternalRomPathTest' --tests '*RomArchiveTest' --tests '*LatestCompanionSnapshotTest' --tests '*CssRasterGridTest' --tests '*AutomaticBuilderCoreTest' --tests '*BuilderRomSourceTest' --tests '*BuilderLanguageTest' --tests '*Artwork*Test'
```

Use the release test variant for the publication gate and its application-ID contract. Run the full host suite, including the four Compose screenshot cases, with `:app:testDebugUnitTest`; the debug variant provides the Compose test Activity manifest. The final 2026-10-24 pre-commit Windows run passed all 364 debug tests and 197 tests selected by the CI Release gate. Screenshot references account for Windows native text rendering while retaining the original references and tolerances; see [reference provenance](../app/src/test/screenshots/README.md) and [VALIDATION](VALIDATION.md).

CI is configured to build an unsigned release and run the focused gate. Device installation, real achievement awards, controller behavior and GPU performance require separate testing.

## Native dependency revisions

Plus-only Kotlin, asset and resource directories are wired into the main source
set from `plus/src/main`; do not build them as a second application or copy them
back into `app/src/main`. Run `node tools/upstream-boundary.mjs check` before the
Gradle gates. See [the extension boundary](../plus/README.md) for maintenance.

| Component | Source location | Pinned version/revision |
|---|---|---|
| mGBA | `engine/mgba` submodule | `26b7884bc25a5933960f3cdcd98bac1ae14d42e2` (`0.10.5`) |
| rcheevos | Vendored `engine/rcheevos` source | `1433173220a7eaede6a9ed7a18e94117be1821e0` (`v12.5.0`) |
| Lua | Vendored `engine/lua` source | `5.4.7` |

Native CMake builds the GB/GBA cores, the sandboxed Lua host and rcheevos; optional mGBA frontends/dependencies are disabled. Preserve licenses and file headers if changing any component. A source ZIP downloaded from GitHub does not embed submodule contents: clone recursively or initialize the pinned mGBA submodule before building. rcheevos is now vendored as in official 0.1.7.
