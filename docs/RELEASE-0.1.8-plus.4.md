# GBA Duo Plus 0.1.8-plus.4

Plus SS adds a third **Swap** mode for playing without a companion screen.
The release includes both Plus and Plus SS, based on official GBA Duo v0.1.8.

- Swap cycles through **game first → companion first → game only → game first**.
- Game only hides the companion frame and centres a larger game viewport with
  the existing GBA controls. It also works without an assigned companion pack.
- Switching keeps the existing game surface and companion views, releases held
  inputs and remembers the selected mode when the game is reopened. Existing
  saved two-screen orders migrate automatically.
- Portrait keeps the camera/notch top inset. Phone, tablet and Fold layouts
  preserve GBA and GB/GBC aspect ratios.
- The regular dual-screen Plus behavior is unchanged. Both editions retain the
  square-default SteamGridDB picker introduced in Plus.3.

## Downloads and upgrading

Both editions use Android versionCode **17** and the existing signing certificate:

- `GBA-Duo-Plus-0.1.8-plus.4.apk`: dual-screen edition, version `0.1.8-plus.4`.
- `GBA-Duo-Plus-SS-0.1.8-plus.4.apk`: Single Screen edition, version `0.1.8-plus.4-ss`.
- `SHA256SUMS.txt`: signed APK checksums.

Install over the matching Plus/SS edition. No uninstall or data reset is needed.
Builder remains **1.1.5**; companion packs are unchanged.

## Validation

The feature passed all 537 Debug unit/host-render tests in both build
configurations, and both Release APKs built. Geometry covers portrait cutouts,
phone landscape, tablet/Fold windows and both game aspects.

On Thor, the three-state cycle kept the same native session, all ten GBA controls
passed press/release checks, and reopening retained game only. This real-device
feature check used a game without a companion pack. Portrait rotation and an
assigned live pack were covered by host tests, not verified on Thor during this
update. Thor's rotation settings were restored after testing.
