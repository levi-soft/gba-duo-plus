# Read-only Studio Connection

Open **Settings > Studio Connection** and choose **Off** or **On**. The choice is
saved for this app edition and applies when the next game session starts. It
defaults to **Off** in release and debug builds; debug builds do not override a
saved Off choice. Reopen the game after changing the setting. The quick menu no
longer contains a separate connection toggle.

When On, the read-only connection runs only while a game session is open, including
when that session is paused, and closes when the session ends. This setting does
not add a LAN listener or change the existing abstract ADB transport. Plus and
Plus SS have separate saved settings, but share the socket name below: enable
the connection in only one running edition at a time.

The endpoint is Android's abstract Unix socket **`gbaduo-plus-studio`**. A desktop tool can reach it through USB debugging/ADB:

```sh
adb -s DEVICE_SERIAL forward tcp:8787 localabstract:gbaduo-plus-studio
```

Use a trusted PC and accept its USB-debugging authorization on the device. Do not expose the forwarded port or the desktop Studio server to a network. The app itself does not open a LAN listener.

## Protocol 1

One newline-terminated UTF-8 JSON request per connection. Requests have an `id` and `command`; responses echo the ID:

```json
{"protocol":1,"id":"example","ok":true,"result":{}}
```

Commands:

| Command | Result |
|---|---|
| `info` | ROM game code/CRC metadata, session identity, frame, pause state, platform and active pack identity |
| `read` | Base64 data for requested GBA RAM ranges |
| `capture` | GBA EWRAM 256 KiB and IWRAM 32 KiB from one emulator-frame boundary |
| `data` | Latest companion/Lua output and its associated data frame, or null |
| `inspect` | Bounded read-only GBA ROM, work RAM, palette, VRAM and OAM ranges (bridge revision 2) |

Revision 2 retains the `protocol: 1` wire envelope for existing Studio clients.
`info` now includes `bridgeVersion: 2`, the actual `romSize` and a `capabilities`
object. Studio must check `capabilities.inspect` before offering ROM inspection.
Older bridges keep their existing RAM workflow and report no inspection support.

`inspect` accepts 1–64 ranges, each 1–4096 bytes, at most 64 KiB in total.
All ranges are validated before any is read. Valid areas are EWRAM, IWRAM,
palette `[0x05000000,0x05000400)`, VRAM `[0x06000000,0x06018000)`,
OAM `[0x07000000,0x07000400)` and ROM from `0x08000000` to the actual loaded
ROM end (at most 32 MiB). ROM mirroring beyond that file, I/O registers, BIOS,
save memory, writes and arbitrary execution are unavailable. The native reader
repeats these checks and runs at the existing emulator-frame boundary.

Studio can use these bytes to preview sequential 4bpp tiles, a 16-color palette
and GBA LZ77 ROM data. Its generated pack stores source descriptors and sandboxed
`gfx.decode` calls, rather than bundling decoded artwork. This is inspection and
authoring support; it does not automatically identify every game's portrait or
sprite layout.

`read` accepts **1-64 ranges**, no more than **64 KiB total**. Valid GBA regions are EWRAM `[0x02000000,0x02040000)` and IWRAM `[0x03000000,0x03008000)`. GBA-only commands reject other platforms. Requests are bounded and serviced on the emulation thread between frames, including paused servicing. CRC32 is identity metadata, not an authentication secret or a requirement to discover the connection.

The bridge has **no write-memory, execute-memory, login-token or achievement-award command**. Account credentials do not enter its snapshots. Game unit navigation is a separate, validated companion action, not a bridge RAM-write capability.

## Studio integration

Get [GBA Duo Studio](https://github.com/levi-soft/gba-duo-studio), the separate visual pack editor. Follow its [English user guide](https://github.com/levi-soft/gba-duo-studio/blob/main/docs/USER-GUIDE.md) or [Vietnamese user guide](https://github.com/levi-soft/gba-duo-studio/blob/main/docs/USER-GUIDE.vi.md) for installation and USB/ADB connection steps.

Compatible GBA Duo Studio builds discover the device, establish forwarding and read this protocol. They can inspect raw memory and use the selected companion's actual runtime data for preview. The editor and bridge do not automatically discover every game's field addresses or guarantee hot reload. Save/export/import a pack using the normal workflow when changing it.

Official GBA Duo v0.1.9 does not expose this Plus bridge. Ordinary native-format packs do not require it; it is optional authoring tooling. The Studio repository is a separate project and is not bundled in this source publication.
