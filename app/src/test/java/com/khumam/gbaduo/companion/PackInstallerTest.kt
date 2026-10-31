package com.khumam.gbaduo.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

class PackInstallerTest {
    private fun rejectedBudgetLeavesInstalledPack(entries: Map<String, String>, limits: PackInstaller.ExtractionLimits, reason: String) {
        val tmp = createTempDir()
        try {
            val packs = File(tmp, "packs").apply { mkdirs() }
            PackInstaller.install(zipOf(tmp, mapOf("manifest.json" to validManifest, "layout.json" to validLayout)), packs)
            val original = File(packs, "demo-pipeline/manifest.json").readText()
            val zip = zipOf(tmp, entries)
            val error = runCatching { PackInstaller.install(zip, packs, limits) }.exceptionOrNull()
            assertTrue(error is PackInstaller.InstallException)
            assertTrue(error!!.message.orEmpty().contains(reason))
            assertEquals(original, File(packs, "demo-pipeline/manifest.json").readText())
            assertEquals(validLayout, File(packs, "demo-pipeline/layout.json").readText())
            assertEquals(listOf("demo-pipeline"), packs.listFiles()!!.map { it.name })
        } finally { tmp.deleteRecursively() }
    }

    @Test fun streamedEntryLimitCountsActualBytesWhenZipHeadersHaveNoSize() {
        val tmp = createTempDir()
        try {
            val zip = zipOf(tmp, mapOf("assets/large.bin" to "x".repeat(4096)))
            ZipInputStream(zip.inputStream()).use { input ->
                assertEquals(-1L, input.nextEntry.size)
            }
        } finally { tmp.deleteRecursively() }
        rejectedBudgetLeavesInstalledPack(
            mapOf("manifest.json" to validManifest, "layout.json" to validLayout, "assets/large.bin" to "x".repeat(4096)),
            PackInstaller.ExtractionLimits(maxTotalBytes = 8192, maxEntryBytes = 1024, maxEntries = 20), "entry",
        )
    }

    @Test fun totalBudgetAccumulatesAcrossIndividuallyAllowedEntries() {
        val metadata = validManifest.toByteArray().size + validLayout.toByteArray().size
        rejectedBudgetLeavesInstalledPack(
            mapOf("manifest.json" to validManifest, "layout.json" to validLayout,
                "assets/a.bin" to "a".repeat(600), "assets/b.bin" to "b".repeat(600)),
            PackInstaller.ExtractionLimits(maxTotalBytes = metadata + 1000L, maxEntryBytes = 1024, maxEntries = 20), "total",
        )
    }

    @Test fun directoryEntriesAlsoConsumeTheEntryCountBudget() {
        rejectedBudgetLeavesInstalledPack(
            linkedMapOf("one/" to "", "two/" to "", "three/" to "", "manifest.json" to validManifest, "layout.json" to validLayout),
            PackInstaller.ExtractionLimits(maxTotalBytes = 8192, maxEntryBytes = 1024, maxEntries = 2), "entries",
        )
    }

    @Test fun directoryPayloadCannotBypassTheUnpackedByteBudget() {
        rejectedBudgetLeavesInstalledPack(
            mapOf("hidden/" to "data must not be silently drained", "manifest.json" to validManifest, "layout.json" to validLayout),
            PackInstaller.ExtractionLimits(maxTotalBytes = 8192, maxEntryBytes = 1024, maxEntries = 20), "directory",
        )
    }

    @Test fun aPackExactlyAtTheExtractionLimitsStillInstalls() {
        val tmp = createTempDir()
        try {
            val entries = mapOf("manifest.json" to validManifest, "layout.json" to validLayout, "assets/a.bin" to "a".repeat(128))
            val sizes = entries.values.map { it.toByteArray().size.toLong() }
            val limits = PackInstaller.ExtractionLimits(sizes.sum(), sizes.max(), entries.size)
            assertEquals("demo-pipeline", PackInstaller.install(zipOf(tmp, entries), File(tmp, "packs"), limits).id)
        } finally { tmp.deleteRecursively() }
    }

    @Test
    fun rejectsGameScriptOutsideThePackEvenWhenThatFileExists() {
        val tmp = createTempDir()
        val packsDir = File(tmp, "packs").apply { mkdirs() }
        val outside = File(packsDir, "outside.lua").apply { writeText("function frame() end") }
        val manifest = validManifest.replace("\"formatVersion\":1", "\"formatVersion\":4,\"gameScript\":\"../outside.lua\"")
        val zip = zipOf(tmp, mapOf("manifest.json" to manifest, "layout.json" to validLayout))
        assertTrue(runCatching { PackInstaller.install(zip, packsDir) }.exceptionOrNull() is PackInstaller.InstallException)
        assertEquals("function frame() end", outside.readText())
        assertTrue(!File(packsDir, "demo-pipeline").exists())
    }


