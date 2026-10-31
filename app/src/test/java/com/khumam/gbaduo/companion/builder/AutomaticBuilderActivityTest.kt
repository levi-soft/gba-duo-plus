package com.khumam.gbaduo.companion.builder

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.khumam.gbaduo.companion.PackStore
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.library.RomEntry
import com.khumam.gbaduo.storage.GameStorage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Executes the real native Activity/bridge/install path with local metadata and bundle fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomaticBuilderActivityTest {
    private fun awaitUi(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        } while (System.nanoTime() < deadline)
        assertTrue("Expected builder Activity completion", condition())
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private fun zip(files: Map<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { out -> files.forEach { (name, value) ->
            out.putNextEntry(ZipEntry(name)); out.write(value.toByteArray()); out.closeEntry()
        } }
    }.toByteArray()

    @Test fun catalogCreatorReturnsBoundInstalledIdAndFinishesWithoutAssigningTheGame() {
        val parent = Robolectric.buildActivity(Activity::class.java).setup(); val activity = parent.get()
        val romBytes = ByteArray(1024)
        "RAW HEADER".toByteArray().copyInto(romBytes, 0xA0)
        "AE7E".toByteArray().copyInto(romBytes, 0xAC); romBytes[0xB2] = 0x96.toByte()
        val crc = CRC32().apply { update(romBytes) }.value
        val rom = File(activity.cacheDir, "fixture.gba").apply { writeBytes(romBytes) }
        val entry = RomEntry(Uri.fromFile(rom), "Friendly name.gba", "RAW HEADER", "AE7E")
        val source = WebCompanionCreation.Source(entry, crc)
        val storage = GameStorage.forGame(activity.filesDir, Platform.GBA, entry.title, entry.gameCode, crc)
        storage.packId = "keep-manual-choice"
        val original = File(storage.dir, "pack.json").readBytes()
        val token = BuildCancellation(); var request: Intent? = null
        WebCompanionCreation.launch(activity, source, 81, token) { fail("Unexpected staging abort: $it") }
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        awaitUi { request = shadowOf(activity).nextStartedActivityForResult?.intent; request != null }
        val staged = File(request!!.getStringExtra("builder.rom")!!)
        assertArrayEquals(romBytes, staged.readBytes())

        val bundle = zip(mapOf("plus-runner.html" to "fixture"))
        val catalog = JSONObject().put("schemaVersion", 1).put("runnerProtocol", 1).put("builderVersion", "1.2.0")
            .put("builder", JSONObject().put("url", "builder.zip").put("sha256", BuilderFiles.sha256(bundle))
                .put("bytes", bundle.size).put("unpackedBytes", 7).put("entrypoint", "plus-runner.html"))
            .put("profiles", JSONArray().put(JSONObject().put("id", "fixture").put("name", "Fixture")
                .put("version", "1.0.0").put("gameCode", "AE7E").put("crc32", "%08X".format(crc)))).toString()
        val manifest = JSONObject().put("id", "fixture-profile").put("name", "Created fixture").put("version", "1.0.0")
            .put("formatVersion", 2).put("layout", "layout.json").put("script", "main.lua")
            .put("match", JSONObject().put("gameCode", JSONArray().put("AE7E")).put("crc32", JSONArray().put("%08X".format(crc))))
        val output = zip(mapOf("manifest.json" to manifest.toString(), "layout.json" to """{"type":"text","text":"Fixture"}""",
            "main.lua" to "function update()return{}end"))
        val creator = Robolectric.buildActivity(AutomaticCompanionActivity::class.java, request)
        creator.get().distributionFactory = { cache -> BuilderDistribution(cache) { uri, _, _ ->
            if (uri.path.endsWith("catalog.json")) catalog.toByteArray() else bundle
        } }
        creator.setup()
        try {
            var browser: WebView? = null
            awaitUi { browser = descendants(creator.get().window.decorView).filterIsInstance<WebView>().firstOrNull(); browser != null }
            val bridge = shadowOf(browser!!).getJavascriptInterface("PlusBuilder")
            val config = bridge.javaClass.getDeclaredMethod("configuration").apply { isAccessible = true }.invoke(bridge) as String
            assertEquals("fixture", JSONObject(config).getString("profileId"))
            assertFalse(config.contains(rom.absolutePath)); assertFalse(config.contains(entry.uri.toString()))
            bridge.javaClass.getDeclaredMethod("beginZip", java.lang.Long.TYPE).apply { isAccessible = true }.invoke(bridge, output.size.toLong())
            bridge.javaClass.getDeclaredMethod("appendZip", String::class.java).apply { isAccessible = true }.invoke(bridge, Base64.encodeToString(output, Base64.NO_WRAP))
            bridge.javaClass.getDeclaredMethod("finishZip").apply { isAccessible = true }.invoke(bridge)
            awaitUi { creator.get().isFinishing }
            assertEquals(Activity.RESULT_OK, shadowOf(creator.get()).resultCode)
            val id = WebCompanionCreation.createdPackId(shadowOf(creator.get()).resultIntent, source)
            assertNotNull(id); assertTrue(PackStore(File(activity.filesDir, "packs")).byId(id!!)!!.matches("AE7E", crc))
            assertArrayEquals(original, File(storage.dir, "pack.json").readBytes())
            assertEquals("keep-manual-choice", storage.packId); assertTrue(token.committed)
            assertFalse(token.cancel()); assertEquals("keep-manual-choice", storage.packId)
        } finally {
            creator.pause().stop().destroy()
            awaitUi { !staged.exists() }
            assertEquals(0, File(activity.cacheDir, "companion-builder-distribution/runs").listFiles()?.size ?: 0)
            storage.packId = null; rom.delete(); parent.pause().stop().destroy()
        }
    }
}
