# External launchers and Daijisho

Plus uses the exported launch activity inherited from official GBA Duo v0.1.6. A frontend must be configured with the **Plus package**, not the official/Dev package. Daijisho configuration is left to the user; no frontend profile is installed or modified by Plus.

Component:

```text
com.khumam.gbaduo.plus/com.khumam.gbaduo.library.LaunchActivity
```

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
