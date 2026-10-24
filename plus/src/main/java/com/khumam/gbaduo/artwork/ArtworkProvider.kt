package com.khumam.gbaduo.artwork

import com.khumam.gbaduo.library.Platform

/** Blocking provider calls belong on an artwork worker, never on the drawing thread. */
interface ArtworkProvider {
    val id: String
    fun search(query: String, platform: Platform): List<ArtworkGame>
    fun images(game: ArtworkGame, platform: Platform, region: String? = null): List<ArtworkCandidate>
    fun lookupRom(rom: ArtworkRomInfo): ArtworkGame? = null
}

/** Safe user-facing error: never attach a URL, credentials, response body or nested cause. */
class ArtworkProviderException(
    message: String,
    val retryAfterSeconds: Long? = null,
    val statusCode: Int? = null,
) : Exception(message)
