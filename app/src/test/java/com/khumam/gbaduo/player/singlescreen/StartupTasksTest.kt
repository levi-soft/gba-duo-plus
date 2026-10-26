package com.khumam.gbaduo.player.singlescreen

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class StartupTasksTest {
    @Test fun slowRomReadDoesNotBlockUiAndDeliveryHappensOnlyOnUiQueue() {
        val messages = LinkedBlockingQueue<() -> Unit>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var result = 0
        val tasks = StartupTasks(post = { messages.add(it) })
        try {
            tasks.submit({ entered.countDown(); check(release.await(3, TimeUnit.SECONDS)); 42 }) { result = it.getOrThrow() }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertEquals(0, result)
            assertTrue(messages.isEmpty())
            release.countDown()
            val deliver = messages.poll(2, TimeUnit.SECONDS)
            assertNotNull(deliver)
            assertEquals(0, result)
            deliver!!()
            assertEquals(42, result)
        } finally { release.countDown(); tasks.close() }
    }

    @Test fun closingDuringStartupDropsLateDeliveryAndDoesNotWaitForUi() {
        val messages = LinkedBlockingQueue<() -> Unit>()
        var delivered = false
        val tasks = StartupTasks(post = { messages.add(it) })
        tasks.submit({ 1 }) { delivered = true }
        val callback = messages.poll(2, TimeUnit.SECONDS)
        assertNotNull(callback)
        tasks.close()
        callback!!()
        tasks.submit({ error("Must not run") }) { delivered = true }
        assertFalse(delivered)
    }

    @Test fun readErrorsAreDeliveredAsFailuresInsteadOfLeavingStartupPending() {
        val messages = LinkedBlockingQueue<() -> Unit>()
        val tasks = StartupTasks(post = { messages.add(it) })
        var failed = false
        try {
            tasks.submit({ error("Unreadable ROM") }) { failed = it.isFailure }
            messages.poll(2, TimeUnit.SECONDS)!!()
            assertTrue(failed)
        } finally { tasks.close() }
    }
}
