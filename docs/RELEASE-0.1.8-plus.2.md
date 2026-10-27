# GBA Duo Plus 0.1.8-plus.2

Fixes artwork selection from Game settings in 0.1.8-plus.1 and removes the duplicate Plus SteamGridDB implementation.

The 0.1.8 settings entry supplies platform and CRC, but no raw ROM size. The Plus picker incorrectly required that optional metadata and finished on launch. Libretro/local image selection now accepts unknown size while retaining platform, CRC and explicit-size validation. It does not re-read ROMs or substitute ZIP file size.

- **Game settings → Find on SteamGridDB** uses the official search, thumbnail selection and download flow. If the key is missing, it opens the shared SteamGridDB settings page.
- **Game settings → Libretro / local image** retains the Plus additions: no-key Libretro search with preview, and local image import.
- **Settings → Scraping → Providers → SteamGridDB** is the only API-key editor, with the existing key test plus a link to obtain a key.
- The duplicate Plus key-entry Activity and SteamGridDB provider are removed. Existing encrypted keys and stored covers remain in the shared storage.
- Cover removal uses Game settings; the duplicate Plus removal button is gone.
- The library uses the official cartridge/cover layout and image loader again. The Plus square cover renderer and separate binder are removed. Legacy Plus covers are copied into the shared cache before the official loader refreshes.

Both editions use Android versionCode 15:

- `GBA-Duo-Plus-0.1.8-plus.2.apk`: dual-screen handheld edition.
- `GBA-Duo-Plus-SS-0.1.8-plus.2.apk`: phone, tablet and foldable edition.
- `SHA256SUMS.txt`: signed APK checksums.

Install over the matching Plus/SS edition. No uninstall or data reset is needed. Builder stays at 1.1.5; companion packs are unchanged.

Automated regression coverage opens both actual Game settings actions and follows Libretro preview/apply and the official SteamGridDB search/select/download flow through canonical artwork storage with injected responses. Missing keys route to the official editor. Invalid identity and explicit invalid sizes still close the Libretro picker. See [validation](VALIDATION.md) for device evidence and limitations.
