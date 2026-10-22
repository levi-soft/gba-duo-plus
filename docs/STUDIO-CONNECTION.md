# Read-only Studio Connection

In a **release** game session, open the quick menu and enable **Studio Connection**. This is an explicit per-session opt-in and is not a permanently enabled LAN/debug service. Debug builds also enable the development connection while a game runs.

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

`read` accepts **1-64 ranges**, no more than **64 KiB total**. Valid GBA regions are EWRAM `[0x02000000,0x02040000)` and IWRAM `[0x03000000,0x03008000)`. GBA-only commands reject other platforms. Requests are bounded and serviced on the emulation thread between frames, including paused servicing. CRC32 is identity metadata, not an authentication secret or a requirement to discover the connection.

The bridge has **no write-memory, execute-memory, login-token or achievement-award command**. Account credentials do not enter its snapshots. Game unit navigation is a separate, validated companion action, not a bridge RAM-write capability.

## Studio integration

Get [GBA Duo Studio](https://github.com/levi-soft/gba-duo-studio), the separate visual pack editor. Follow its [English user guide](https://github.com/levi-soft/gba-duo-studio/blob/main/docs/USER-GUIDE.md) or [Vietnamese user guide](https://github.com/levi-soft/gba-duo-studio/blob/main/docs/USER-GUIDE.vi.md) for installation and USB/ADB connection steps.

Compatible GBA Duo Studio builds discover the device, establish forwarding and read this protocol. They can inspect raw memory and use the selected companion's actual runtime data for preview. The editor and bridge do not automatically discover every game's field addresses or guarantee hot reload. Save/export/import a pack using the normal workflow when changing it.

Official GBA Duo v0.1.6 does not expose this Plus bridge. Ordinary native-format packs do not require it; it is optional authoring tooling. The Studio repository is a separate project and is not bundled in this source publication.
