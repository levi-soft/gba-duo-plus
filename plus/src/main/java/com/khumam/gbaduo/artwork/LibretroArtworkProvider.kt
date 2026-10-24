package com.khumam.gbaduo.artwork

import com.khumam.gbaduo.library.Platform
import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

/** Public Libretro thumbnails. No account, hash service or ROM upload is involved. */
class LibretroArtworkProvider(
    private val transport: ArtworkTransport = ArtworkHttp.transport,
    private val clock: () -> Long = System::currentTimeMillis,
) : ArtworkProvider {
    override val id = "libretro"
    private data class Index(val timestamp: Long, val games: List<ArtworkGame>)
    private val cache = mutableMapOf<Platform, Index>()

    override fun search(query: String, platform: Platform): List<ArtworkGame> {
        val normalized = normalize(query.take(240))
        if (normalized.isBlank()) return emptyList()
        val words = normalized.split(' ')
        return index(platform).filter { game -> words.all { normalize(game.title).contains(it) } }.take(100)
    }

    override fun lookupRom(rom: ArtworkRomInfo): ArtworkGame? {
        val platform = Platform.byId(rom.identity.platform) ?: return null
        // File names retain subtitles that cartridge headers often abbreviate or omit.
        val name = normalize(rom.fileName).ifBlank { normalize(rom.title) }
        if (name.isBlank()) return null
        val games = index(platform)
        val stem = rom.fileName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("\\.(gba|gbc|gb|zip|7z)$", RegexOption.IGNORE_CASE), "")
        games.singleOrNull { it.title.equals(stem, ignoreCase = true) }?.let { return it }
        return games.filter { normalize(it.title) == name }.singleOrNull()
    }

    override fun images(game: ArtworkGame, platform: Platform, region: String?): List<ArtworkCandidate> {
        if (game.provider != id || index(platform).none { it.id == game.id }) return emptyList()
        val url = base(platform) + ArtworkHttp.encode(game.id + ".png")
        return listOf(ArtworkCandidate(id, game.id, url, thumbnailUrl = url,
            sourcePage = base(platform), mediaType = "cover"))
    }

    @Synchronized private fun index(platform: Platform): List<ArtworkGame> {
        ArtworkHttp.checkInterrupted()
        val now = clock()
        cache[platform]?.takeIf { now >= it.timestamp && now - it.timestamp < CACHE_MS }?.let { return it.games }
        // The public server exposes Apache directory indexes; .index URLs return 404.
        val html = ArtworkHttp.text(URI(base(platform)), transport = transport)
        val games = HREF.findAll(html).mapNotNull { match ->
            val href = match.groupValues[1]
            if (href.length > 1800 || href.contains('/') || href.contains('?') || href.contains('#')) return@mapNotNull null
            val filename = try { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { return@mapNotNull null }
            if (!filename.endsWith(".png", true) || filename.length > 600 || filename.any { it.isISOControl() || it in "/\\" }) return@mapNotNull null
            val title = filename.dropLast(4)
            if (title.isBlank() || title == "." || title == "..") return@mapNotNull null
            ArtworkGame(id, title, title)
        }.distinctBy { it.id }.take(15000).toList()
        if (games.isEmpty()) throw ArtworkProviderException("The artwork source returned an empty or invalid game index.")
        cache[platform] = Index(now, games)
        return games
    }

    private fun base(platform: Platform): String = "https://thumbnails.libretro.com/" + ArtworkHttp.encode(when (platform) {
        Platform.GB -> "Nintendo - Game Boy"
        Platform.GBC -> "Nintendo - Game Boy Color"
        Platform.GBA -> "Nintendo - Game Boy Advance"
    }) + "/Named_Boxarts/"

    companion object {
        private const val CACHE_MS = 24 * 60 * 60 * 1000L
        private val HREF = Regex("href=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        private val TAGS = Regex("\\([^)]*\\)|\\[[^]]*]")
        /** Regional/translation dump tags are ignored; multiple variants still require user review. */
        internal fun normalize(value: String): String {
            val filename = value.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("\\.(gba|gbc|gb|zip|7z|png)$", RegexOption.IGNORE_CASE), "")
            return Normalizer.normalize(TAGS.replace(filename, " "), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
                .replace('đ', 'd').replace(Regex("[^a-z0-9]+"), " ").trim()
        }
    }
}
