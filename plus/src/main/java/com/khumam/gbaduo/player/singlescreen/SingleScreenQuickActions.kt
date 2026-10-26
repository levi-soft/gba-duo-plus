package com.khumam.gbaduo.player.singlescreen

import android.os.Handler
import android.os.Looper
import java.io.File

/** Slot 0 is dedicated to SS quick actions; the existing menu keeps manual slots 1–9. */
internal class SingleScreenQuickActions(
    private val ready: () -> Boolean,
    private val submit: (String, Boolean, (Boolean) -> Unit) -> Unit,
    private val path: String,
    private val feedback: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var closed = false

    fun save() = request(true)
    fun load() = request(false)

    private fun request(save: Boolean) {
        if (closed || busy || !ready()) return
        if (!save && !File(path).isFile) { feedback("No quick save yet."); return }
        busy = true
        submit(path, save) { success ->
            handler.post {
                if (!closed) {
                    busy = false
                    feedback(if (success) { if (save) "Quick save created." else "Quick save loaded." }
                        else { if (save) "Quick save failed." else "Quick load failed." })
                }
            }
        }
    }

    fun close() { closed = true }
}
