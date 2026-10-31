package com.khumam.gbaduo.library

import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.khumam.gbaduo.R
import com.khumam.gbaduo.companion.PackManifest
import com.khumam.gbaduo.companion.builder.WebCompanionCreation
import com.khumam.gbaduo.settings.PackCatalogActivity
import com.khumam.gbaduo.storage.GameStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompanionMenuIntegrationTest {
    private val entry = RomEntry(Uri.parse("content://fixture/Fixture.gba"), "Fixture.gba", "RAW HEADER", "AE7E")

    private fun menu(matches: List<PackManifest> = emptyList(), check: (LibraryActivity, GameStorage, AlertDialog) -> Unit) {
        // Exercise the actual menu/callback without starting library scans or a native core.
        val activity = Robolectric.buildActivity(LibraryActivity::class.java).get()
        val storage = GameStorage.forGame(activity.filesDir, Platform.GBA, entry.title, entry.gameCode, 123L)
        LibraryActivity::class.java.getDeclaredMethod(
            "showCompanionDialog", RomEntry::class.java, java.lang.Long.TYPE,
            GameStorage::class.java, List::class.java,
        ).apply { isAccessible = true }.invoke(activity, entry, 123L, storage, matches)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        try { check(activity, storage, dialog) } finally { dialog.dismiss() }
    }

    private fun choose(dialog: AlertDialog, label: String) {
        val list = dialog.listView
        val index = (0 until list.adapter.count).single { list.adapter.getItem(it).toString() == label }
        list.performItemClick(null, index, list.adapter.getItemId(index))
    }

    private fun deliverResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        ReflectionHelpers.callInstanceMethod<Void>(activity, "onActivityResult",
            ClassParameter.from(Int::class.javaPrimitiveType!!, requestCode),
            ClassParameter.from(Int::class.javaPrimitiveType!!, resultCode),
            ClassParameter.from(Intent::class.java, data))
    }

    @Test fun importStillOpensDocumentPickerAfterAddingWebCatalog() = menu { activity, _, dialog ->
        choose(dialog, activity.getString(R.string.import_pack))
        val started = shadowOf(activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.action)
        assertEquals("application/zip", started.type)
    }

    @Test fun webCatalogUsesOfficialActivityAndImmutableRawRomIdentity() = menu { activity, _, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val started = shadowOf(activity).nextStartedActivityForResult.intent
        assertEquals(PackCatalogActivity::class.java.name, started.component?.className)
        assertEquals("AE7E", started.getStringExtra(PackCatalogActivity.EXTRA_GAME_CODE))
        val source = WebCompanionCreation.source(started)!!
        assertEquals(entry.uri, source.entry.uri)
        assertEquals(entry.fileName, source.entry.fileName)
        assertEquals("RAW HEADER", source.entry.title)
        assertEquals("AE7E", source.entry.gameCode)
        assertEquals(123L, source.crc32)
        assertFalse(started.getStringExtra(PackCatalogActivity.EXTRA_REQUEST_ID).isNullOrBlank())
    }

    @Test fun companionMenuHasOneWebDiscoveryEntryAndNoSeparateCreator() = menu { activity, _, dialog ->
        val labels = (0 until dialog.listView.adapter.count).map { dialog.listView.adapter.getItem(it).toString() }
        assertEquals(listOf(activity.getString(R.string.no_pack), activity.getString(R.string.import_pack),
            activity.getString(R.string.pack_web_get)), labels)
        assertFalse(labels.any { it.contains("Find and create", ignoreCase = true) || it == activity.getString(R.string.plus_builder_create) })
    }

    @Test fun matchingInstalledPackStillAssignsItsOwnId() {
        val pack = PackManifest.parse("""{"id":"fixture-pack","name":"Fixture pack","version":"1.0.0","formatVersion":2,"match":{"gameCode":["AE7E"]},"layout":"layout.json"}""")
        menu(listOf(pack)) { _, storage, dialog ->
            dialog.listView.performItemClick(null, 1, 1L)
            assertEquals(pack.id, storage.packId)
        }
    }

    private fun installed(activity: LibraryActivity, id: String = "web-pack", crc: String = "0000007B") {
        File(activity.filesDir, "packs/$id").apply { mkdirs() }.resolve("manifest.json").writeText(
            """{"id":"$id","name":"Web pack","version":"1.0.0","formatVersion":2,"match":{"gameCode":["AE7E"],"crc32":["$crc"]},"layout":"layout.json"}""")
    }

    @Test fun acceptsMatchingInstalledResultForSameRequestOnlyOnce() = menu { activity, storage, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val request = shadowOf(activity).nextStartedActivityForResult
        installed(activity)
        val result = Intent(request.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "web-pack")
        deliverResult(activity, request.requestCode, Activity.RESULT_OK, result)
        assertEquals("web-pack", storage.packId)
        storage.packId = "manual-choice"
        deliverResult(activity, request.requestCode, Activity.RESULT_OK, result)
        assertEquals("manual-choice", storage.packId)
    }

    @Test fun changedAssignmentCannotBeOverwrittenByLateResult() = menu { activity, storage, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val request = shadowOf(activity).nextStartedActivityForResult
        installed(activity)
        storage.packId = "manual-choice"
        deliverResult(activity, request.requestCode, Activity.RESULT_OK,
            Intent(request.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "web-pack"))
        assertEquals("manual-choice", storage.packId)
    }

    @Test fun rejectsInstalledPackForWrongRevision() = menu { activity, storage, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val request = shadowOf(activity).nextStartedActivityForResult
        installed(activity, crc = "0000007C")
        val wrongRevision = Intent(request.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "web-pack")
        deliverResult(activity, request.requestCode, Activity.RESULT_OK, wrongRevision)
        assertNull(storage.packId)
    }

    @Test fun staleRequestNonceCannotConsumeCurrentResult() = menu { activity, storage, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val request = shadowOf(activity).nextStartedActivityForResult
        installed(activity)
        val result = Intent(request.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "web-pack")
        deliverResult(activity, request.requestCode, Activity.RESULT_OK,
            Intent(result).putExtra(PackCatalogActivity.EXTRA_REQUEST_ID, "obsolete-request"))
        assertNull(storage.packId)
        deliverResult(activity, request.requestCode, Activity.RESULT_OK, result)
        assertEquals("web-pack", storage.packId)
    }

    @Test fun previousCatalogCancellationCannotConsumeNewRequest() = menu { activity, storage, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val previous = shadowOf(activity).nextStartedActivityForResult
        // A second request uses a distinct Activity result code as well as a nonce.
        LibraryActivity::class.java.getDeclaredMethod(
            "showCompanionDialog", RomEntry::class.java, java.lang.Long.TYPE,
            GameStorage::class.java, List::class.java,
        ).apply { isAccessible = true }.invoke(activity, entry, 123L, storage, emptyList<PackManifest>())
        val newer = ShadowAlertDialog.getLatestAlertDialog()
        try {
            choose(newer, activity.getString(R.string.pack_web_get))
            val current = shadowOf(activity).nextStartedActivityForResult
            assertNotEquals(previous.requestCode, current.requestCode)
            installed(activity)
            deliverResult(activity, previous.requestCode, Activity.RESULT_CANCELED, null)
            deliverResult(activity, current.requestCode, Activity.RESULT_OK,
                Intent(current.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "web-pack"))
            assertEquals("web-pack", storage.packId)
        } finally { newer.dismiss() }
    }

    @Test fun returnedRawSourceCannotPointAtAnotherRom() = menu { activity, storage, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val request = shadowOf(activity).nextStartedActivityForResult
        installed(activity)
        val result = Intent(request.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "web-pack")
        WebCompanionCreation.attachSource(result, entry.copy(uri = Uri.parse("content://fixture/other.gba")), 123L)
        deliverResult(activity, request.requestCode, Activity.RESULT_OK, result)
        assertNull(storage.packId)
    }

    @Test fun canceledRequestCannotLaterAssignEvenWhenResultHasCorrectIdentity() = menu { activity, storage, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val request = shadowOf(activity).nextStartedActivityForResult
        installed(activity)
        deliverResult(activity, request.requestCode, Activity.RESULT_CANCELED, null)
        deliverResult(activity, request.requestCode, Activity.RESULT_OK,
            Intent(request.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "web-pack"))
        assertNull(storage.packId)
    }

    @Test fun finishingLibraryCannotAcceptSuccessfulResult() = menu { activity, storage, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val request = shadowOf(activity).nextStartedActivityForResult
        installed(activity)
        activity.finish()
        deliverResult(activity, request.requestCode, Activity.RESULT_OK,
            Intent(request.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "web-pack"))
        assertNull(storage.packId)
    }

    @Test fun uninstalledResultCannotSelectNonexistentPack() = menu { activity, storage, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val request = shadowOf(activity).nextStartedActivityForResult
        deliverResult(activity, request.requestCode, Activity.RESULT_OK,
            Intent(request.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "missing-pack"))
        assertNull(storage.packId)
    }

    @Test fun partialFinalAssignmentFailurePreservesPreviousBytesAndCleansTemporary() = menu { activity, storage, dialog ->
        storage.packId = "previous-pack"
        val assignment = File(storage.dir, "pack.json")
        val original = assignment.readBytes()
        choose(dialog, activity.getString(R.string.pack_web_get))
        val request = shadowOf(activity).nextStartedActivityForResult
        installed(activity)
        var temporary: File? = null
        activity.writeWebPackAssignmentTemporary = { file, _ ->
            temporary = file
            file.writeText("{\"packId\":\"partial")
            throw IOException("Disk full")
        }
        deliverResult(activity, request.requestCode, Activity.RESULT_OK,
            Intent(request.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "web-pack"))
        assertArrayEquals(original, assignment.readBytes())
        assertEquals("previous-pack", storage.packId)
        assertNotNull(temporary)
        assertFalse(temporary!!.exists())
        assertTrue(ShadowToast.getTextOfLatestToast().toString().contains("Could not select companion"))
        assertTrue(ShadowToast.getTextOfLatestToast().toString().contains("Disk full"))
    }

    @Test fun failedFinalAssignmentForPreviouslyUnassignedRomLeavesItUnassigned() = menu { activity, storage, dialog ->
        choose(dialog, activity.getString(R.string.pack_web_get))
        val request = shadowOf(activity).nextStartedActivityForResult
        installed(activity)
        activity.writeWebPackAssignmentTemporary = { _, _ -> throw IOException("Permission denied") }
        deliverResult(activity, request.requestCode, Activity.RESULT_OK,
            Intent(request.intent).putExtra(PackCatalogActivity.EXTRA_PACK_ID, "web-pack"))
        assertNull(storage.packId)
        assertFalse(File(storage.dir, "pack.json").exists())
        assertFalse(storage.dir.listFiles().orEmpty().any { it.name.startsWith(".pack.json-") })
    }
}
