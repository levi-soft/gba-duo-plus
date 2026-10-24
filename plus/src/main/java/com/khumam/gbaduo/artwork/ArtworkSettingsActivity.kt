package com.khumam.gbaduo.artwork

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.TextView
import com.khumam.gbaduo.library.ui.Pixel
import com.khumam.gbaduo.settings.PixelPage
import java.util.concurrent.Executors

/** Source setup is independent of emulator and RetroAchievements settings. */
class ArtworkSettingsActivity : PixelPage() {
    private val worker = Executors.newSingleThreadExecutor()
    private val gate = ArtworkRequestGate()
    private lateinit var credentials: ArtworkCredentials
    private lateinit var status: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        page("Artwork sources")
        credentials = ArtworkCredentials(this)
        note("Libretro artwork is available without an account. Find covers automatically, choose images from SteamGridDB, or import your own image.", 0)
        content.addView(button("Scrape missing artwork", Pixel.YELLOW) { ArtworkBatch.show(this) }, spaced(16))
        note("Bulk matching uses Libretro's thumbnail library. Only a unique matching game is selected. Existing artwork is kept; ambiguous names and unsupported games need manual review.")
        note("SteamGridDB (optional)", 24).apply { setTextColor(Pixel.YELLOW); textSize = 17f }
        val key = field("SteamGridDB API key", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, true)
        content.addView(button("Get SteamGridDB API key", Pixel.YELLOW) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.steamgriddb.com/profile/preferences/api"))) }
        }, spaced(12))
        note("Sign in on SteamGridDB, create a personal API key, then paste it above. Your key is stored encrypted on this device.")
        val keySave = button("Save SteamGridDB key", Pixel.LAVENDER) {
            val enteredKey = key.text.toString()
            save { credentials.saveSteamGridDbKey(enteredKey) }
        }
        content.addView(keySave, spaced(12))
        note("To use a local image, open a game's Artwork menu and choose image. Preview it before applying. No account is required.", 24)
        status = note("Libretro and manual import work without a SteamGridDB key.", 16)
        val request = gate.begin()
        worker.execute {
            val stored = runCatching { credentials.steamGridDbKey() }
            runOnUiThread {
                if (gate.isCurrent(request)) stored.onSuccess { steam ->
                    // Do not overwrite input the user already entered while Keystore was loading.
                    if (key.text.isEmpty()) key.setText(steam)
                }.onFailure { status.text = "Saved source credentials could not be opened. Enter them again." }
            }
        }
        focusFirst(key)
    }

    private fun save(action: () -> Unit) {
        val token = gate.begin(); status.text = "Saving source settings…"
        worker.execute {
            val result = runCatching { gate.commit(token, action) }
            runOnUiThread { if (gate.isCurrent(token)) status.text = if (result.isSuccess) "Source settings saved." else "Could not save source settings. Please try again." }
        }
    }

    override fun finish() {
        gate.close()
        super.finish()
    }

    override fun onDestroy() {
        gate.close(); worker.shutdownNow(); super.onDestroy()
    }
}
