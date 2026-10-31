package com.khumam.gbaduo.companion.builder

import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.storage.GameStorage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AutomaticBuilderCoreTest {
    private val origin = URI("https://example.org/builder/catalog.json")
    private fun zip(dir: File, files: Map<String, String>): File = File(dir, "input-${java.util.UUID.randomUUID()}.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { out -> files.forEach { (name, content) ->
            out.putNextEntry(ZipEntry(name)); out.write(content.toByteArray()); out.closeEntry()
        } }
    }
    private fun catalog(bytes: ByteArray, unpacked: Long = 9): String = JSONObject()
        .put("schemaVersion", 1).put("runnerProtocol", 1).put("builderVersion", "0.9.6")
        .put("builder", JSONObject().put("url", "releases/builder.zip")
            .put("sha256", BuilderFiles.sha256(bytes)).put("bytes", bytes.size)
            .put("unpackedBytes", unpacked).put("entrypoint", "plus-runner.html"))
        .put("profiles", org.json.JSONArray().put(JSONObject().put("id", "fe7-vietnamese")
            .put("name", "FE7").put("version", "0.7.14").put("gameCode", "AE7E").put("crc32", "32CD990D")))
        .toString()

    @Test fun exactRomMatchingAndSameOriginBundleResolution() {
        val c = BuilderCatalog.parse(catalog(byteArrayOf(1)), origin)
        assertEquals("https://example.org/builder/releases/builder.zip", c.bundle.uri.toString())
        assertNotNull(c.profile("AE7E", 0x32cd990d))
        assertNull(c.profile("AE7E", 0x32cd990e))
        assertNull(c.profile("BE8E", 0x32cd990d))
    }

    @Test fun rejectsCrossOriginRedirectAndMalformedCatalogIdentity() {
        val valid = catalog(byteArrayOf(1))
        for (url in listOf("https://evil.invalid/build.zip", "http://example.org/a", "../build.zip", "//evil.invalid/a")) {
            val bad = JSONObject(valid).apply { getJSONObject("builder").put("url", url) }
            assertThrows(IllegalArgumentException::class.java) { BuilderCatalog.parse(bad.toString(), origin) }
        }
        for (replacement in listOf("bad", "../x", "0")) {
            val bad = JSONObject(valid).apply { getJSONArray("profiles").getJSONObject(0).put("crc32", replacement) }
            assertThrows(IllegalArgumentException::class.java) { BuilderCatalog.parse(bad.toString(), origin) }
        }
    }

    @Test fun verifiedBundleIsAvailableOfflineAndCorruptCacheFailsClosed() {
        val root = createTempDir(); val bytes = zip(root, mapOf("plus-runner.html" to "123456789")).readBytes()
        val json = catalog(bytes)
        var offline = false
        val cache = BuilderDistribution(File(root, "cache")) { uri, _, _ ->
            if (offline) throw IOException("offline")
            if (uri.path.endsWith("catalog.json")) json.toByteArray() else bytes
        }
        cache.resolve(origin, BuildCancellation()).use { assertTrue(File(it.directory, "plus-runner.html").isFile) }
        offline = true
        cache.resolve(origin, BuildCancellation()).use { assertTrue(it.fromCache) }
        File(root, "cache/bundles").listFiles()!!.first().writeText("corrupt")
        assertThrows(IOException::class.java) { cache.resolve(origin, BuildCancellation()) }
    }

    @Test fun catalogWriteFailureCleansItsUnclaimedExtractedRun() {
        val root = createTempDir(); val bytes = zip(root, mapOf("plus-runner.html" to "123456789")).readBytes()
        val json = catalog(bytes); val cacheDir = File(root, "cache")
        val blockedRecord = File(cacheDir, "catalog-${BuilderFiles.sha256(origin.toString().toByteArray())}.json").apply { mkdirs() }
        File(blockedRecord, "keep.txt").writeText("keep")
        val cache = BuilderDistribution(cacheDir) { uri, _, _ -> if (uri.path.endsWith("catalog.json")) json.toByteArray() else bytes }
        assertThrows(IOException::class.java) { cache.resolve(origin, BuildCancellation()) }
        assertEquals(0, File(cacheDir, "runs").listFiles()?.size ?: 0)
        assertEquals("keep", File(blockedRecord, "keep.txt").readText())
    }

    @Test fun discoveryDownloadsOnlyBoundedMetadataAndCanReadItOfflineWithoutABundle() {
        val root = createTempDir(); val requests = mutableListOf<Pair<URI, Long>>()
        val json = catalog(byteArrayOf(1)); var offline = false
        val distribution = BuilderDistribution(File(root, "cache")) { uri, limit, _ ->
            requests += uri to limit
            if (offline) throw IOException("offline")
            check(uri == origin) { "Discovery tried to download the builder" }
            json.toByteArray()
        }
        assertNotNull(distribution.catalog(origin, BuildCancellation()).profile("AE7E", 0x32cd990d))
        assertEquals(listOf(origin to BuilderCatalog.MAX_CATALOG_BYTES), requests)
        assertFalse(File(root, "cache/bundles").exists()); assertFalse(File(root, "cache/runs").exists())
        offline = true
        assertNotNull(distribution.catalog(origin, BuildCancellation()).profile("AE7E", 0x32cd990d))
        // Cached metadata is not a promise that offline creation is possible.
        assertThrows(IOException::class.java) { distribution.resolve(origin, BuildCancellation()) }
    }

    @Test fun discoveringANewerBundleDoesNotInvalidateTheVerifiedOfflinePair() {
        val root = createTempDir(); val bytes = zip(root, mapOf("plus-runner.html" to "123456789")).readBytes()
        val verified = catalog(bytes); val newer = JSONObject(verified).apply {
            put("builderVersion", "0.9.7")
            getJSONObject("builder").put("sha256", "a".repeat(64))
        }.toString()
        var mode = 0
        val distribution = BuilderDistribution(File(root, "cache")) { uri, _, _ ->
            if (mode == 2) throw IOException("offline")
            if (uri == origin) (if (mode == 0) verified else newer).toByteArray() else bytes
        }
        distribution.resolve(origin, BuildCancellation()).close()
        mode = 1
        assertEquals("0.9.7", distribution.catalog(origin, BuildCancellation()).version)
        mode = 2
        assertEquals("0.9.7", distribution.catalog(origin, BuildCancellation()).version)
        distribution.resolve(origin, BuildCancellation()).use {
            assertTrue(it.fromCache); assertEquals("0.9.6", it.catalog.version)
        }
    }

    @Test fun discoveryRejectsOversizedOrUntrustedMetadataRatherThanUsingStaleCache() {
        val root = createTempDir(); var response = catalog(byteArrayOf(1)).toByteArray()
        val distribution = BuilderDistribution(File(root, "cache")) { _, _, _ -> response }
        distribution.catalog(origin, BuildCancellation())
        response = ByteArray(BuilderCatalog.MAX_CATALOG_BYTES.toInt() + 1)
        assertThrows(IllegalArgumentException::class.java) { distribution.catalog(origin, BuildCancellation()) }
        response = JSONObject(catalog(byteArrayOf(1))).apply {
            getJSONObject("builder").put("url", "https://evil.invalid/builder.zip")
        }.toString().toByteArray()
        assertThrows(IllegalArgumentException::class.java) { distribution.catalog(origin, BuildCancellation()) }
        assertThrows(IllegalArgumentException::class.java) { distribution.catalog(URI("http://example.org/catalog.json"), BuildCancellation()) }
    }

    @Test fun zipSlipDuplicateAndInflationLimitsAreRejected() {
        val root = createTempDir()
        for (name in listOf("../escaped", "/outside", "a\\b")) {
            val file = zip(root, mapOf(name to "x"))
            assertThrows(IllegalArgumentException::class.java) {
                BuilderFiles.extract(file, File(root, "out"), 100, 20, BuildCancellation())
            }
        }
        val file = zip(root, mapOf("large" to "1234567890"))
        assertThrows(IllegalArgumentException::class.java) {
            BuilderFiles.extract(file, File(root, "bounded"), 9, 20, BuildCancellation())
        }
        // ZIP writers forbid duplicates; patch equally-sized local and central
        // filename bytes to reproduce a hostile archive with duplicate paths.
        val duplicate = zip(root, mapOf("first.txt" to "a", "other.txt" to "b"))
        val raw = duplicate.readBytes(); val needle = "other.txt".toByteArray(); val replacement = "first.txt".toByteArray()
        for (at in 0..raw.size - needle.size) if (raw.copyOfRange(at, at + needle.size).contentEquals(needle)) replacement.copyInto(raw, at)
        duplicate.writeBytes(raw)
        assertThrows(IllegalArgumentException::class.java) {
            BuilderFiles.extract(duplicate, File(root, "duplicate"), 100, 20, BuildCancellation())
        }
        assertFalse(File(root, "escaped").exists())
    }

    private fun generated(dir: File, crc: String = "32CD990D", permissions: Boolean = false): File {
        val manifest = JSONObject().put("id", "manual-pack").put("name", "Companion").put("version", "1.0")
            .put("formatVersion", if (permissions) 3 else 2).put("layout", "layout.json").put("script", "main.lua")
            .put("match", JSONObject().put("gameCode", org.json.JSONArray().put("AE7E"))
                .put("crc32", org.json.JSONArray().put(crc)))
        if (permissions) manifest.put("permissions", org.json.JSONArray().put("memory-write"))
        return zip(dir, mapOf("manifest.json" to manifest.toString(), "layout.json" to "{\"type\":\"text\",\"text\":\"hi\"}", "main.lua" to "function update()return{}end"))
    }

    @Test fun installCreatesUniquePackAndPreservesManualFiles() {
        val root = createTempDir(); val packs = File(root, "packs"); File(packs, "manual-pack").mkdirs()
        val marker = File(packs, "manual-pack/custom.txt").apply { writeText("keep") }
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        storage.packId = "manual-pack"
        val result = GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, "manual-pack", BuildCancellation())
        assertTrue(result.id.startsWith("auto-")); assertNotEquals("manual-pack", result.id)
        assertEquals(result.id, storage.packId); assertEquals("keep", marker.readText())
    }

    @Test fun repeatedBuildReplacesOneAutomaticPackWithoutAccumulatingCopies() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        val first = GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, null, BuildCancellation())
        File(packs, "${first.id}/obsolete.txt").writeText("old")
        val second = GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, first.id, BuildCancellation())
        assertEquals(first.id, second.id)
        assertEquals(listOf(first.id), packs.listFiles()!!.map { it.name })
        assertFalse(File(packs, "${first.id}/obsolete.txt").exists())
        assertEquals(second.id, storage.packId)
    }

    @Test fun failedAutomaticReplacementRestoresPreviousPackAndAssignment() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        val first = GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, null, BuildCancellation())
        File(packs, "${first.id}/keep.txt").writeText("previous pack")
        val original = File(storage.dir, "pack.json").readBytes()
        assertThrows(IOException::class.java) {
            GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, first.id, BuildCancellation(),
                writeAssignmentTemporary = { _, _ -> throw IOException("disk full") })
        }
        assertEquals("previous pack", File(packs, "${first.id}/keep.txt").readText())
        assertArrayEquals(original, File(storage.dir, "pack.json").readBytes())
        assertEquals(listOf(first.id), packs.listFiles()!!.map { it.name })
    }

    @Test fun legacyUuidAutomaticPackIsUpdatedInPlace() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        val first = GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, null, BuildCancellation())
        val legacyId = "auto-12345678-1234-1234-1234-123456789abc"
        val old = File(packs, first.id); val legacy = File(packs, legacyId)
        assertTrue(old.renameTo(legacy))
        val file = File(legacy, "manifest.json")
        file.writeText(JSONObject(file.readText()).put("id", legacyId).toString())
        File(legacy, ".plus-generated-source").delete()
        storage.packId = legacyId
        val updated = GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, legacyId, BuildCancellation())
        assertEquals(legacyId, updated.id)
        assertEquals(listOf(legacyId), packs.listFiles()!!.map { it.name })
    }

    @Test fun wrongRomWritePermissionAndStaleAssignmentNeverReplaceExistingChoice() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        storage.packId = "custom"
        for (file in listOf(generated(root, "00000000"), generated(root, permissions = true))) {
            assertThrows(IllegalArgumentException::class.java) {
                GeneratedPackTransaction.install(file, packs, storage, "AE7E", 0x32cd990d, "custom", BuildCancellation())
            }
            assertEquals("custom", storage.packId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, "old-choice", BuildCancellation())
        }
        assertEquals("custom", storage.packId)
        assertEquals(0, packs.listFiles()?.size ?: 0)
    }

    @Test fun cancellationDoesNotInstallOrChangeAssignment() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        storage.packId = "custom"
        val token = BuildCancellation().apply { cancel() }
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, "custom", token)
        }
        assertEquals("custom", storage.packId)
    }

    @Test fun cancellationWinsBeforeOwnerThreadCommitAndNewPackIsRemoved() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        storage.packId = "custom"; val token = BuildCancellation()
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, "custom", token) { commit ->
                token.cancel(); commit()
            }
        }
        assertEquals("custom", storage.packId); assertEquals(0, packs.listFiles()?.size ?: 0)
    }

    @Test fun ownerThreadRejectsAConcurrentManualSelectionWithoutRestoringOldChoice() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        storage.packId = "custom"
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, "custom", BuildCancellation()) { commit ->
                storage.packId = "new-manual-choice"; commit()
            }
        }
        assertEquals("new-manual-choice", storage.packId); assertEquals(0, packs.listFiles()?.size ?: 0)
    }

    @Test fun successfulCommitWinsOverLaterCancellation() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        storage.packId = "custom"; val token = BuildCancellation()
        val result = GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, "custom", token) { commit ->
            commit(); assertFalse(token.cancel())
        }
        assertEquals(result.id, storage.packId); assertTrue(File(packs, result.id).isDirectory); assertTrue(token.committed)
    }

    @Test fun catalogCreationInstallsButLeavesTheOriginalAssignmentBytesForItsParent() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Raw header", "AE7E", 0x32cd990d)
        storage.packId = "manual-choice"
        val assignment = File(storage.dir, "pack.json"); val original = assignment.readBytes()
        val token = BuildCancellation()
        val result = GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d,
            "manual-choice", token, assignToGame = false,
            writeAssignmentTemporary = { _, _ -> fail("Result-only creator must not write an assignment") })
        assertTrue(File(packs, "${result.id}/manifest.json").isFile)
        assertArrayEquals(original, assignment.readBytes()); assertEquals("manual-choice", storage.packId)
        assertFalse(token.cancel()); assertEquals("manual-choice", storage.packId)
    }

    @Test fun cancelAtCatalogCommitPreservesAnAlreadyAssignedStableIdAndItsContents() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        val previous = GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, null, BuildCancellation())
        val marker = File(packs, "${previous.id}/keep.txt").apply { writeText("previous contents") }
        val oldManifest = File(packs, "${previous.id}/manifest.json").readBytes()
        val oldAssignment = File(storage.dir, "pack.json").readBytes(); val token = BuildCancellation()
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, previous.id,
                token, assignToGame = false) { commit -> token.cancel(); commit() }
        }
        assertEquals("previous contents", marker.readText())
        assertArrayEquals(oldManifest, File(packs, "${previous.id}/manifest.json").readBytes())
        assertArrayEquals(oldAssignment, File(storage.dir, "pack.json").readBytes())
        assertEquals(listOf(previous.id), packs.listFiles()!!.map { it.name })
    }

    @Test fun partialAssignmentWriteFailurePreservesPreviousBytesAndRemovesNewPack() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        storage.packId = "custom"
        val assignment = File(storage.dir, "pack.json")
        assignment.writeText("{\n  \"packId\": \"custom\", \"retained\": \"original bytes\"\n}\n")
        val original = assignment.readBytes()
        val marker = File(packs, "custom/keep.txt").apply { parentFile.mkdirs(); writeText("keep") }
        val token = BuildCancellation()
        assertThrows(IOException::class.java) {
            GeneratedPackTransaction.install(generated(root), packs, storage, "AE7E", 0x32cd990d, "custom", token,
                writeAssignmentTemporary = { target, bytes ->
                    target.outputStream().use { it.write(bytes, 0, minOf(5, bytes.size)) }
                    throw IOException("No space left while writing assignment")
                })
        }
        assertArrayEquals(original, assignment.readBytes())
        assertEquals("custom", storage.packId)
        assertEquals("keep", marker.readText())
        assertFalse(token.committed)
        assertEquals(listOf("custom"), packs.listFiles()!!.map { it.name })
        assertFalse(storage.dir.listFiles()!!.any { it.name.startsWith(".pack.json-") })
    }

    @Test fun malformedLayoutCannotBeAssigned() {
        val root = createTempDir(); val packs = File(root, "packs")
        val storage = GameStorage.forGame(root, Platform.GBA, "Game", "AE7E", 0x32cd990d)
        storage.packId = "custom"
        val original = generated(root)
        val entries = mutableMapOf<String, String>()
        java.util.zip.ZipInputStream(original.inputStream()).use { input ->
            var entry = input.nextEntry
            while (entry != null) { entries[entry.name] = input.readBytes().toString(Charsets.UTF_8); entry = input.nextEntry }
        }
        entries["layout.json"] = "{\"type\":\"pages\",\"pages\":[]}"
        assertThrows(com.khumam.gbaduo.companion.LayoutParser.InvalidLayoutException::class.java) {
            GeneratedPackTransaction.install(zip(root, entries), packs, storage, "AE7E", 0x32cd990d, "custom", BuildCancellation())
        }
        assertEquals("custom", storage.packId); assertEquals(0, packs.listFiles()?.size ?: 0)
    }
}
