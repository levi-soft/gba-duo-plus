package com.khumam.gbaduo.player.singlescreen

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Session-owned startup I/O. Never waits for the worker from the UI thread. */
internal class StartupTasks(
    private val post: (() -> Unit) -> Unit,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "gbaduo-ss-startup") },
) {
    @Volatile private var closed = false

    fun <T> submit(work: () -> T, done: (Result<T>) -> Unit) {
        if (closed) return
        executor.execute {
            val result = runCatching(work)
            if (!closed) post { if (!closed) done(result) }
        }
    }

    fun close() {
        closed = true
        executor.shutdownNow()
    }
}
