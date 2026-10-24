# RetroAchievements in Plus

Plus integrates the **official rcheevos client library**; it does not create achievement definitions or award progress independently of the service.

Open **Settings > RetroAchievements** before starting a game, or **RetroAchievements** from the game menu, and sign in on the device. Both routes use the same encrypted token store. Both routes use the official 0.1.7 process-wide RA client. The password is used transiently for authentication. A successful session token is stored encrypted using a device-local Android Keystore key. It is not copied into companion data, Studio packets or this repository. Signing out clears the stored session.

The loaded ROM is identified by rcheevos from its **actual bytes**. There is no hash-override preference, native setter or hidden alternate identity in Plus. Translation/asset/gameplay patches may change recognition; a working companion and a recognized achievement set are separate concerns.

## Presentation

- Automatic sign-in notice followed by recognized-game image/progress.
- Recognized games show unlocked/total achievements and badge images.
- Unrecognized games show a no-achievements notice rather than an "Unknown game" intermediate popup.
- Unlocks queue at the bottom right of the game display, with badge, title and points.
- Popup background opacity is **70%**; text/images remain opaque. Sign-in notices last about 2.8 seconds, game summaries about 3.5 seconds and unlock notices about 5 seconds.
- Notifications do not intentionally take controller focus or pause gameplay.

Only **non-Hardcore** operation is enabled. There is no Hardcore toggle in this edition. This preserves the intended companion/save-state workflow; the app does not claim Hardcore compliance.

API traffic uses HTTPS to RetroAchievements. Public badge/game images are downloaded from the official media host with bounded sizes, disabled redirects and a disposable cache. Failed network requests and server errors can still prevent sign-in, detection or immediate notification; Plus does not claim offline achievement awards or guaranteed server availability.

## Validation boundary

Historical Thor checks covered sign-in, detection/progress, locked badges and notification sequencing. The 0.1.7 upgrade replaces the former Plus RA runtime with the official 0.1.7 integration (rcheevos 12.5.0), then adds encrypted tokens, achievement list, progress sidecars and presentation preferences. The current source-publication run builds the native integration and checks that removed hash/translation APIs are absent. It does **not** claim a freshly observed gameplay unlock and server-confirmed award for this exact commit. Test that separately with a supported ROM/account before making service-compatibility claims.

The RetroAchievements name, mark, achievement badges and game images belong to their respective owners. See [CREDITS](../CREDITS.md) for rcheevos and RAWeb origins.
