package com.khumam.gbaduo.companion.management

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.khumam.gbaduo.R
import com.khumam.gbaduo.companion.PackStore
import java.io.File
import java.util.concurrent.Executors

/** Plus management entry point; the library keeps its existing assignment menu. */
object CompanionPackManagement {
    private val cleanup = Executors.newSingleThreadExecutor()
    fun show(activity: Activity, gameCode: String, crc32: Long, onChanged: () -> Unit) {
        val packs = PackStore(File(activity.filesDir, "packs")).matching(gameCode, crc32)
        if (packs.isEmpty()) {
            Toast.makeText(activity, R.string.plus_packs_empty, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.plus_packs_manage)
            .setItems(InstalledPackStorage.labels(packs).toTypedArray()) { _, index ->
                val pack = packs[index]
                AlertDialog.Builder(activity)
                    .setTitle(R.string.plus_packs_delete)
                    .setMessage(activity.getString(R.string.plus_packs_delete_confirm, pack.name, pack.version))
                    .setNegativeButton(R.string.plus_packs_cancel, null)
                    .setPositiveButton(R.string.plus_packs_delete) { _, _ ->
                        // Serialized with automatic install commits and manual import.
                        runCatching {
                            InstalledPackStorage(activity.filesDir).delete(pack.id) { retired ->
                                cleanup.execute { retired.deleteRecursively() }
                            }
                        }
                            .onSuccess {
                                Toast.makeText(activity, R.string.plus_packs_deleted, Toast.LENGTH_SHORT).show()
                                onChanged()
                            }
                            .onFailure { Toast.makeText(activity, it.message, Toast.LENGTH_LONG).show() }
                    }.show()
            }
            .setNegativeButton(R.string.plus_packs_cancel, null)
            .show()
    }
}
