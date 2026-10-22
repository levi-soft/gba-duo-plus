# Companion-driven unit cursor focus

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

## Supported profiles

The current source includes whitelisted profiles for FE7 US, FE8 Europe and FE8 US/Australia, plus specific previously inspected modified-ROM checksums. Exact game-code/CRC matches are enforced by `CursorProfile.forRom` in `Fe7CursorNavigator.kt`.

This is not support for every ROM hack, every regional revision or arbitrary games. Published checksum/profile constants are emulator compatibility metadata; the repository does not include the corresponding ROMs or private companion packs.

Portable packs should retain local selection when the host function is absent or the ROM is unsupported. Official GBA Duo v0.1.6 does not gain this action merely by importing a pack.

Automated tests cover convergence, release/cancellation, invalid states and all deployment banks. Historical Thor checks covered selected player/enemy interactions. They are not fresh proof of every profile/map/green-ally/camera-scroll case for this publication.
