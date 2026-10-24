package com.khumam.gbaduo.artwork

import java.util.Locale

/** Artwork never substitutes another game's identity for this actual ROM. */
data class ArtworkIdentity(val platform: String, val crc32: Long) {
    init {
        require(platform in setOf("gb", "gbc", "gba")) { "Unsupported artwork platform" }
        require(crc32 in 0..0xffffffffL) { "Invalid ROM checksum" }
    }

    val key: String get() = platform + "-" + String.format(Locale.ROOT, "%08X", crc32)
}

data class ArtworkRomInfo(
    val identity: ArtworkIdentity,
    val title: String,
    val fileName: String,
    val gameCode: String,
    val romSize: Long,
)

data class ArtworkGame(
    val provider: String,
    val id: String,
    val title: String,
    val year: String? = null,
)

data class ArtworkCandidate(
    val provider: String,
    val id: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val author: String? = null,
    val sourcePage: String? = null,
    val mediaType: String = "cover",
    val region: String? = null,
) {
    // Do not expose remote addresses or query secrets in logs.
    override fun toString() = "ArtworkCandidate(provider=$provider, width=$width, height=$height)"
}

/** Only attribution and the local image are persisted, never provider download URLs or tokens. */
data class ArtworkRecord(
    val identity: ArtworkIdentity,
    val imageFile: String,
    val provider: String?,
    val artworkId: String?,
    val sourcePage: String?,
    val author: String?,
    val manual: Boolean,
    val width: Int,
    val height: Int,
    val updatedAt: Long,
)
