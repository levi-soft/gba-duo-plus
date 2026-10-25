package com.khumam.gbaduo.companion

import android.app.Activity
import android.content.DialogInterface
import com.khumam.gbaduo.companion.management.CompanionPackManagement
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompanionPackManagementTest {
    @Test fun managementListsVersionAndDeletesOnlyAfterConfirmation() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val folder = File(activity.filesDir, "packs/demo").apply { mkdirs() }
        File(folder, "manifest.json").writeText("""{"id":"demo","name":"FE7","version":"1.0.0",
            "formatVersion":1,"match":{"gameCode":["AE7E"],"crc32":["32CD990D"]},"layout":"layout.json"}""")
        var changed = false
        try {
            CompanionPackManagement.show(activity, "AE7E", 0x32cd990d) { changed = true }
            val list = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(shadowOf(list).items.single().toString().contains("v1.0.0"))
            shadowOf(list).clickOnItem(0)
            ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(folder.exists()); assertFalse(changed)
            CompanionPackManagement.show(activity, "AE7E", 0x32cd990d) { changed = true }
            shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0)
            ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertFalse(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()?.toString(), folder.exists()); assertTrue(changed)
        } finally {
            folder.deleteRecursively()
            controller.pause().stop().destroy()
        }
    }
}
