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

Focused tests cover default-square queries, explicit portrait/all queries, empty-square results without fallback, automatic cover lookup and the real Game settings → picker flow through all three modes and saving. The same feature source was exercised on Thor with real square SteamGridDB covers, including applying a cover and displaying it through the official cartridge renderer. Full-library batch scraping was checked through its query path and fixtures, not run against the user's entire library.
