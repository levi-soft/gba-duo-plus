package com.khumam.gbaduo.emu

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class CoreSessionTest {
    @Test fun stopDuringLoadingCannotBeUndoneAndNextGameWaitsForOldOwner() {
        val old = CoreSession()
        val next = CoreSession()
        val oldEntered = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        val nextStarted = CountDownLatch(1)
        val owners = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val first = Thread {
            old.runExclusive {
                maximum.accumulateAndGet(owners.incrementAndGet(), ::maxOf)
                oldEntered.countDown()
                check(finishLoad.await(3, TimeUnit.SECONDS))
                check(old.stopped)
                owners.decrementAndGet()
            }
        }
        val second = Thread {
            next.runExclusive {
                maximum.accumulateAndGet(owners.incrementAndGet(), ::maxOf)
                nextStarted.countDown()
                owners.decrementAndGet()
            }
        }
        first.start()
        try {
            assertTrue(oldEntered.await(2, TimeUnit.SECONDS))
            old.stop()
            second.start()
            assertFalse(nextStarted.await(50, TimeUnit.MILLISECONDS))
            finishLoad.countDown()
            assertTrue(nextStarted.await(2, TimeUnit.SECONDS))
            assertEquals(1, maximum.get())
            assertTrue(old.stopped)
        } finally {
            finishLoad.countDown()
            first.join(2000); second.join(2000)
        }
    }

    @Test fun aCancelledStartupNeverTouchesNativeCore() {
        val session = CoreSession()
        session.stop()
        session.runExclusive { fail("Stopped session entered native core") }
    }
}
