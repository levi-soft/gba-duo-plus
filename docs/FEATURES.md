# Features compared with official GBA Duo v0.1.6

This comparison is against **tag v0.1.6**, commit `0c201e9faa224eb1527651ac0ca3e12a3ce0fe5c`, not a claim about whatever the newest upstream release may contain in the future.

| Capability | Official v0.1.6 baseline | Plus 0.1.2 |
|---|---|---|
| GBA/GB/GBC emulation | mGBA core | Retained; not a new Plus core |
| Two-screen game/companion placement | Present | Retained with additional companion sizing/placement fixes |
| JSON/CSS/Lua companion ZIPs | Present | Retained; no mandatory Plus-only pack format |
| Pages, tabs, buttons, lists, bindings | Present | Retained with rendering/snapshot fixes for complex layouts |
| Save RAM and nine save-state slots | Present | Retained |
| Fast-forward and controller input | Present | Retained, with input/cursor cancellation coordination |
| ROM folders, Settings and About | Present | Retained; Plus identity and attribution added |
| External VIEW LaunchActivity | Introduced upstream | Adopted; additional input aliases, validation and lifecycle handling retained |
| Shader selection saved across sessions | Present | Retained, including new preset IDs |
| Swap A/B | Runtime toggle | Toggle persisted across sessions |
| Off / GBA Color / LCD Grid / Scanlines | Present | Retained |
| Scale2x | Present | Not exposed in the Plus preset picker |
| xBRZ / HQ2x / ScaleFX | Not present in this baseline | Added GLES3 shader pipelines |
| RetroAchievements client | Not present in this baseline | Added rcheevos integration, login, achievement list and notifications |
| Hardcore achievements | Not present | Not implemented/enabled |
| Companion requests to focus a game unit | Not present | Added for explicitly whitelisted profiles using D-pad pulses |
| Desktop read-only Studio connection | Not present | Added; release builds require per-session opt-in |
| ROM-hash identity override | Not present | Not included |
| Translation service/model integration | Not present | Not included |

## Integration details

**RetroAchievements:** Actual ROM identification is performed through rcheevos. Login credentials are not companion fields. Automatic notices distinguish signed-in state, recognized games and unsupported hashes. Badge fetching is separate from the emulator/UI threads. This integration does not make a ROM recognized by the service merely because a companion works with it.

**Rendering:** Layout/raster-grid/aspect-fit changes and retention of the latest valid companion snapshot address observed issues in complex packs. These are native host changes, not proprietary assets or a requirement to embed a particular game in the emulator.

**Shaders:** Algorithms come from the credited upstream shader authors. Plus supplies GLES3 loading, multipass render targets, sampler bindings, presentation and saved-selection integration. ScaleFX needs suitable floating-point render-target support and may be expensive on some GPUs.

**Launchers:** Daijisho can be configured to launch Plus through Android intents. This is configurable frontend compatibility, not a bundled Daijisho player profile or official integration partnership. Frontend achievement metadata and Plus's runtime achievement client are distinct systems.

**Cursor focus:** A companion can request a deployment ID. The host checks the ROM/game state and supplies bounded directional input. It does not write cursor coordinates to RAM or automatically press A/B. Unsupported games retain ordinary local companion selection without a universal host cursor feature.

**Studio:** Release users explicitly enable the connection for the current game session. Debug builds also expose the development bridge while a game runs. The abstract Unix socket is not a network server; PC tools forward it through ADB.

See [VALIDATION](VALIDATION.md) for the evidence boundary. Presence in source or a passing host test is not the same as live-device coverage for every transition.
