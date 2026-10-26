package com.khumam.gbaduo.player.singlescreen

import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SingleScreenQuickActionsTest {
    @Test fun missingSaveDoesNotLoadAndFeedbackWaitsForTheCorrectNativeCompletion() {
        val directory = Files.createTempDirectory("ss-quick-actions").toFile()
        val state = directory.resolve("slot0.ss")
        val messages = mutableListOf<String>()
        val requests = mutableListOf<Boolean>()
        var completion: ((Boolean) -> Unit)? = null
        val actions = SingleScreenQuickActions({ true }, { path, save, done ->
            assertEquals(state.path, path); requests += save; completion = done
        }, state.path, messages::add)
        try {
            actions.load(); assertEquals(listOf("No quick save yet."), messages); assertTrue(requests.isEmpty())
            actions.save(); actions.save(); actions.load()
            assertEquals(listOf(true), requests)
            assertEquals(1, messages.size)
            state.writeBytes(byteArrayOf(1))
            completion!!(true); shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Quick save created.", messages.last())
            actions.load(); assertEquals(listOf(true, false), requests)
            completion!!(false); shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Quick load failed.", messages.last())
            actions.save(); actions.close(); completion!!(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Quick load failed.", messages.last())
        } finally {
            actions.close(); state.delete(); directory.delete()
        }
    }
}