    private fun zipOf(dir: File, entries: Map<String, String>): File {
        val zip = File(dir, "pack.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return zip
    }

    private val validManifest = """
        {"id":"demo-pipeline","name":"Demo","version":"1.0.0","author":"a",
         "formatVersion":1,"match":{"gameCode":["BPRE"],"crc32":[]},
         "tickHz":4,"layout":"layout.json","values":{}}
    """.trimIndent()
    private val validLayout = """{"type":"text","text":"hi"}"""

    @Test
    fun installsAValidPack() {
        val tmp = createTempDir()
        val packsDir = File(tmp, "packs").apply { mkdirs() }
        val zip = zipOf(tmp, mapOf("manifest.json" to validManifest, "layout.json" to validLayout))

        val manifest = PackInstaller.install(zip, packsDir)

        assertEquals("demo-pipeline", manifest.id)
        assertTrue(File(packsDir, "demo-pipeline/manifest.json").exists())
        assertTrue(File(packsDir, "demo-pipeline/layout.json").exists())
    }

    @Test(expected = PackInstaller.InstallException::class)
    fun rejectsZipSlip() {
        val tmp = createTempDir()
        val packsDir = File(tmp, "packs").apply { mkdirs() }
        val zip = zipOf(
            tmp,
            mapOf(
                "manifest.json" to validManifest,
                "layout.json" to validLayout,
                "../../../escaped.txt" to "pwned",
            ),
        )

        PackInstaller.install(zip, packsDir)
    }

    @Test(expected = PackInstaller.InstallException::class)
    fun rejectsInvalidId() {
        val tmp = createTempDir()
        val packsDir = File(tmp, "packs").apply { mkdirs() }
        val badManifest = validManifest.replace("\"demo-pipeline\"", "\"../saves\"")
        val zip = zipOf(tmp, mapOf("manifest.json" to badManifest, "layout.json" to validLayout))

        PackInstaller.install(zip, packsDir)
    }

    @Test
    fun failedInstallLeavesExistingSameIdPackUntouched() {
        val tmp = createTempDir()
        val packsDir = File(tmp, "packs").apply { mkdirs() }
        val goodZip = zipOf(tmp, mapOf("manifest.json" to validManifest, "layout.json" to validLayout))
        PackInstaller.install(goodZip, packsDir)
        val originalLayout = File(packsDir, "demo-pipeline/layout.json").readText()

        // Same id, but missing layout.json entirely -> should fail validation and not touch the existing pack.
        val brokenZip = File(tmp, "broken.zip")
        ZipOutputStream(brokenZip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(validManifest.toByteArray())
            zos.closeEntry()
        }

        runCatching { PackInstaller.install(brokenZip, packsDir) }

        assertTrue(File(packsDir, "demo-pipeline/manifest.json").exists())
        assertEquals(originalLayout, File(packsDir, "demo-pipeline/layout.json").readText())
    }

    @Test
    fun requiresDeclaredStylesheets() {
        val tmp = createTempDir()
        val packsDir = File(tmp, "packs").apply { mkdirs() }
        val withStyles = validManifest.replace("\"formatVersion\":1", "\"formatVersion\":2,\"styles\":[\"style.css\"]")
        val missing = zipOf(tmp, mapOf("manifest.json" to withStyles, "layout.json" to validLayout))
        val error = runCatching { PackInstaller.install(missing, packsDir) }.exceptionOrNull()
        assertTrue(error is PackInstaller.InstallException && error.message!!.contains("style.css"))

        val complete = zipOf(tmp, mapOf("manifest.json" to withStyles, "layout.json" to validLayout, "style.css" to "text { color: red }"))
        assertEquals(listOf("style.css"), PackInstaller.install(complete, packsDir).styleFiles)
    }

    @Test
    fun requiresDeclaredGameScript() {
        val tmp = createTempDir()
        val packsDir = File(tmp, "packs").apply { mkdirs() }
        val withGame = validManifest.replace("\"formatVersion\":1", "\"formatVersion\":4,\"gameScript\":\"game.lua\"")
        val missing = zipOf(tmp, mapOf("manifest.json" to withGame, "layout.json" to validLayout))
        val error = runCatching { PackInstaller.install(missing, packsDir) }.exceptionOrNull()
        assertTrue(error is PackInstaller.InstallException && error.message!!.contains("game.lua"))

        val complete = zipOf(tmp, mapOf("manifest.json" to withGame, "layout.json" to validLayout, "game.lua" to "function frame() end"))
        assertEquals("game.lua", PackInstaller.install(complete, packsDir).gameScriptFile)
        assertTrue(File(packsDir, "demo-pipeline/game.lua").isFile)
    }
}
