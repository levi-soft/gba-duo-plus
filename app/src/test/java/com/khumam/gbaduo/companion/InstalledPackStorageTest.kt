package com.khumam.gbaduo.companion

import com.khumam.gbaduo.companion.management.InstalledPackStorage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class InstalledPackStorageTest {
    private fun pack(root: File, id: String, version: String = "1.0.0"): PackManifest {
        val json = """{"id":"$id","name":"Companion","version":"$version","formatVersion":1,
            "match":{"gameCode":["AE7E"]},"layout":"layout.json"}"""
        File(root, "packs/$id/manifest.json").apply { parentFile.mkdirs(); writeText(json) }
        return PackManifest.parse(json)
    }

    @Test fun deleteClearsEveryAssignmentButPreservesOtherPacksSavesAndOriginalZip() {
        val root = createTempDirectory("installed-pack-test").toFile()
        pack(root, "old"); pack(root, "keep")
        val a = File(root, "games/gba/A/pack.json").apply { parentFile.mkdirs(); writeText("""{"packId":"old","extra":true}""") }
        val b = File(root, "games/gba/B/pack.json").apply { parentFile.mkdirs(); writeText("""{"packId":"old"}""") }
        val other = File(root, "games/gba/C/pack.json").apply { parentFile.mkdirs(); writeText("""{"packId":"keep"}""") }
        val legacy = File(root, "pack_assignments.json").apply { writeText("""{"A.gba":"old","B.gba":"keep"}""") }
        val save = File(a.parentFile, "game.sav").apply { writeText("save bytes") }
        val original = File(root, "download.zip").apply { writeText("original zip") }
        InstalledPackStorage(root).delete("old")
        assertNull(PackStore(File(root, "packs")).byId("old"))
        assertNotNull(PackStore(File(root, "packs")).byId("keep"))
        assertFalse(JSONObject(a.readText()).has("packId")); assertTrue(JSONObject(a.readText()).getBoolean("extra"))
        assertFalse(JSONObject(b.readText()).has("packId"))
        assertEquals("keep", JSONObject(other.readText()).getString("packId"))
        assertFalse(JSONObject(legacy.readText()).has("A.gba"))
        assertEquals("keep", JSONObject(legacy.readText()).getString("B.gba"))
        assertEquals("save bytes", save.readText()); assertEquals("original zip", original.readText())
    }

    @Test fun invalidIdAndMalformedAssignmentDoNotDeletePack() {
        val root = createTempDirectory("installed-pack-test").toFile(); pack(root, "old")
        assertThrows(IllegalArgumentException::class.java) { InstalledPackStorage(root).delete("../games") }
        File(root, "games/gba/A/pack.json").apply { parentFile.mkdirs(); writeText("broken JSON") }
        assertThrows(Exception::class.java) { InstalledPackStorage(root).delete("old") }
        assertNotNull(PackStore(File(root, "packs")).byId("old"))
    }

    @Test fun duplicateNamesRemainDistinguishableAndVersionsAreShown() {
        val root = createTempDirectory("installed-pack-test").toFile()
        val packs = listOf(pack(root, "old"), pack(root, "other"), pack(root, "new", "1.0.1"))
        val labels = InstalledPackStorage.labels(packs)
        assertEquals(3, labels.distinct().size)
        assertTrue(labels[0].endsWith("\nold")); assertTrue(labels[1].endsWith("\nother"))
        assertEquals("Companion · v1.0.1", labels[2])
    }
}
