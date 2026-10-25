# Companion input and cursor navigation

## Standard buttons for any game

`input.press(buttonOrButtons, frames)` is available inside explicit Lua
`actions.*` taps for any matching companion/game, including games outside FE:

```lua
function actions.confirm()
  if input and input.press then input.press("A") end
end
function actions.shortcut()
  if input and input.press then input.press({"R", "A"}, 2) end
end
```

Buttons are `A`, `B`, `L`, `R`, `SELECT`, `START`, `UP`, `DOWN`, `LEFT`, `RIGHT`.
Frames defaults to 1 and must be an integer from 1 to 30. A table is a
simultaneous chord, not a sequence. Unknown buttons and opposite directions are
rejected. The last input request in an action wins. An action error discards its
input. No memory-write permission is required.

The host releases buttons after the requested number of emulated frames.
Physical input, pause, save/load and pack changes cancel them. Input cannot run
from `init()` or `update()`. For other supported consoles, use their subset of
standard buttons. A pack author still defines what each UI action means for the
game; Plus does not automatically understand arbitrary menus or cursors.

## FE map focus

An optional Lua tap action can call:

```lua
function actions.focusUnit(id)
  if input and input.focusUnit then
    input.focusUnit(id)
  end
end
```

Use the actual action name configured by your pack. The host function is available while executing an `actions.*` call; it is not an unrestricted update/init memory-control API.

The request queues a deployment ID. The emulator validates ROM identity, map dimensions, unit visibility/aliveness, occupancy, phase and the idle cursor process. It then sends bounded D-pad pulses so the game retains ownership of cursor animation, camera scrolling and hover behavior.

There is **no RAM-coordinate write and no automatic A/B confirmation**. Physical input, pause/save/load, pack or target changes, phase/chapter/turn changes, busy menus/movement/events, invalid identity and the bounded frame timeout cancel navigation. A new valid tap can replace the target.

## Pack-owned cursor contracts

New packs declare an optional `cursorNavigation` object in `manifest.json`:

```json
{
  "version": 1, "kind": "fe-map",
  "cursor": "0x0202AA1C", "size": "0x0202D200",
  "chapter": "0x0202AA56", "turn": "0x0202AA58", "bm": "0x0202AA08",
  "procs": "0x02023CC4", "playerScript": "0x085C56A8", "idlePc": "0x085C5718",
  "blue": "0x0202AB78", "green": "0x0202CAF8", "red": "0x0202BCE8",
  "unitPosition": 14, "unitHp": 17, "unitStateBytes": 2,
  "unavailableMask": "0x002C"
}
```

The manifest must also declare a nonempty exact game-code/CRC match. Addresses
are hex strings; offsets, widths and schema version are integers. Unknown fields,
unsupported versions/kinds and invalid ranges/alignment/layouts reject the pack.
Malformed metadata never silently falls back to a host profile.

`fe-map` version 1 uses 64 process records of 108 bytes, 72-byte unit slots and
deployment banks 1–62 / 65–84 / 129–178. `size` points at u16 width/height followed
by the occupancy row pointer; `cursor` points at u16 x/y. `chapter` is followed by
the phase byte; `turn` is u16. `bm+1`, `bm+2` and the matched proc's +0x28 byte must
be zero. The proc must match `playerScript` and `idlePc` exactly. Unit +0/+4/+11/+12
retain the FE character/class pointer, deployment ID and state meanings. Enemy
fog uses bit 0x200. Offsets and 16/32-bit state width are supplied by the pack.

The builder resolves reusable presets at build time for all three Vietnamese
and five English profiles. Packs contain flat data. The emulator resolves a
contract once per pack change; it does not parse configuration during frames.
Compatible ROMs can be added through verified pack metadata without an APK
whitelist change. A different navigation algorithm still needs host support;
non-FE games can use the independent `input.press` API.

Packs without metadata keep the original exact-ROM host profiles as a legacy
fallback. Older Plus APKs ignore the new optional field and retain their previous
limits. Official GBA Duo does not acquire these APIs by importing a pack, so
portable scripts should retain the existence checks shown above.

Automated tests cover schema/identity validation, unlisted ROM contracts,
navigation across deployment banks, cancellation and generic button pulses. The
previous FE6 test APKs were accepted on Thor for Map taps in FE6 T-EN and both
Ember variants. Live acceptance of this new host path remains pending; historical
acceptance is not a fresh test of the new APK. No ROMs/private assets are public.

## Failed update retention in Plus 0.1.7-plus.4

When Lua update fails, the last complete view is retained without replaying its `ui` actions. `_scriptStale=true` and `_scriptError` identify the failure; `_scriptDataFrame` records the original successful frame, while `_frameCounter` remains current. Success replaces the retained data, including a confirmed empty selection. Pack changes and save-state requests clear the cache. Studio data includes these diagnostics; retaining the view does not raise sandbox budgets or claim data is fresh.
