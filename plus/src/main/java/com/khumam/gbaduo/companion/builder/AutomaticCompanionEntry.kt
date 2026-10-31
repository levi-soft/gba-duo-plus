package com.khumam.gbaduo.companion.builder

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import android.widget.Toast
import com.khumam.gbaduo.R
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.library.RomEntry
import com.khumam.gbaduo.storage.GameStorage
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Shared confirmation/staging for catalog creation and the legacy internal caller. */
object AutomaticCompanionEntry {
    fun launch(activity: Activity, entry: RomEntry, storage: GameStorage, crc32: Long) {
        prepare(activity, entry, storage, BuildCancellation(), { reason ->
            if (reason != null) Toast.makeText(activity, activity.getString(R.string.plus_builder_failed, reason), Toast.LENGTH_LONG).show()
        }) { file, romName -> AutomaticCompanionActivity.launch(activity, file, entry.title, entry.gameCode, crc32, romFileName = romName) }
    }

    internal fun prepare(activity: Activity, entry: RomEntry, storage: GameStorage,
        cancellation: BuildCancellation, onAborted: (String?) -> Unit, onPrepared: (File, String) -> Unit) {
        val finished = AtomicBoolean(false)
        var dialog: AlertDialog? = null
        var cancelListener: Closeable? = null
        lateinit var lifecycle: Application.ActivityLifecycleCallbacks
        fun cleanUp() {
            cancelListener?.close(); cancelListener = null
            dialog?.dismiss(); dialog = null
            activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
        }
        fun abort(reason: String?) {
            if (!finished.compareAndSet(false, true)) return
            cancellation.cancel(); cleanUp(); onAborted(reason)
        }
        fun isGone() = activity.isDestroyed || activity.isFinishing
        lifecycle = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(owner: Activity) { if (owner === activity) abort(null) }
            override fun onActivityCreated(owner: Activity, state: Bundle?) {}
            override fun onActivityStarted(owner: Activity) {}
            override fun onActivityResumed(owner: Activity) {}
            override fun onActivityPaused(owner: Activity) {}
            override fun onActivityStopped(owner: Activity) {}
            override fun onActivitySaveInstanceState(owner: Activity, state: Bundle) {}
        }
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
        cancelListener = cancellation.onCancel { activity.runOnUiThread { abort(null) } }
        if (finished.get()) return
        if (isGone()) { abort(null); return }
        if (entry.platform != Platform.GBA || entry.uri == null) {
            abort(activity.getString(R.string.plus_builder_gba_only)); return
        }
        val start = start@{
            if (finished.get() || cancellation.cancelled || isGone()) { abort(null); return@start }
            dialog = AlertDialog.Builder(activity).setTitle(R.string.plus_builder_title)
                .setMessage(R.string.plus_builder_copying)
                .setNegativeButton(R.string.plus_builder_cancel) { _, _ -> abort(null) }
                .setOnCancelListener { abort(null) }.show()
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                var staged: File? = null
                val result = runCatching {
                    cancellation.check()
                    val dir = File(activity.cacheDir, "companion-builder-input").apply { mkdirs() }
                    val file = File.createTempFile("rom-", ".gba", dir); staged = file
                    val romName = activity.contentResolver.openInputStream(entry.uri)?.use { input ->
                        file.outputStream().use { output -> BuilderRomSource.copy(entry.fileName, input, output, cancelled = { cancellation.cancelled }) }
                    } ?: error("Could not read ROM")
                    cancellation.check(); file to romName
                }
                activity.runOnUiThread {
                    if (finished.get() || cancellation.cancelled || isGone()) { staged?.delete(); abort(null) }
                    else result.onSuccess { (file, romName) ->
                        try {
                            cancellation.check(); onPrepared(file, romName)
                            if (finished.compareAndSet(false, true)) cleanUp()
                        } catch (error: Exception) {
                            file.delete(); abort(if (cancellation.cancelled) null else error.message ?: "Could not start creation")
                        }
                    }.onFailure { error -> staged?.delete(); abort(error.message ?: "Could not prepare ROM") }
                }
                executor.shutdown()
            }
        }
        if (storage.packId == null) start()
        else dialog = AlertDialog.Builder(activity).setTitle(R.string.plus_builder_title).setMessage(R.string.plus_builder_existing)
            .setNegativeButton(R.string.plus_builder_cancel) { _, _ -> abort(null) }
            .setOnCancelListener { abort(null) }
            .setPositiveButton(R.string.plus_builder_create) { _, _ -> start() }.show()
    }
}
