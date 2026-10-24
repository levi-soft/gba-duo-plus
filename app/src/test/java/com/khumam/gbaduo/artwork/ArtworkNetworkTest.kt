package com.khumam.gbaduo.artwork

import com.khumam.gbaduo.library.Platform
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class ArtworkNetworkTest {
    private fun response(json: String, status: Int = 200) = ArtworkResponse(status, json.toByteArray(), emptyMap())

    @Test fun libretroCachesBoundedPublicIndexesAndUsesPlatformFolders() {
        var now = 100L
        val paths = mutableListOf<String>()
        val provider = LibretroArtworkProvider(ArtworkTransport {
            paths += it.uri.rawPath
            assertTrue(it.headers.isEmpty())
            assertEquals(4 * 1024 * 1024, it.maxBytes)
            response("""<a href="Fire%20Emblem%20(USA).png">art</a>""")
        }, { now })
        assertEquals(1, provider.search("fire emblem", Platform.GBA).size)
        provider.search("emblem", Platform.GBA)
        assertEquals(1, paths.size)
        provider.search("emblem", Platform.GB)
        provider.search("emblem", Platform.GBC)
        assertTrue(paths[0].contains("Game%20Boy%20Advance/Named_Boxarts/"))
        assertTrue(paths[1].contains("Game%20Boy/Named_Boxarts/"))
        assertTrue(paths[2].contains("Game%20Boy%20Color/Named_Boxarts/"))
        now += 24 * 60 * 60 * 1000L
        provider.search("emblem", Platform.GBA)
        assertEquals(4, paths.size)
    }

    @Test fun libretroAutomaticLookupRequiresUniqueNormalizedFilenameAndNeverFuzzyMatches() {
        val provider = LibretroArtworkProvider(ArtworkTransport { response("""
            <a href="Fire%20Emblem%20(USA).png">a</a>
            <a href="Fire%20Emblem%20(Europe).png">b</a>
            <a href="Advance%20Wars%20(USA).png">c</a>
        """) })
        fun rom(name: String) = ArtworkRomInfo(ArtworkIdentity("gba", 1), "FIREEMBLEM", name, "", 1024)
        assertNull(provider.lookupRom(rom("Fire Emblem (Vietnamese) [T+Eng].gba")))
        assertNull(provider.lookupRom(rom("Advance War.gba")))
        assertEquals("Fire Emblem (USA)", provider.lookupRom(rom("Fire Emblem (USA).gba"))!!.title)
        assertEquals("Fire Emblem (Europe)", provider.lookupRom(rom("Fire Emblem (Europe).gba"))!!.title)
        assertEquals("Advance Wars (USA)", provider.lookupRom(rom("Advance Wars (Việt hóa).gba"))!!.title)
        assertEquals(2, provider.search("Fire Emblem", Platform.GBA).size)
    }

    @Test fun libretroRejectsUntrustedIndexLinksAndBuildsEncodedPublicImageUrls() {
        val provider = LibretroArtworkProvider(ArtworkTransport { response("""
            <a href="https://evil.invalid/art.png">x</a><a href="../art.png">x</a>
            <a href="%2Fsecret.png">x</a><a href="art.png?x=1">x</a>
            <a href="Game%20%26%20More%20(USA).png">good</a>
        """) })
        val game = provider.search("Game", Platform.GBA).single()
        val image = provider.images(game, Platform.GBA).single()
        assertTrue(image.url.endsWith("Game%20%26%20More%20%28USA%29.png"))
        assertEquals("thumbnails.libretro.com", ArtworkHttp.imageUri("libretro", image.url).host)
        assertTrue(provider.images(ArtworkGame("libretro", "invented", "invented"), Platform.GBA).isEmpty())
        assertThrows(ArtworkProviderException::class.java) {
            ArtworkHttp.imageUri("libretro", "https://thumbnails.libretro.com.evil.invalid/a.png")
        }
    }

    @Test fun libretroEmptyInvalidOrOversizedIndexesFailWithoutPoisoningCache() {
        var calls = 0
        val provider = LibretroArtworkProvider(ArtworkTransport {
            calls++
            if (calls == 1) response("<html>no images</html>") else response("<a href=\"Tetris%20(World).png\">a</a>")
        })
        assertThrows(ArtworkProviderException::class.java) { provider.search("Tetris", Platform.GB) }
        assertEquals(1, provider.search("Tetris", Platform.GB).size)
        val oversized = LibretroArtworkProvider(ArtworkTransport { ArtworkResponse(200, ByteArray(4 * 1024 * 1024 + 1), emptyMap()) })
        assertThrows(ArtworkProviderException::class.java) { oversized.search("game", Platform.GB) }
    }

    @Test fun steamSearchEncodesNameAndUsesBearerOnlyOnApiRequest() {
        var request: ArtworkRequest? = null
        val provider = SteamGridDbProvider("private-key", ArtworkTransport {
            request = it
            response("""{"success":true,"data":[{"id":123,"name":"Fire Emblem","release_date":1050192000}]}""")
        })
        val games = provider.search("Fire & Emblem", Platform.GBA)
        assertEquals("Fire Emblem", games.single().title)
        assertEquals("Bearer private-key", request!!.headers["Authorization"])
        assertEquals("/api/v2/search/autocomplete/Fire%20%26%20Emblem", request!!.uri.rawPath)
        assertFalse(request!!.uri.toString().contains("private-key"))
    }

    @Test fun steamImagesPreferStaticSquareAndExcludeFlaggedImages() {
        var request: ArtworkRequest? = null
        val provider = SteamGridDbProvider("key", ArtworkTransport {
            request = it
            response("""{"success":true,"data":[
              {"id":1,"url":"https://cdn2.steamgriddb.com/grid/a.png","thumb":"https://cdn2.steamgriddb.com/thumb/a.png","width":512,"height":512,"author":{"name":"Artist"}},
              {"id":2,"url":"https://cdn2.steamgriddb.com/grid/b.png","width":512,"height":512,"nsfw":true},
              {"id":3,"url":"https://cdn2.steamgriddb.com/grid/c.png","width":512,"height":512,"humor":true}
            ]}""")
        })
        val art = provider.images(ArtworkGame("steamgriddb", "123", "Fire Emblem"), Platform.GBA)
        assertEquals(1, art.size)
        assertEquals("Artist", art[0].author)
        assertTrue(request!!.uri.rawQuery.contains("dimensions=512x512%2C1024x1024"))
        assertTrue(request!!.uri.rawQuery.contains("types=static"))
        assertTrue(request!!.uri.rawQuery.contains("nsfw=false"))
    }

    @Test fun serviceErrorsNeverContainCredentialsOrUrlsAndPreserveRetryHint() {
        val provider = SteamGridDbProvider("DO-NOT-PRINT", ArtworkTransport {
            ArtworkResponse(429, "private body DO-NOT-PRINT".toByteArray(), mapOf("Retry-After" to "15"))
        })
        val error = assertThrows(ArtworkProviderException::class.java) { provider.search("game", Platform.GB) }
        assertEquals(15L, error.retryAfterSeconds)
        assertFalse(error.toString().contains("DO-NOT-PRINT"))
        assertFalse(error.toString().contains("https://"))
    }

    @Test fun imageDownloadsValidateProviderHostAndNeverSendApiCredentials() {
        var requests = 0
        val transport = ArtworkTransport { request -> requests++; assertTrue(request.headers.isEmpty()); ArtworkResponse(200, byteArrayOf(1, 2), emptyMap()) }
        assertArrayEquals(byteArrayOf(1, 2), ArtworkHttp.download(ArtworkCandidate("steamgriddb", "1", "https://cdn2.steamgriddb.com/grid/a.png"), transport))
        for (url in listOf("http://cdn2.steamgriddb.com/a.png", "https://evil.invalid/a.png", "https://cdn2.steamgriddb.com.evil.invalid/a.png", "https://user:pass@cdn2.steamgriddb.com/a.png")) {
            assertThrows(ArtworkProviderException::class.java) { ArtworkHttp.download(ArtworkCandidate("steamgriddb", "1", url), transport) }
        }
        assertEquals(1, requests)
    }

    @Test fun transportFailuresAndDebugDescriptionsDoNotExposeSecrets() {
        val request = ArtworkRequest(URI("https://www.steamgriddb.com/api/v2/search?secret=PRIVATE"), mapOf("Authorization" to "SECRET"))
        assertFalse(request.toString().contains("PRIVATE"))
        assertFalse(request.toString().contains("SECRET"))
        val provider = SteamGridDbProvider("PRIVATE", ArtworkTransport { throw IllegalStateException("PRIVATE https://example.invalid/?SECRET") })
        val error = assertThrows(ArtworkProviderException::class.java) { provider.search("game", Platform.GBA) }
        assertFalse(error.toString().contains("PRIVATE"))
        assertFalse(error.toString().contains("SECRET"))
        assertNull(error.cause)
    }

    @Test fun forgedOversizedJsonAndImageResponsesAreRejectedBeforeUse() {
        val json = SteamGridDbProvider("key", ArtworkTransport { ArtworkResponse(200, ByteArray(2 * 1024 * 1024 + 1), emptyMap()) })
        assertThrows(ArtworkProviderException::class.java) { json.search("game", Platform.GBA) }
        assertThrows(ArtworkProviderException::class.java) {
            ArtworkHttp.download(ArtworkCandidate("steamgriddb", "1", "https://cdn2.steamgriddb.com/grid/a.png"), ArtworkTransport {
                ArtworkResponse(200, ByteArray(ArtworkHttp.MAX_IMAGE_BYTES + 1), emptyMap())
            })
        }
        val redirect = SteamGridDbProvider("key", ArtworkTransport { ArtworkResponse(302, ByteArray(0), mapOf("Location" to "https://evil.invalid")) })
        assertThrows(ArtworkProviderException::class.java) { redirect.search("game", Platform.GBA) }
    }

    @Test fun steamAllFormatsIsExplicitAndRetainsNonSquareArtworkChoices() {
        var query = ""
        val provider = SteamGridDbProvider("key", ArtworkTransport {
            query = it.uri.rawQuery
            response("""{"success":true,"data":[{"id":1,"url":"https://cdn2.steamgriddb.com/grid/a.png","width":600,"height":900}]}""")
        })
        val game = ArtworkGame("steamgriddb", "1", "game")
        assertTrue(provider.images(game, Platform.GBA).isEmpty())
        assertEquals(1, provider.imagesAllFormats(game, Platform.GBA).size)
        assertFalse(query.contains("dimensions="))
        assertTrue(query.contains("types=static"))
    }
}
