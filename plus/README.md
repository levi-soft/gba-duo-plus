# Plus extension boundary

The official baseline is pinned in [upstream.json](upstream.json). Its Git commit
is immutable; the application checkout is the integrated product, not an untouched
official checkout.

- `plus/src/main/java`: Plus-only implementations (Studio transport, cursor
  navigation, shader runner, RA credential/image helpers, launcher helpers and
  rendering utilities).
- `plus/src/main/assets` and `res`: Plus-only shaders and resources.
- `app/src/main`: upstream-derived application and explicitly documented
  integration edits. Official RA remains the client implementation.
- `upstream.json`: baseline identity and the reason for every changed upstream
  application file. This is a review inventory, not an automatically applied patch.

Gradle includes the extension directories in the application's main source set.
This is a source module boundary, **not a separate Android library/AAR**: existing
host types and Kotlin `internal` APIs share one compilation unit. Packages and JNI
names remain unchanged. Building does not compile a second copy of the emulator.

New standalone features belong here. Do not copy upstream classes here to hide
changes from review. Small host calls connect lifecycle/input/rendering to these
implementations. Some existing RA, renderer and native changes still require
direct integration; they are visible in the inventory and Git diff.

`player/singlescreen/` owns SS phone geometry, touch regions, pointer tracking,
embedded companion hosting and cancellable startup I/O. Only the build identity,
Player lifecycle/input calls and parsed companion installation are integrated
into the existing host. See [Single Screen](../docs/SINGLE-SCREEN.md).

`companion/builder/` owns automatic companion discovery, the verified offline
builder cache, local WebView execution, ROM staging and safe installation.
The existing Get from the web catalog delegates exact-ROM creation through a
source-bound adapter and registers the private Activity. Ready-made ZIP downloads
and update checks retain the official implementations.
Manual companion ZIP import is retained. Removal/replacement when upstream
provides equivalent support is documented in [AUTOMATIC-COMPANIONS](../docs/AUTOMATIC-COMPANIONS.md).

`artwork/` owns legacy cover migration, Libretro/manual image selection, encrypted credentials and missing-cover lookup. Selected covers use the official shared artwork store and cartridge rendering. The official folder matcher, including Cocoon names, remains inherited. The official carousel uses optional Plus actions without a second cover renderer. See [ARTWORK](../docs/ARTWORK.md) for provider setup and removal steps.

## Review and upgrade

```sh
node tools/upstream-boundary.mjs check
node tools/upstream-boundary.mjs report
node --test tools/upstream-boundary.test.mjs
```

An optional pristine official checkout can coexist beside Plus:

```sh
git worktree add --detach ../GBA-Duo-Official-Reference 61582fcb5e07d98b19675e75e9bde532a7c68c4c
git -C ../GBA-Duo-Official-Reference status --short
```

Keep that checkout read-only by convention. It is for inspection/build comparison,
is not bundled into the APK, and does not include Plus features. Do not edit it or
initialize another copy of private companion data there.

For the next release:

1. Fetch the official repository and verify the desired tag/commit.
2. Before merging, run `node tools/upstream-boundary.mjs compare <candidate>`.
   This lists host integration files also changed upstream; inspect the entire
   upstream diff as well, because behavioral changes need not touch those files.
3. Merge the candidate on an upgrade branch. Prefer completed official features;
   remove superseded Plus changes and inventory entries.
4. Update the pinned commit/tag and review every integration reason. Run `check`
   and inspect `git diff <new-baseline> -- app/src/main plus app/build.gradle.kts`.
5. Run the JVM suite, native harness and release build, then check game launch,
   physical input, Studio opt-in/close, shader selection/context recreation,
   save-state/RA behavior and companion layouts on an actual device.

The checker prevents undocumented application edits and misplaced new extension
files; it does not certify runtime compatibility or automatically resolve patches.
Use Git diff to export patches for review if needed. Do not maintain a second
hand-edited patch directory that can drift from the source actually built.
