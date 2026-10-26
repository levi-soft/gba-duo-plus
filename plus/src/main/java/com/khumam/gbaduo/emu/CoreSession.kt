package com.khumam.gbaduo.emu

/** NativeCore is process-global: a replacement game waits for the old owner to finish. */
internal class CoreSession {
    @Volatile var stopped = false
        private set

    fun stop() { stopped = true }

    fun runExclusive(block: () -> Unit) {
        synchronized(owner) {
            if (!stopped) block()
        }
    }

    companion object {
        private val owner = Any()
    }
}
