package com.khumam.gbaduo.companion.builder

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.khumam.gbaduo.R
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.library.RomEntry
import com.khumam.gbaduo.storage.GameStorage
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Removable Plus entry point; the official library only delegates here. */
object AutomaticCompanionEntry {
    fun launch(activity: Activity, entry: RomEntry, storage: GameStorage, crc32: Long) {
        if (entry.platform != Platform.GBA || entry.uri == null) {
            Toast.makeText(activity, R.string.plus_builder_gba_only, Toast.LENGTH_LONG).show(); return
        }
        val start = {
            val cancelled = AtomicBoolean(false)
            val dialog = AlertDialog.Builder(activity).setTitle(R.string.plus_builder_title)
                .setMessage(R.string.plus_builder_copying)
                .setNegativeButton(R.string.plus_builder_cancel) { _, _ -> cancelled.set(true) }
                .setOnCancelListener { cancelled.set(true) }.show()
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                var staged: File? = null
                val result = runCatching {
                    val dir = File(activity.cacheDir, "companion-builder-input").apply { mkdirs() }
                    val file = File.createTempFile("rom-", ".gba", dir); staged = file
                    activity.contentResolver.openInputStream(entry.uri)?.use { input ->
                        file.outputStream().use { output -> BuilderRomSource.copy(entry.fileName, input, output, cancelled = cancelled::get) }
                    } ?: error("Could not read ROM")
                    file
                }
                activity.runOnUiThread {
                    dialog.dismiss()
                    if (cancelled.get() || activity.isDestroyed || activity.isFinishing) staged?.delete()
                    else result.onSuccess { file ->
                        runCatching { AutomaticCompanionActivity.launch(activity, file, entry.title, entry.gameCode, crc32) }
                            .onFailure { file.delete(); Toast.makeText(activity, it.message, Toast.LENGTH_LONG).show() }
                    }.onFailure { staged?.delete(); Toast.makeText(activity, activity.getString(R.string.plus_builder_failed, it.message ?: ""), Toast.LENGTH_LONG).show() }
                }
                executor.shutdown()
            }
        }
        if (storage.packId == null) start()
        else AlertDialog.Builder(activity).setTitle(R.string.plus_builder_title).setMessage(R.string.plus_builder_existing)
            .setNegativeButton(R.string.plus_builder_cancel, null).setPositiveButton(R.string.plus_builder_create) { _, _ -> start() }.show()
    }
}
