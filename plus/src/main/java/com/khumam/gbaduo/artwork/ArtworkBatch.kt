package com.khumam.gbaduo.artwork

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import com.khumam.gbaduo.library.GameTitle
import com.khumam.gbaduo.library.RomEntry
import com.khumam.gbaduo.library.RomLibrary
import java.util.concurrent.Executors

/** Explicit bulk operation: unique Libretro name matches only; existing choices always win. */
object ArtworkBatch {
    fun show(activity: Activity, entries: List<RomEntry>? = null) {
        AlertDialog.Builder(activity).setTitle("Scrape missing artwork?")
            .setMessage("Download missing artwork from Libretro. No account is needed. Only unique matching names are used; ambiguous or unsupported games are left for review. Existing images are kept.")
            .setNegativeButton("Cancel", null).setPositiveButton("Start") { _, _ -> start(activity, entries) }.show()
    }

    private fun start(activity: Activity, supplied: List<RomEntry>?) {
        val gate = ArtworkRequestGate(); val token = gate.begin()
        val dialog = AlertDialog.Builder(activity).setTitle("Scrape missing artwork").setMessage("Reading the library…")
            .setNegativeButton("Cancel") { _, _ -> gate.close() }.setOnCancelListener { gate.close() }.show()
        val lifecycle = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(target: Activity) { if (target === activity) gate.close() }
            override fun onActivityCreated(target: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(target: Activity) = Unit
            override fun onActivityResumed(target: Activity) = Unit
            override fun onActivityPaused(target: Activity) = Unit
            override fun onActivityStopped(target: Activity) = Unit
            override fun onActivitySaveInstanceState(target: Activity, state: Bundle) = Unit
        }
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
        val worker = Executors.newSingleThreadExecutor()
        worker.execute {
            var applied = 0; var retained = 0; var review = 0; var failed = 0; var stopped = false
            val result = runCatching {
                val library = supplied ?: RomLibrary(activity).list()
                val provider = LibretroArtworkProvider(); val repository = ArtworkRepository(activity)
                for ((index, entry) in library.withIndex()) {
                    if (!gate.isCurrent(token) || activity.isDestroyed || activity.isFinishing) break
                    activity.runOnUiThread { if (gate.isCurrent(token)) dialog.setMessage("${index + 1} / ${library.size}\n${GameTitle.display(entry.fileName, entry.title)}\nApplied $applied · Kept $retained · Review $review · Failed $failed") }
                    try {
                        val source = entry.uri ?: continue
                        val identity = activity.contentResolver.openInputStream(source)?.use { ArtworkRomReader.read(entry.fileName, it) { !gate.isCurrent(token) } }
                            ?: error("Could not read ROM")
                        val key = ArtworkIdentity(entry.platform.id, identity.crc32)
                        if (repository.has(key)) { retained++; continue }
                        val info = ArtworkRomInfo(key, GameTitle.display(entry.fileName, entry.title), identity.fileName, entry.gameCode, identity.size)
                        val game = provider.lookupRom(info)
                        if (game == null) { review++; continue }
                        val candidate = provider.images(game, entry.platform).firstOrNull()
                        if (candidate == null) { review++; continue }
                        if (!gate.isCurrent(token)) break
                        val bytes = ArtworkHttp.download(candidate)
                        // Staging is cancellable up to the final claim. The repository
                        // rechecks missing artwork after staging, so manual choices win.
                        val saved = repository.applyIfMissing(key, bytes, candidate) { gate.claim(token) }
                        if (saved != null) applied++ else if (gate.isCurrent(token)) retained++ else break
                    } catch (error: Exception) {
                        if (!gate.isCurrent(token)) break
                        failed++
                        if (error is ArtworkProviderException && error.statusCode == 429) {
                            // Stop rather than repeatedly consume a provider's quota or hammer a retry window.
                            stopped = true; break
                        }
                    }
                    if (gate.isCurrent(token)) Thread.sleep(400)
                }
            }
            activity.runOnUiThread {
                activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
                if (!activity.isDestroyed && !activity.isFinishing) {
                    val cancelled = !gate.isCurrent(token)
                    dialog.dismiss()
                    val heading = when { cancelled -> "Artwork scraping cancelled"; stopped -> "Provider limit reached; try again later"; result.isFailure -> "Artwork scraping stopped"; else -> "Artwork scraping finished" }
                    AlertDialog.Builder(activity).setTitle(heading)
                        .setMessage("Applied $applied · Kept $retained\nNeeds review $review · Failed $failed\n\nExisting artwork was preserved. Run again to continue with missing images.")
                        .setPositiveButton("OK", null).show()
                }
            }
            worker.shutdown()
        }
    }
}
