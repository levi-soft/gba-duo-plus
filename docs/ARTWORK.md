# Artwork (Plus extension)

Official 0.1.8 supplies artwork folders, SteamGridDB scraping and per-game selection locks. Plus extends those features with Libretro, manual import, a gallery and square cover rendering. It keeps the original cartridge carousel, controller navigation, game launching, saves, companion assignments and RetroAchievements identity.

## Pick a cover

Select a game in the library and choose **Game settings → Find artwork**, or press **X** and open Find artwork. Existing controls retain their functions: A plays, Y opens Companion, and Start opens Settings.

- **SteamGridDB:** search a game, choose the correct result, browse square images, preview one and select **Apply artwork**. **Show all image formats** includes other grid aspect ratios. The library uses a square frame and fits images without stretching or forced cropping.
- **Choose image:** use the Android picker to import a PNG, JPEG or WebP. Preview before applying. Plus copies the image into its private storage; it does not move or change the original file.
- **Libretro (default):** search box art without an account or API key. Choose a matching regional title to open its single cover directly in preview, then apply. Libretro skips the image-selection grid when only one cover exists.
- **Remove artwork:** restore the original cartridge placeholder. No ROM, save or companion is removed.

Selected images remain available offline. Changing a provider's game association or choosing a cover never changes the ROM bytes, achievement identity or save name. Translations and mods can be assigned an image from a selected base game even when their actual hashes do not match its provider database.

## Configure sources

Open **Settings → Scraping**. Open **Libretro artwork** for the retained Plus provider configuration and missing-cover workflow. The Plus artwork screens use English to match the current Plus interface, including when Android is configured in Vietnamese.

SteamGridDB requires a personal API key. **Get SteamGridDB API key** opens its official account page; sign in there and paste the key into Plus. Keys are encrypted using an artwork-specific Android Keystore key, separate from RetroAchievements credentials. Never include keys in bug reports.

Libretro is the default source and requires no account. SteamGridDB is an optional alternative for choosing custom square covers; its saved API key remains available. ScreenScraper has been removed.

Sources: [Libretro thumbnails](https://github.com/libretro-thumbnails/libretro-thumbnails), [SteamGridDB API](https://www.steamgriddb.com/api/v2). Daijisho also lists Libretro as its first GBA artwork source; Plus uses its own provider implementation.

## Missing-artwork scraping

**Scrape missing artwork** uses the public Libretro box-art index. Existing choices are kept. Only an unambiguous normalized title match is assigned automatically; translations, renamed ROMs and ambiguous games may need individual search or manual import. This is title matching, not verified ROM-hash identification. Cancellation and running again are supported. A manual choice made during downloading wins. SteamGridDB also supports upstream batch scraping when its key is configured; folder matches take priority. Per-game removed/picked locks protect explicit selections.

Requests send search/index paths and artwork names to the chosen source. ROM bytes, saves and companion data are not uploaded. Hashing, requests and image decoding run on workers. Carousel drawing uses local decoded images and does not initiate network requests.
## Storage and image handling

Selected artwork is durable user data under `filesDir/artwork/` (shared with upstream settings; legacy `plus-artwork/` selections are copied once and kept for rollback), keyed by actual platform and ROM CRC32. Renaming or moving a ROM does not change that identity. Failed validation or replacement preserves the previous selection. Image input, dimensions and encoded output are bounded; large valid images are downsampled before saving. Gallery thumbnails are separately limited in size. Source/author information is retained without storing provider credentials or private download URLs in artwork metadata.

Replacement images and metadata are prepared and synchronized on a worker before
the final request-ownership claim. Cancellation or navigation before that claim
keeps the previous artwork and removes staged files. A claim accepted before
cancellation may finish one coherent atomic replacement; cancellation itself
never waits for image encoding or storage I/O. Batch scraping rechecks for a
manual selection at publication. SteamGridDB key loading and provider searches
also run on a worker.

## Source and upstream boundary

Plus provider, picker, binder and legacy-migration classes are in `plus/src/main/java/com/khumam/gbaduo/artwork/`. The shared index, folder scraping and per-game settings extend upstream 0.1.8 classes explicitly. Other hooks include:

1. Library Activity: entry button/X, binder lifecycle.
2. Cartridge carousel: optional bind callback and recycled-view cleanup.
3. Cartridge view: optional draw delegate; returning false uses the original drawing.
4. Settings: one card opening the Plus configuration screen.
5. Manifest: private Plus activities.

These integrations are recorded in `plus/upstream.json`. No official class is copied into Plus to conceal a fork. If upstream later provides equivalent support, route or remove these hooks and remove the Plus implementation. Preserve selected image files during migration; disabling a feature must not silently delete user artwork.

## Validation status

Automated checks cover storage identity, failed replacement, offline loading, image bounds, aspect ratio, recycled views, HTTP bounds, authentication, redaction and stale requests. SteamGridDB authenticated search and the square gallery were observed working on Thor on 2026-10-24, with selected artwork visible in the library. All three official GB/GBC/GBA box-art directories and sample images returned HTTP 200 without credentials. Native manual-picker acceptance remains a separate device check.

The full JVM run after the Libretro migration completed 357 tests: 356 passed and the inherited `cssPackDetailPage` reference mismatch remained (9,090 pixels, identical actual-image SHA-256 to the pre-artwork baseline). No golden image or tolerance was changed.

Follow-up on 2026-10-24: the Windows screenshot-reference fix brought the full debug JVM suite to **357/357 passing**, including all four screenshots. The original references and tolerances remain unchanged; see [validation details](VALIDATION.md#windows-screenshot-validation-2026-10-24).

The migration artwork suite passed 24/24 tests. Independent review found no critical/high blockers. The signed release was audited and installed on Thor without clearing existing artwork or the saved SteamGridDB key.

Thor verification: Libretro was the default source, Harvest Moon (USA) was selected, and its downloaded box art reached the preview screen without credentials. The gallery opened at its title rather than retaining the previous result-list offset. Existing library covers survived the update. The preview was left for the user; no test image was applied over their selected cover.
