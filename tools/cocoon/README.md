# Cocoon player profiles

These authored player additions target the public Cocoon platform schema. They
use the same `VIEW` / `{file.uri}` launch protocol as the official GBADuo player,
with the existing Plus and Plus SS package IDs. An APK cannot register itself in
Cocoon's external player catalogue merely by exporting a launch Activity.

Run from the repository root:

```sh
node --test tools/cocoon/build-profiles.test.mjs
node tools/cocoon/build-profiles.mjs
```

The generator verifies the Git blob hashes of three platform definitions pinned
in `profiles.lock.json` before producing three local JSON files. Each file keeps
the upstream platform ID and metadata, but contains only the two new player
definitions. It does not ship default player choices, ROM folders, saves or
user library data, and never imports anything into Cocoon automatically.

The default output is ignored `reports/cocoon-import-profiles-0.1.9/`. Existing
output directories are refused; `--out <new-directory>` creates another output.
Follow [the Vietnamese setup guide](../../docs/COCOON-SETUP.vi.md).

Source: [CocoonFE gamenative platform definitions](https://github.com/inssekt/CocoonFE/tree/3c2b6785a024e7ae73d4bbb2b331ff7519a9b7e1/platforms).
The `main` branch has older platform revisions and does not carry the official
GBADuo entries present on `gamenative`. Cocoon's public repository has no LICENSE
file; no upstream metadata is represented here as original Plus code or
relicensed by this project. The new generator/player definitions use this
repository's source license.

Cocoon 3.11's APK import path was inspected. The importer reads existing players,
combines the imported entries with them by player ID, and applies the platform
refresh through its own engine. No direct database write or modified Cocoon APK
is used.

On 2026-10-31 all three profiles were imported through Cocoon 3.11's official UI
on AYN Thor. GB/GBC/GBA each showed Plus, Plus SS and the official GBADuo player.
The existing GBA default stayed GBADuo after import; normal Plus was then selected
and saved explicitly, and survived Cocoon's Restart action. Existing GBA/NDS/
Switch folders and NDS/Switch defaults were retained; all four existing exported
gamelists were byte-identical before and after. GB/GBC were newly enabled and
Cocoon automatically chose the first installed player, without adding ROM folders.
This verifies local import/selection/persistence. A separate final regular APK
check cold-launched Maiden twice through Cocoon's own UI, with the correct CRC
and companion assignment; the title screen rendered before any Resume action.
See [validation limits](../../docs/VALIDATION.md).
The generator's `deviceUiVerified: false` describes that generation invocation,
which does not inspect or modify a device.
