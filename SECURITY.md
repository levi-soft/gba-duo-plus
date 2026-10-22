# Security and privacy

Do not submit passwords, session tokens, signing keys, ROMs, saves or private RAM captures in public issues. Use GitHub private vulnerability reporting when enabled, or ask the maintainer for a private reporting channel without posting exploit details.

RetroAchievements uses HTTPS and a device-local encrypted session token. Account-bearing responses and POST bodies are not intended to be logged or exposed to companions/Studio. Sign in on your own device; no account or API credential is bundled with the source.

Studio Connection is a read-only local/ADB development interface. Release builds require per-session opt-in; debug builds expose it while a game runs. Trust the PC you authorize for USB debugging and never forward the socket or desktop tool to a public network. There is no RAM-write or achievement-award bridge command.

Companion Lua is game-memory/host integration code. Import packs only from sources you trust. Source publication, tests and native sandbox boundaries are not a guarantee that arbitrary third-party packs are safe or correct.

Signing material belongs outside Git. The release build requires explicit signing environment variables or produces unsigned output. Do not publish a personal keystore, Android debug keystore, `local.properties`, app preferences or device backup.
