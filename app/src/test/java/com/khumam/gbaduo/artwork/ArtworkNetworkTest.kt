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

    @Test fun imageDownloadsValidateProviderHostAndNeverSendApiCredentials() {
        var requests = 0
        val transport = ArtworkTransport { request -> requests++; assertTrue(request.headers.isEmpty()); ArtworkResponse(200, byteArrayOf(1, 2), emptyMap()) }
        assertArrayEquals(byteArrayOf(1, 2), ArtworkHttp.download(ArtworkCandidate("steamgriddb", "1", "https://cdn2.steamgriddb.com/grid/a.png"), transport))
        for (url in listOf("http://cdn2.steamgriddb.com/a.png", "https://evil.invalid/a.png", "https://cdn2.steamgriddb.com.evil.invalid/a.png", "https://user:pass@cdn2.steamgriddb.com/a.png")) {
            assertThrows(ArtworkProviderException::class.java) { ArtworkHttp.download(ArtworkCandidate("steamgriddb", "1", url), transport) }
        }
        assertEquals(1, requests)
    }

}
