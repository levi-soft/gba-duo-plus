# GBA Duo Plus 0.1.8-plus.3

SteamGridDB now opens with **Square (default)** covers. The existing official picker offers **Square / Portrait / All shapes**, and changing the shape reloads the selected game's covers.

- Square requests 512×512 and 1024×1024 images. Automatic SteamGridDB scraping also defaults to square.
- If no square cover exists, the picker asks you to choose another shape. It does not silently fall back to portrait.
- Each newly opened picker starts with Square; rotating the device preserves the current choice.
- Thumbnails retain their image proportions. Responses from earlier searches/shape selections cannot replace newer results.
- The official cartridge renderer, shared artwork storage and single SteamGridDB key editor are retained.

Both editions use Android versionCode 16:

- `GBA-Duo-Plus-0.1.8-plus.3.apk`: dual-screen edition.
- `GBA-Duo-Plus-SS-0.1.8-plus.3.apk`: Single Screen edition.
- `SHA256SUMS.txt`: signed APK checksums.

Install over the matching Plus/SS edition. Builder remains 1.1.5; companion packs are unchanged. No uninstall or data reset is needed.

## Same-version SS update: three-state Swap

The SS APK is refreshed within this release, retaining `0.1.8-plus.3-ss` and
Android versionCode 16. The regular Plus APK is unchanged.

- Swap cycles through **game first → companion first → game only → game first**.
- Game only hides the companion frame and centres a larger game viewport with
  the existing GBA controls. It also works without an assigned companion pack.
- Switching keeps the existing game surface and companion views, releases held
  inputs and remembers the selected mode when the game is reopened.
- Portrait keeps the camera/notch top inset. Phone, tablet and Fold layouts
  preserve GBA and GB/GBC aspect ratios.

Validation: all 537 Debug unit/host-render tests passed in both build configurations;
both Release APKs built and the signed SS APK passed signature/alignment checks.
On Thor, the three-state cycle kept the same native session, all ten GBA controls
passed press/release checks, and reopening retained game only. The real-device run
used a game without a companion pack. Portrait rotation and an assigned live pack
were covered by host tests, not verified on Thor during this update.

Download the SS APK again and install over the existing SS app to receive this
same-version update. The refreshed `SHA256SUMS.txt` identifies the new SS binary.

Focused tests cover default-square queries, explicit portrait/all queries, empty-square results without fallback, automatic cover lookup and the real Game settings → picker flow through all three modes and saving. The same feature source was exercised on Thor with real square SteamGridDB covers, including applying a cover and displaying it through the official cartridge renderer. Full-library batch scraping was checked through its query path and fixtures, not run against the user's entire library.
