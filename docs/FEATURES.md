# Plus 0.1.7 compared with official GBA Duo v0.1.7

Baseline: official tag `v0.1.7`, commit `6aa3823b7c4e4c8478b74fe24cf837a19224fc23`.
This is a source comparison; runtime validation limits are in [VALIDATION](VALIDATION.md).

| Capability | Official 0.1.7 | Upgraded Plus 0.1.7 | Integration decision |
|---|---|---|---|
| mGBA GB/GBC/GBA and dual-screen placement | Present | Retained | Keep upstream core and platform support. |
| Pixel library, cartridge carousel, readable game titles | Present | Adopted with Plus identity | Keep the new library, title cleanup and controller navigation. |
| Pixel Settings and folder screens | Present | Adopted | Keep upstream UI, original attribution and Plus links. |
| External save folder and conflict-preserving synchronization | Present | Adopted, including Plus RA state sidecars | Keep upstream sync; copy `slotN.ss.ra` alongside states. |
| RetroAchievements Softcore | Present | Official process-wide RA runtime with Plus extensions | Use the official login, ROM identification, event and reconnect pipeline. Add Keystore token protection, achievement list and progress save/load on that same client. |
| Achievement popups | Present | Official queued popups with Plus position/opacity settings | Use upstream popup design/queue; apply bottom-right position and 70% panel background. |
| Swap A/B remembered | Session toggle only | Persisted across sessions | Retain Plus preferences. |
| Pixel shaders | Original filters and Scale2x | xBRZ, HQ2x, ScaleFX and selected original filters | Retain Plus shader pipelines; Scale2x stays hidden. Shader persistence was already upstream. |
| Launcher intents / Daijisho custom player | Present | Retained, with Plus path aliases and session handling | Keep the upstream launch entry point and Plus compatibility additions. No bundled Daijisho profile. |
| Companion JSON/CSS/Lua packs | Present | Retained | Native format remains shared. Feature availability still depends on host version. |
| `gfx.decode`, `gfx.sprite`, `gfx.tilemap`, LZ77 | Present | Adopted | Keep expanded ROM-derived graphics, limits and image cache. No game art is bundled here. |
| Responsive CSS / math / grid tracks | Present | Adopted with Plus indexed selector/cache optimization | Keep `@media`, `calc`, `min`, `max`, `clamp`, auto-fill/fit and minmax; apply media predicates before using cached styles. |
| Bounded Lua / native work | 1M work budget and 25ms per call | Adopted unchanged | Keep load budget 5M/250ms and per-call limits; no silent increase for private packs. |
| Permission-gated pack RAM writes | Present | Adopted | Opt-in setting and per-pack/game consent, bounded work RAM writes, safety state. Plus refuses writes if that safety save fails. |
| Companion control | No Plus input API | Extended | Tap-scoped standard-button pulses for any matching game; FE map focus uses exact-ROM pack metadata, bounded D-pad navigation and physical-input cancellation. |
| Failed Lua updates | No Plus retention | Last complete snapshot retained, marked stale, error preserved; cleared on pack/save-state change | Keep per-tick sandbox limits; do not replay one-shot UI actions. |
| USB Studio Connection | No Plus bridge | Expanded | Per-session read-only RAM, ROM and graphics inspection with capability discovery. Abstract socket forwarded with ADB; pack RAM-write permission does not authorize bridge writes. |
| Automatic companion creation | Not part of this pinned baseline | Plus-only addition | Discover exact-ROM profiles, download/cache a verified builder, create locally, then install/assign. Manual ZIP import remains available. Replacement/removal path is documented separately. |
| ROM artwork | Drawn cartridge placeholders | Plus-only selectable square covers | Manual image import, SteamGridDB gallery, default no-account Libretro lookup and missing-artwork queue; local storage with original cartridge fallback. |
| Hardcore / hash override / translation | No Hardcore in this release | Not included | Keep Plus's existing scope. Actual ROM identity is used for achievements. |

## Upgrade choices

The new upstream runtime and graphics/CSS APIs are the foundation. Existing Plus rendering fixes,
input coordination, shader presets, launcher handling and desktop bridge remain on top.
The RA dependency is the same rcheevos 12.5.0 in both implementations. The dependency now uses
upstream's vendored layout rather than the former Plus submodule.

RetroAchievements now uses the official 0.1.7 native/Kotlin integration and process-wide client.
Plus's former NativeAchievements/RetroAchievementsSession implementation is removed.
Missing Plus features extend the official client: encrypted Keystore token storage, achievement list,
RA progress serialization with save states, bounded HTTPS requests and serialized native access.
The official pixel popup design/queue is retained with the user's bottom-right/70% presentation.
No plain-text token fallback is written when the Keystore is unavailable.

Private companion packs, ROMs, save files, captures and signing keys are not part of this repository.
