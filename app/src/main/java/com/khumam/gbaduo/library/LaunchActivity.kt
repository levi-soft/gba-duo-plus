package com.khumam.gbaduo.library

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.khumam.gbaduo.player.PlayerActivity
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Upstream 0.1.6 launcher trampoline, with Dev's SAF/provider validation off the UI thread. */
class LaunchActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(incoming: Intent) {
        super.onNewIntent(incoming)
        setIntent(incoming)
        handleIntent(incoming)
    }

    private fun handleIntent(incoming: Intent) {
        val request = runCatching { LauncherRomResolver.request(incoming) }.getOrNull()
        if (request == null) {
            Toast.makeText(this, "Choose a supported ROM file: .gba, .gb, .gbc or .zip.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val ticket = ++generation
        val latest = requests.incrementAndGet()
        io.execute {
            val result = runCatching { LauncherRomResolver.resolve(this, request) }
            runOnUiThread {
                if (isDestroyed || ticket != generation) return@runOnUiThread
                if (latest != requests.get()) { finish(); return@runOnUiThread }
                result.onSuccess { target ->
                    startActivity(Intent(this, PlayerActivity::class.java).apply {
                        data = target.uri
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = incoming.clipData
                        putExtra(PlayerActivity.EXTRA_ROM_URI, target.uri.toString())
                        putExtra(PlayerActivity.EXTRA_ROM_FILE_NAME, target.fileName)
                    })
                }.onFailure { error ->
                    Toast.makeText(this, error.message ?: "Could not open the ROM.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }
    }

    override fun onDestroy() {
        generation++
        io.shutdownNow()
        super.onDestroy()
    }

    private companion object { val requests = AtomicLong() }
}
