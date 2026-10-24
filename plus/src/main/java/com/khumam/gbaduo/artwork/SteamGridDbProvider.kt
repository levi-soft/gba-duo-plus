package com.khumam.gbaduo.artwork

import com.khumam.gbaduo.library.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset

/** Official SGDB API v2; it searches game names, not ROM hashes or hardware platforms. */
class SteamGridDbProvider(private val apiKey: String, private val transport: ArtworkTransport = ArtworkHttp.transport) : ArtworkProvider {
    override val id = "steamgriddb"
    private fun request(uri: URI): JSONArray {
        val key = apiKey.trim()
        if (key.isBlank()) throw ArtworkProviderException("Add a SteamGridDB API key in Artwork sources first.")
        if (key.length > 1024 || key.any { it == '\r' || it == '\n' }) throw ArtworkProviderException("The SteamGridDB API key is invalid.")
        val result = ArtworkHttp.json(uri, mapOf("Authorization" to "Bearer $key"), transport)
        if (!result.optBoolean("success", false)) throw ArtworkProviderException("SteamGridDB could not complete this request.")
        return result.optJSONArray("data") ?: JSONArray()
    }

    override fun search(query: String, platform: Platform): List<ArtworkGame> {
        val text = query.trim().take(180)
        if (text.isBlank()) return emptyList()
        val data = request(URI("$BASE/search/autocomplete/${ArtworkHttp.encode(text)}"))
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val gameId = item.optString("id").takeIf { it.length <= 16 && it.matches(Regex("[0-9]+")) } ?: return@mapNotNull null
            val title = item.optString("name").trim().take(240)
            if (title.isBlank()) return@mapNotNull null
            val year = item.optLong("release_date", 0).takeIf { it > 0 }?.let { runCatching { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).year.toString() }.getOrNull() }
            ArtworkGame(id, gameId, title, year)
        }.distinctBy { it.id }
    }

    override fun images(game: ArtworkGame, platform: Platform, region: String?): List<ArtworkCandidate> {
        return images(game, squareOnly = true)
    }

    /** Explicit user choice; square artwork remains the default search. */
    fun imagesAllFormats(game: ArtworkGame, platform: Platform): List<ArtworkCandidate> = images(game, squareOnly = false)

    private fun images(game: ArtworkGame, squareOnly: Boolean): List<ArtworkCandidate> {
        if (game.provider != id || game.id.length > 16 || !game.id.matches(Regex("[0-9]+"))) throw ArtworkProviderException("Select a SteamGridDB game first.")
        val parameters = linkedMapOf(
            "types" to "static", "mimes" to "image/png,image/jpeg,image/webp",
            "nsfw" to "false", "humor" to "false", "epilepsy" to "false", "limit" to "50", "page" to "0",
        )
        if (squareOnly) parameters["dimensions"] = "512x512,1024x1024"
        val data = request(ArtworkHttp.url("$BASE/grids/game/${game.id}", parameters))
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            if (item.optBoolean("nsfw") || item.optBoolean("humor") || item.optBoolean("epilepsy") || item.optBoolean("animated")) return@mapNotNull null
            val imageId = item.optString("id").takeIf { it.length <= 16 && it.matches(Regex("[0-9]+")) } ?: return@mapNotNull null
            val address = item.optString("url")
            if (runCatching { ArtworkHttp.imageUri(id, address) }.isFailure) return@mapNotNull null
            val width = item.optInt("width"); val height = item.optInt("height")
            if (width <= 0 || height <= 0 || (squareOnly && width != height) || width > 4096 || height > 4096) return@mapNotNull null
            val thumb = item.optString("thumb").takeIf { runCatching { ArtworkHttp.imageUri(id, it) }.isSuccess }
            ArtworkCandidate(id, imageId, address, thumb, width, height,
                item.optJSONObject("author")?.optString("name")?.take(160), "https://www.steamgriddb.com/grid/$imageId")
        }.distinctBy { it.id }
    }

    companion object { const val BASE = "https://www.steamgriddb.com/api/v2"; const val API_KEY_URL = "https://www.steamgriddb.com/profile/preferences/api" }
}
