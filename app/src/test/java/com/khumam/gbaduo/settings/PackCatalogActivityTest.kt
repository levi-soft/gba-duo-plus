package com.khumam.gbaduo.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.khumam.gbaduo.R
import com.khumam.gbaduo.companion.PackCatalog
import com.khumam.gbaduo.companion.PackStore
import com.khumam.gbaduo.companion.builder.BuildCancellation
import com.khumam.gbaduo.companion.builder.BuilderProfile
import com.khumam.gbaduo.companion.builder.WebCompanionCreation
import com.khumam.gbaduo.library.RomEntry
import com.khumam.gbaduo.library.artwork.Http
import com.khumam.gbaduo.library.artwork.HttpResponse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PackCatalogActivityTest {
    private val entry = RomEntry(Uri.parse("content://fixture/game.gba"), "Friendly filename.gba", "RAW HEADER", "AE7E")
    private val profile = BuilderProfile("fixture", "Exact ROM profile", "1.0.0", "AE7E", 123L)
    private val catalog = """{"formatVersion":1,"companions":[{"id":"download-pack","title":"Download fixture","version":"1.0.0","download":"https://fixture/download.zip","games":[{"gameCode":"AE7E"}]}]}"""
    private val emptyCatalog = """{"formatVersion":1,"companions":[]}"""

    private fun intent() = Intent(RuntimeEnvironment.getApplication(), PackCatalogActivity::class.java)
        .putExtra(PackCatalogActivity.EXTRA_GAME_CODE, entry.gameCode)
        .putExtra(PackCatalogActivity.EXTRA_TITLE, "Friendly filename")
        .putExtra(PackCatalogActivity.EXTRA_REQUEST_ID, "request-fixture")
        .also { WebCompanionCreation.attachSource(it, entry, 123L) }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private fun text(activity: Activity) = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
    private fun card(activity: Activity, title: String): View = descendants(activity.window.decorView).filterIsInstance<TextView>()
        .single { it.text.toString().equals(title, ignoreCase = true) }.let { (it.parent as View).parent as View }

    private fun awaitUi(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        } while (System.nanoTime() < deadline)
        assertTrue("Expected catalog UI was not published", condition())
    }

    private fun awaitClosedWorkers(activity: PackCatalogActivity) {
        val field = PackCatalogActivity::class.java.getDeclaredField("io").apply { isAccessible = true }
        assertTrue("Closed catalog worker did not exit", (field.get(activity) as ExecutorService).awaitTermination(5, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun deliverResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        ReflectionHelpers.callInstanceMethod<Void>(activity, "onActivityResult",
            ClassParameter.from(Int::class.javaPrimitiveType!!, requestCode),
            ClassParameter.from(Int::class.javaPrimitiveType!!, resultCode),
            ClassParameter.from(Intent::class.java, data))
    }

    private fun http(handler: (String) -> HttpResponse) = object : Http {
        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            assertTrue(headers.isEmpty())
            return handler(url)
        }
    }

    private fun zipBytes(crc: String = "0000007B"): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            val manifest = """{"id":"download-pack","name":"Installed fixture","version":"1.0.0","formatVersion":2,"match":{"gameCode":["AE7E"],"crc32":["$crc"]},"layout":"layout.json"}"""
            for ((name, body) in mapOf("manifest.json" to manifest, "layout.json" to """{"type":"text","text":"Fixture"}""")) {
                zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
            }
        }
    }.toByteArray()

    @Test fun exactProfileAndOfficialPacksAppearOnSamePageWithoutStartingCreator() {
        val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
        val requests = CopyOnWriteArrayList<String>()
        val launches = AtomicInteger()
        controller.get().httpFactory = { http { url -> requests += url; HttpResponse(200, catalog.toByteArray()) } }
        controller.get().creationFinder = { _, source, _ ->
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            assertEquals(entry.title, source.entry.title)
            assertEquals(entry.uri, source.entry.uri)
            assertEquals(123L, source.crc32)
            profile
        }
        controller.get().creationLauncher = { _, _, _, _, _ -> launches.incrementAndGet(); Unit }
        controller.setup()
        try {
            awaitUi { text(controller.get()).contains("DOWNLOAD FIXTURE") && text(controller.get()).contains("CREATE COMPANION") }
            assertTrue(text(controller.get()).any { it.contains(profile.name) })
            assertTrue(text(controller.get()).any { it.equals("Downloadable packs", ignoreCase = true) })
            assertTrue(text(controller.get()).any { it.equals("Create from your ROM", ignoreCase = true) })
            assertEquals(listOf(PackCatalog.URL), requests.toList())
            assertEquals(0, launches.get())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun officialFailureDoesNotHideCreationAndEmptyOfficialListDoesNotSayNoPacks() {
        for (response in listOf(HttpResponse(503, byteArrayOf()), HttpResponse(200, emptyCatalog.toByteArray()))) {
            val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
            controller.get().httpFactory = { http { response } }
            controller.get().creationFinder = { _, _, _ -> profile }
            controller.setup()
            try {
                awaitUi { text(controller.get()).contains("CREATE COMPANION") && !text(controller.get()).contains(controller.get().getString(R.string.pack_web_loading)) }
                assertFalse(text(controller.get()).any { it.startsWith("No packs for ") })
                assertTrue(card(controller.get(), "Create companion").isEnabled)
            } finally { controller.pause().stop().destroy() }
        }
    }

    @Test fun creationLookupFailureDoesNotHideOfficialDownloads() {
        val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
        controller.get().httpFactory = { http { HttpResponse(200, catalog.toByteArray()) } }
        controller.get().creationFinder = { _, _, _ -> throw IOException("offline creation metadata") }
        controller.setup()
        try {
            awaitUi { text(controller.get()).contains("DOWNLOAD FIXTURE") && text(controller.get()).contains(controller.get().getString(R.string.plus_web_creation_failed)) }
            assertTrue(card(controller.get(), "Download fixture").isEnabled)
            assertFalse(text(controller.get()).contains("CREATE COMPANION"))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun unsupportedOrWrongRevisionNeverOffersCreateButton() {
        for (answer in listOf(null, profile.copy(crc32 = 124L), profile.copy(gameCode = "AE7J"))) {
            val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
            controller.get().httpFactory = { http { HttpResponse(200, catalog.toByteArray()) } }
            controller.get().creationFinder = { _, _, _ -> answer }
            controller.setup()
            try {
                awaitUi { text(controller.get()).contains("DOWNLOAD FIXTURE") && !text(controller.get()).any { it.contains("Checking creation") } }
                assertFalse(text(controller.get()).contains("CREATE COMPANION"))
            } finally { controller.pause().stop().destroy() }
        }
    }

    @Test fun officialCatalogWithoutGbaSourceStillWorksAndNeverStartsCreationLookup() {
        val withoutSource = Intent(RuntimeEnvironment.getApplication(), PackCatalogActivity::class.java)
            .putExtra(PackCatalogActivity.EXTRA_GAME_CODE, "AE7E")
        val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, withoutSource)
        val lookups = AtomicInteger()
        controller.get().httpFactory = { http { HttpResponse(200, catalog.toByteArray()) } }
        controller.get().creationFinder = { _, _, _ -> lookups.incrementAndGet(); profile }
        controller.setup()
        try {
            awaitUi { text(controller.get()).contains("DOWNLOAD FIXTURE") }
            assertEquals(0, lookups.get())
            assertFalse(text(controller.get()).contains("CREATE COMPANION"))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun stalledOfficialRequestDoesNotBlockCreationAndDestroyRejectsLateMetadata() {
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
        controller.get().httpFactory = { http {
            try { awaitUninterruptibly(release); HttpResponse(200, catalog.toByteArray()) }
            finally { returned.countDown() }
        } }
        controller.get().creationFinder = { _, _, _ -> profile }
        controller.setup()
        try {
            awaitUi { text(controller.get()).contains("CREATE COMPANION") }
            assertFalse(text(controller.get()).contains("DOWNLOAD FIXTURE"))
            controller.pause().stop().destroy()
            val before = text(controller.get())
            release.countDown()
            assertTrue(returned.await(5, TimeUnit.SECONDS))
            awaitClosedWorkers(controller.get())
            assertEquals(before, text(controller.get()))
            assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).resultCode)
        } finally { release.countDown() }
    }

    @Test fun officialDownloadStillInstallsThroughExistingPipelineAndEchoesBoundTarget() {
        val requests = CopyOnWriteArrayList<String>()
        val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
        controller.get().httpFactory = { http { url ->
            requests += url
            HttpResponse(200, if (url == PackCatalog.URL) catalog.toByteArray() else zipBytes())
        } }
        controller.get().creationFinder = { _, _, _ -> null }
        controller.setup()
        try {
            awaitUi { text(controller.get()).contains("DOWNLOAD FIXTURE") }
            card(controller.get(), "Download fixture").performClick()
            awaitUi { controller.get().isFinishing }
            val result = shadowOf(controller.get()).resultIntent
            assertEquals(Activity.RESULT_OK, shadowOf(controller.get()).resultCode)
            assertEquals("download-pack", result.getStringExtra(PackCatalogActivity.EXTRA_PACK_ID))
            assertEquals("request-fixture", result.getStringExtra(PackCatalogActivity.EXTRA_REQUEST_ID))
            assertEquals(entry.uri, WebCompanionCreation.source(result)!!.entry.uri)
            assertEquals(entry.title, WebCompanionCreation.source(result)!!.entry.title)
            assertEquals(listOf(PackCatalog.URL, "https://fixture/download.zip"), requests.toList())
            assertNotNull(PackStore(File(controller.get().filesDir, "packs")).byId("download-pack"))
            assertFalse(File(controller.get().cacheDir, "web-download-pack.zip").exists())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun createExcludesEveryOtherActionUntilAbortedAndForwardsSuccessfulResult() {
        val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
        val requests = CopyOnWriteArrayList<String>()
        controller.get().httpFactory = { http { url -> requests += url; HttpResponse(200, catalog.toByteArray()) } }
        controller.get().creationFinder = { _, _, _ -> profile }
        var abort: ((String?) -> Unit)? = null
        var createRequest = -1
        val launches = AtomicInteger()
        controller.get().creationLauncher = { _, source, requestCode, _, onAborted ->
            assertEquals(entry.title, source.entry.title)
            createRequest = requestCode; abort = onAborted; launches.incrementAndGet(); Unit
        }
        controller.setup()
        try {
            awaitUi { text(controller.get()).contains("DOWNLOAD FIXTURE") && text(controller.get()).contains("CREATE COMPANION") }
            val download = card(controller.get(), "Download fixture")
            val create = card(controller.get(), "Create companion")
            create.performClick()
            assertFalse(download.isEnabled)
            assertFalse(create.isEnabled)
            download.performClick(); create.performClick()
            assertEquals(1, launches.get())
            assertEquals(listOf(PackCatalog.URL), requests.toList())
            abort!!.invoke(null)
            assertTrue(download.isEnabled)
            assertTrue(create.isEnabled)
            create.performClick()
            File(controller.get().filesDir, "packs/created-pack").apply { mkdirs() }.resolve("manifest.json").writeText(
                """{"id":"created-pack","name":"Created","version":"1.0.0","formatVersion":2,"match":{"gameCode":["AE7E"],"crc32":["0000007B"]},"layout":"layout.json"}""")
            deliverResult(controller.get(), createRequest, Activity.RESULT_OK,
                WebCompanionCreation.resultIntent("created-pack", WebCompanionCreation.source(intent())!!))
            assertTrue(controller.get().isFinishing)
            assertEquals("created-pack", shadowOf(controller.get()).resultIntent.getStringExtra(PackCatalogActivity.EXTRA_PACK_ID))
            assertEquals("request-fixture", shadowOf(controller.get()).resultIntent.getStringExtra(PackCatalogActivity.EXTRA_REQUEST_ID))
            assertEquals(entry.uri, WebCompanionCreation.source(shadowOf(controller.get()).resultIntent)!!.entry.uri)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun creatorResultForAnotherSourceIsRejectedAndActionsBecomeAvailableAgain() {
        val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
        controller.get().httpFactory = { http { HttpResponse(200, catalog.toByteArray()) } }
        controller.get().creationFinder = { _, _, _ -> profile }
        var requestCode = -1
        controller.get().creationLauncher = { _, _, request, _, _ -> requestCode = request }
        controller.setup()
        try {
            awaitUi { text(controller.get()).contains("CREATE COMPANION") && text(controller.get()).contains("DOWNLOAD FIXTURE") }
            card(controller.get(), "Create companion").performClick()
            val wrongSource = WebCompanionCreation.Source(entry.copy(uri = Uri.parse("content://fixture/other.gba")), 123L)
            deliverResult(controller.get(), requestCode, Activity.RESULT_OK, WebCompanionCreation.resultIntent("created-pack", wrongSource))
            assertFalse(controller.get().isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).resultCode)
            assertTrue(card(controller.get(), "Download fixture").isEnabled)
            assertTrue(card(controller.get(), "Create companion").isEnabled)
        } finally { controller.pause().stop().destroy() }
    }

    private fun awaitUninterruptibly(latch: CountDownLatch) {
        var complete = false
        while (!complete) try { complete = latch.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { /* late network response */ }
    }

    @Test fun creationCardArrivingDuringDownloadCannotStartASecondOperation() {
        val releaseProfile = CountDownLatch(1)
        val releaseDownload = CountDownLatch(1)
        val downloading = CountDownLatch(1)
        val launches = AtomicInteger()
        val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
        controller.get().httpFactory = { http { url ->
            if (url == PackCatalog.URL) HttpResponse(200, catalog.toByteArray()) else {
                downloading.countDown()
                awaitUninterruptibly(releaseDownload)
                HttpResponse(200, zipBytes())
            }
        } }
        controller.get().creationFinder = { _, _, _ -> awaitUninterruptibly(releaseProfile); profile }
        controller.get().creationLauncher = { _, _, _, _, _ -> launches.incrementAndGet(); Unit }
        controller.setup()
        try {
            awaitUi { text(controller.get()).contains("DOWNLOAD FIXTURE") }
            card(controller.get(), "Download fixture").performClick()
            assertTrue(downloading.await(5, TimeUnit.SECONDS))
            releaseProfile.countDown()
            awaitUi { text(controller.get()).contains("CREATE COMPANION") }
            val create = card(controller.get(), "Create companion")
            assertFalse(create.isEnabled)
            create.performClick()
            assertEquals(0, launches.get())
            controller.get().finish()
            releaseDownload.countDown()
            awaitClosedWorkers(controller.get())
        } finally {
            releaseProfile.countDown(); releaseDownload.countDown()
            controller.pause().stop().destroy()
        }
    }

    @Test fun backDuringDownloadRejectsLateBytesWithoutInstallingOrReturningSuccess() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
        controller.get().httpFactory = { http { url ->
            if (url == PackCatalog.URL) HttpResponse(200, catalog.toByteArray()) else {
                started.countDown()
                try { awaitUninterruptibly(release); HttpResponse(200, zipBytes()) } finally { completed.countDown() }
            }
        } }
        controller.get().creationFinder = { _, _, _ -> null }
        controller.setup()
        try {
            awaitUi { text(controller.get()).contains("DOWNLOAD FIXTURE") }
            card(controller.get(), "Download fixture").performClick()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            controller.get().finish()
            release.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            awaitClosedWorkers(controller.get())
            assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).resultCode)
            assertNull(PackStore(File(controller.get().filesDir, "packs")).byId("download-pack"))
        } finally { release.countDown(); controller.pause().stop().destroy() }
    }

    @Test fun destroyCancelsCreationTokenAndLateAbortOrResultCannotReturnPack() {
        val controller = Robolectric.buildActivity(PackCatalogActivity::class.java, intent())
        controller.get().httpFactory = { http { HttpResponse(200, emptyCatalog.toByteArray()) } }
        controller.get().creationFinder = { _, _, _ -> profile }
        var cancellation: BuildCancellation? = null
        var abort: ((String?) -> Unit)? = null
        var requestCode = -1
        controller.get().creationLauncher = { _, _, request, token, onAborted ->
            cancellation = token; abort = onAborted; requestCode = request
        }
        controller.setup()
        awaitUi { text(controller.get()).contains("CREATE COMPANION") }
        card(controller.get(), "Create companion").performClick()
        controller.pause().stop().destroy()
        assertTrue(cancellation!!.cancelled)
        abort!!.invoke("late failure")
        deliverResult(controller.get(), requestCode, Activity.RESULT_OK, Intent().putExtra(PackCatalogActivity.EXTRA_PACK_ID, "created-pack"))
        assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).resultCode)
    }
}
