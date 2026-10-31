package com.khumam.gbaduo.companion.builder

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Looper
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.library.RomEntry
import com.khumam.gbaduo.storage.GameStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebCompanionCreationTest {
    private val entry = RomEntry(Uri.parse("content://roms/document/fe7.gba"), "My FE7.gba", "FIREEMBLEM", "AE7E", size = 16777216, lastModified = 123)
    private val source get() = WebCompanionCreation.Source(entry, 0x32cd990d)

    private fun awaitUi(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        } while (System.nanoTime() < deadline)
        assertTrue("Expected staging completion", condition())
    }

    @Test fun sourceRoundTripPreservesRawStorageIdentityAndOriginalUri() {
        val intent = WebCompanionCreation.attachSource(Intent(), entry, source.crc32)
        assertEquals(source, WebCompanionCreation.source(intent))
        assertEquals("FIREEMBLEM", WebCompanionCreation.source(intent)!!.entry.title)
    }

    @Test fun unavailableOrMalformedRomSourceClearsOldSourceInsteadOfRetainingIt() {
        val intent = WebCompanionCreation.attachSource(Intent(), entry, source.crc32)
        for (invalid in listOf(entry.copy(uri = null), entry.copy(platform = Platform.GBC),
            entry.copy(uri = Uri.parse("https://example.org/rom.gba")),
            entry.copy(uri = Uri.parse("content:/missing-authority")),
            entry.copy(uri = Uri.parse("file:relative.gba")), entry.copy(gameCode = "../x"),
            entry.copy(fileName = "game.gb"))) {
            WebCompanionCreation.attachSource(intent, invalid, source.crc32)
            assertNull(WebCompanionCreation.source(intent))
        }
        WebCompanionCreation.attachSource(intent, entry, -1)
        assertNull(WebCompanionCreation.source(intent))
        assertNull(WebCompanionCreation.source(Intent()))
    }

    @Test fun resultMustEchoExactSourceAndSafeInstalledId() {
        val result = WebCompanionCreation.resultIntent("auto-abc123", source)
        assertEquals("auto-abc123", WebCompanionCreation.createdPackId(result, source))
        assertNull(WebCompanionCreation.createdPackId(result, source.copy(crc32 = source.crc32 + 1)))
        assertNull(WebCompanionCreation.createdPackId(result, source.copy(entry = entry.copy(title = "Display name"))))
        assertNull(WebCompanionCreation.createdPackId(result, source.copy(entry = entry.copy(uri = Uri.parse("content://roms/document/other.gba")))))
        assertNull(WebCompanionCreation.createdPackId(Intent(result).putExtra("pack_id", "../unsafe"), source))
        assertNull(WebCompanionCreation.createdPackId(Intent().putExtra("pack_id", "auto-abc123"), source))
    }

    @Test fun matchingDiscoveryUsesExactCodeAndCrcAndFetchesOnlyMetadata() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val uri = URI("https://example.org/builder/catalog.json"); val requests = mutableListOf<URI>()
        val catalog = """{"schemaVersion":1,"runnerProtocol":1,"builderVersion":"1.2.0","builder":{"url":"builder.zip","sha256":"${"a".repeat(64)}","bytes":1,"unpackedBytes":1,"entrypoint":"plus-runner.html"},"profiles":[{"id":"fe7","name":"Fire Emblem","version":"1.0.0","gameCode":"AE7E","crc32":"32CD990D"}]}"""
        val distribution = BuilderDistribution(File(activity.cacheDir, "metadata-test")) { request, limit, _ ->
            requests += request; assertEquals(BuilderCatalog.MAX_CATALOG_BYTES, limit); catalog.toByteArray()
        }
        assertEquals("fe7", WebCompanionCreation.find(source, distribution, uri, BuildCancellation())?.id)
        assertNull(WebCompanionCreation.find(source.copy(crc32 = source.crc32 + 1), distribution, uri, BuildCancellation()))
        assertEquals(listOf(uri, uri), requests)
    }

    @Test fun cancelExistingPackConfirmationNotifiesCallerOnceWithoutStartingCreator() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup(); val activity = controller.get()
        val storage = GameStorage.forGame(activity.filesDir, Platform.GBA, entry.title, entry.gameCode, source.crc32)
        storage.packId = "manual-selection"
        val token = BuildCancellation(); val aborts = mutableListOf<String?>()
        try {
            WebCompanionCreation.launch(activity, source, 81, token) { aborts += it }
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf<String?>(null), aborts); assertTrue(token.cancelled)
            assertNull(shadowOf(activity).nextStartedActivityForResult)
            assertEquals("manual-selection", storage.packId)
            token.cancel(); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, aborts.size)
        } finally { controller.pause().stop().destroy(); storage.packId = null }
    }

    @Test fun externalCancellationUnlocksConfirmationAndCannotLaunchALateCreator() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup(); val activity = controller.get()
        val storage = GameStorage.forGame(activity.filesDir, Platform.GBA, entry.title, entry.gameCode, source.crc32)
        storage.packId = "manual-selection"
        val token = BuildCancellation(); val aborts = mutableListOf<String?>()
        try {
            WebCompanionCreation.launch(activity, source, 81, token) { aborts += it }
            val confirmation = ShadowAlertDialog.getLatestAlertDialog()
            token.cancel(); shadowOf(Looper.getMainLooper()).idle()
            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf<String?>(null), aborts)
            assertFalse(confirmation.isShowing); assertNull(shadowOf(activity).nextStartedActivityForResult)
        } finally { controller.pause().stop().destroy(); storage.packId = null }
    }

    @Test fun destroyingParentAbortsConfirmationWithoutChangingAssignment() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup(); val activity = controller.get()
        val storage = GameStorage.forGame(activity.filesDir, Platform.GBA, entry.title, entry.gameCode, source.crc32)
        storage.packId = "manual-selection"
        val token = BuildCancellation(); val aborts = mutableListOf<String?>()
        WebCompanionCreation.launch(activity, source, 81, token) { aborts += it }
        controller.pause().stop().destroy(); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(token.cancelled); assertEquals(listOf<String?>(null), aborts)
        assertEquals("manual-selection", storage.packId); storage.packId = null
    }

    @Test fun unreadableRomAbortsOnceAndRemovesItsPrivateStagingFile() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup(); val activity = controller.get()
        val missing = entry.copy(uri = Uri.fromFile(File(activity.cacheDir, "missing.gba")))
        val target = WebCompanionCreation.Source(missing, source.crc32)
        val aborts = mutableListOf<String?>()
        try {
            WebCompanionCreation.launch(activity, target, 81, BuildCancellation()) { aborts += it }
            awaitUi { aborts.isNotEmpty() }
            assertEquals(1, aborts.size); assertNotNull(aborts.single())
            assertNull(shadowOf(activity).nextStartedActivityForResult)
            assertEquals(0, File(activity.cacheDir, "companion-builder-input").listFiles()?.size ?: 0)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun stagedRequestSharesCancellationAndCannotBeClaimedAfterParentCancels() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup(); val activity = controller.get()
        val original = File(activity.cacheDir, "source.gba").apply { writeBytes(ByteArray(512)) }
        val target = WebCompanionCreation.Source(entry.copy(uri = Uri.fromFile(original)), source.crc32)
        val token = BuildCancellation(); val aborts = mutableListOf<String?>(); var request: Intent? = null
        try {
            WebCompanionCreation.launch(activity, target, 81, token) { aborts += it }
            awaitUi { request = shadowOf(activity).nextStartedActivityForResult?.intent; request != null }
            assertEquals(AutomaticCompanionActivity::class.java.name, request!!.component?.className)
            assertEquals(target, WebCompanionCreation.source(request!!)); assertTrue(aborts.isEmpty())
            token.cancel()
            assertThrows(IllegalStateException::class.java) { WebCompanionCreation.takeOperation(request!!) }
            val cancelledChild = Robolectric.buildActivity(AutomaticCompanionActivity::class.java, request)
            cancelledChild.get().distributionFactory = { error("Cancelled request must not resolve a builder") }
            cancelledChild.setup()
            assertTrue(cancelledChild.get().isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(cancelledChild.get()).resultCode)
            cancelledChild.pause().stop().destroy()
            awaitUi { request!!.getStringExtra("builder.rom")?.let { !File(it).exists() } == true }
        } finally {
            request?.getStringExtra("builder.rom")?.let { File(it).delete() }
            original.delete(); controller.pause().stop().destroy()
        }
    }

    @Test fun cancelDuringStagingReleasesTheCallerAndDeletesPartialRomWithoutLaunching() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup(); val activity = controller.get()
        val enteredRead = CountDownLatch(1); val releaseRead = CountDownLatch(1)
        val input = object : java.io.ByteArrayInputStream(ByteArray(1024)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                enteredRead.countDown()
                check(releaseRead.await(5, TimeUnit.SECONDS))
                return super.read(buffer, offset, length)
            }
        }
        shadowOf(activity.contentResolver).registerInputStream(entry.uri!!, input)
        val token = BuildCancellation(); val aborts = mutableListOf<String?>()
        try {
            WebCompanionCreation.launch(activity, source, 81, token) { aborts += it }
            assertTrue(enteredRead.await(5, TimeUnit.SECONDS))
            token.cancel(); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf<String?>(null), aborts)
            releaseRead.countDown()
            awaitUi { File(activity.cacheDir, "companion-builder-input").listFiles()?.isEmpty() == true }
            assertEquals(1, aborts.size); assertNull(shadowOf(activity).nextStartedActivityForResult)
        } finally { releaseRead.countDown(); controller.pause().stop().destroy() }
    }
}
