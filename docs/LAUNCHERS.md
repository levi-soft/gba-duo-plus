# External launchers and Daijisho

Plus uses the exported launch activity inherited from official GBA Duo v0.1.6. A frontend must be configured with the **Plus package**, not the official/Dev package. Daijisho configuration is left to the user; no frontend profile is installed or modified by Plus.

Component:

```text
com.khumam.gbaduo.plus/com.khumam.gbaduo.library.LaunchActivity
```

## Add a GBA Duo Plus player in Daijisho

First install Plus and add your ROM folder inside Plus so Android grants access. In Daijisho, open **Settings > Library > Add a Player**, following the [Daijisho player configuration guide](https://github.com/TapiocaFox/Daijishou/wiki/Start-Arguments-Cheat-Sheet).

| Field | Value |
|---|---|
| Player name | `GBA Duo Plus` |
| Player accepted filename regex | `(?i)^.*\.(?:gba|gbc|gb|zip)$` |
| Kill package processes before am start | Disabled |

Paste these lines into **Player am start arguments** (do not include `am start` itself):

```text
-n com.khumam.gbaduo.plus/com.khumam.gbaduo.library.LaunchActivity
-a android.intent.action.VIEW
-e ROM {file.path}
```

Save the player and select it in the appropriate platform's player settings. Repeat the selection for GB, GBC or GBA platforms as needed. Daijisho substitutes `{file.path}` with the selected game's path; it is not a literal ROM filename. For a URI-based configuration, replace the last line with `-d {file.uri}`; the provider must grant read access.

The instructions above target a **release** installation. For a debug installation, change only the package before the slash:

```text
-n com.khumam.gbaduo.plus.debug/com.khumam.gbaduo.library.LaunchActivity
```

`.debug` identifies a separate development app with separate saves, preferences and folder permissions. It is not a player option or a required suffix for the normal Plus build. Plus does not register this custom player automatically, and this guide does not imply inclusion in Daijisho's official player catalog.

## ADB and other frontends

Example from an ADB shell (replace the placeholder with a readable real path):

```sh
am start -a android.intent.action.VIEW \
  -n com.khumam.gbaduo.plus/com.khumam.gbaduo.library.LaunchActivity \
  --es ROM "/storage/emulated/0/Roms/example.gba"
```

Android's command is `am start`. The URI form can use the intent's data field:

```sh
am start -a android.intent.action.VIEW \
  -n com.khumam.gbaduo.plus/com.khumam.gbaduo.library.LaunchActivity \
  -d "file:///storage/emulated/0/Roms/example.gba"
```

Supported extra aliases include `rom`, `ROM`, `path`, `PATH`, `file`, `FILE`, and legacy `rom_uri`/`rom_path`. The legacy explicit `LibraryActivity` route forwards recognized launch intents, but `LaunchActivity` is preferred.

Readable `.gba`, `.gb`, `.gbc` and supported ZIP inputs are accepted. `content://` access requires the relevant provider/grant; passing a string cannot bypass Android scoped-storage permissions. Access your ROM folder through the Android folder picker when needed.

Repeated launches of the same game can reuse the current session; switching games follows a controlled core/session transition. An ordinary library launch without ROM input opens the library instead of guessing a game.

Daijisho's game scanning/achievement metadata is separate from Plus's rcheevos session. Configure the launcher as you normally would, then sign in to RetroAchievements inside Plus for runtime achievement support.
