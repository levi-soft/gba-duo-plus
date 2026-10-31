package com.khumam.gbaduo.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.khumam.gbaduo.BuildConfig
import com.khumam.gbaduo.R
import com.khumam.gbaduo.companion.CatalogPack
import com.khumam.gbaduo.companion.CatalogStatus
import com.khumam.gbaduo.companion.Fetch
import com.khumam.gbaduo.companion.PackCatalog
import com.khumam.gbaduo.companion.PackDownloader
import com.khumam.gbaduo.companion.PackManifest
import com.khumam.gbaduo.companion.PackStore
import com.khumam.gbaduo.companion.builder.BuildCancellation
import com.khumam.gbaduo.companion.builder.BuilderProfile
import com.khumam.gbaduo.companion.builder.WebCompanionCreation
import com.khumam.gbaduo.library.artwork.Http
import com.khumam.gbaduo.library.artwork.HttpResponse
import com.khumam.gbaduo.library.artwork.UrlHttp
import com.khumam.gbaduo.library.ui.Pixel
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

/** Official downloads and exact-ROM local creation share the Library's web entry. */
class PackCatalogActivity : PixelPage() {
    private lateinit var gameCode: String
    private lateinit var gameTitle: String
    private lateinit var downloader: PackDownloader
    private lateinit var packStore: PackStore
    private lateinit var downloads: LinearLayout
    private lateinit var creation: LinearLayout
    private lateinit var operationStatus: LinearLayout
    private val io = Executors.newFixedThreadPool(2)
    private val metadataCancellation = BuildCancellation()
    private var source: WebCompanionCreation.Source? = null
    private var requestId: String? = null
    @Volatile private var closed = false
    private var officialGeneration = 0
    private var creationGeneration = 0
    private var operationGeneration = 0
    private var operation: Operation? = null
    private var operationCancellation: BuildCancellation? = null
    private var creationRequestCode = 0
    private val actions = mutableListOf<View>()

    // Per-instance factories let host tests exercise the real catalog and installer without a network.
    internal var httpFactory: () -> Http = { UrlHttp() }
    internal var creationFinder: (Context, WebCompanionCreation.Source, BuildCancellation) -> BuilderProfile? =
        WebCompanionCreation::find
    internal var creationLauncher: (Activity, WebCompanionCreation.Source, Int, BuildCancellation, (String?) -> Unit) -> Unit =
        { activity, target, request, token, aborted -> WebCompanionCreation.launch(activity, target, request, token, aborted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameCode = intent.getStringExtra(EXTRA_GAME_CODE) ?: run { finish(); return }
        gameTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        // Read the identity once. Display labels and later Intent mutations never change the target.
        source = WebCompanionCreation.source(intent)?.takeIf { it.entry.gameCode == gameCode }
        val packsDir = File(filesDir, "packs")
        packStore = PackStore(packsDir)
        val http = httpFactory()
        downloader = PackDownloader(object : Http {
            override fun get(url: String, headers: Map<String, String>): HttpResponse {
                checkOpen()
                val response = http.get(url, headers)
                // A network implementation may ignore interruption. Late bytes must not start installation.
                checkOpen()
                return response
            }
        }, packsDir, cacheDir)

        page(getString(R.string.pack_web_title), listOf("▲ ▼" to "Choose", "A" to "Select", "B" to "Back"))
        operationStatus = section()
        downloads = section()
        creation = section()
        loadOfficial()
        if (source != null) loadCreation()
    }

    private fun section() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        content.addView(this, spaced(8))
    }

    private fun active() = !closed && !isFinishing && !isDestroyed

    private fun checkOpen() {
        if (closed || Thread.currentThread().isInterrupted) throw IOException("Catalog closed")
    }

    private fun clear(section: LinearLayout) {
        actions.removeAll { it.parent === section }
        section.removeAllViews()
    }

    private fun addNote(section: LinearLayout, text: String, error: Boolean = false) {
        section.addView(note(text, top = 8).also { content.removeView(it) }.apply {
            setTextColor(if (error) Pixel.CORAL else Pixel.ON_INK_SOFT)
        }, spaced(8))
    }

    private fun action(section: LinearLayout, view: View, wrap: Boolean = false) {
        content.removeView(view)
        section.addView(view, spaced(12, if (wrap) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT))
        actions += view
        setActionEnabled(view, operation == null)
        if (operation == null && actions.size == 1) focusFirst(view)
    }

    private fun setActionEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.isFocusable = enabled
        view.alpha = if (enabled) 1f else 0.5f
    }

    private fun updateActions() = actions.forEach { setActionEnabled(it, operation == null) }

    private fun failText(failed: Fetch.Failed): String = when (failed.reason) {
        Fetch.Reason.OFFLINE -> getString(R.string.pack_web_offline)
        Fetch.Reason.UNSUPPORTED_CATALOG -> getString(R.string.pack_web_unsupported)
        Fetch.Reason.DOWNLOAD_FAILED -> getString(R.string.pack_web_download_failed)
        Fetch.Reason.TOO_BIG -> getString(R.string.pack_web_too_big)
        Fetch.Reason.BAD_CHECKSUM -> getString(R.string.pack_web_bad_checksum)
        Fetch.Reason.INSTALL_FAILED -> getString(R.string.pack_import_failed, failed.detail.orEmpty())
    }

    private fun loadOfficial() {
        if (!active() || operation != null) return
        val generation = ++officialGeneration
        clear(downloads)
        downloads.addView(label(getString(R.string.plus_web_downloads)), spaced(8))
        downloads.addView(busyRow(getString(R.string.pack_web_loading)), spaced(12))
        io.execute {
            val result = downloader.catalog()
            runOnUiThread {
                if (!active() || generation != officialGeneration) return@runOnUiThread
                clear(downloads)
                downloads.addView(label(getString(R.string.plus_web_downloads)), spaced(8))
                when (result) {
                    is Fetch.Failed -> {
                        addNote(downloads, failText(result), error = true)
                        action(downloads, button(getString(R.string.pack_web_retry), Pixel.YELLOW) { loadOfficial() }, wrap = true)
                    }
                    is Fetch.Ok -> showOfficial(result.value)
                }
            }
        }
    }

    private fun loadCreation() {
        val target = source ?: return
        if (!active() || operation != null) return
        val generation = ++creationGeneration
        clear(creation)
        creation.addView(label(getString(R.string.plus_web_creation)), spaced(8))
        creation.addView(busyRow(getString(R.string.plus_web_creation_checking)), spaced(12))
        io.execute {
            val answer = runCatching {
                metadataCancellation.check()
                creationFinder(applicationContext, target, metadataCancellation)?.takeIf {
                    it.gameCode == target.entry.gameCode && it.crc32 == target.crc32
                }.also { metadataCancellation.check() }
            }
            runOnUiThread {
                if (!active() || generation != creationGeneration) return@runOnUiThread
                clear(creation)
                creation.addView(label(getString(R.string.plus_web_creation)), spaced(8))
                answer.fold(onSuccess = { profile ->
                    if (profile == null) {
                        addNote(creation, getString(R.string.plus_web_creation_none))
                    } else {
                        val (card, _) = menuCard(R.drawable.ic_settings, Pixel.YELLOW,
                            getString(R.string.plus_builder_create), "${profile.name} · v${profile.version}") { create(target) }
                        action(creation, card)
                    }
                }, onFailure = { error ->
                    if (error !is CancellationException) {
                        addNote(creation, getString(R.string.plus_web_creation_failed), error = true)
                        action(creation, button(getString(R.string.pack_web_retry), Pixel.YELLOW) { loadCreation() }, wrap = true)
                    }
                })
            }
        }
    }

    private fun List<CatalogPack>.sorted() =
        sortedWith(compareByDescending<CatalogPack> { it.official }.thenBy { it.title })

    private fun showOfficial(all: List<CatalogPack>) {
        val exact = PackCatalog.forGame(all, gameCode).sorted()
        if (exact.isNotEmpty()) {
            addCards(exact)
            return
        }
        addNote(downloads, getString(R.string.plus_web_no_downloads, gameTitle.ifBlank { gameCode }))
        val related = PackCatalog.relatedTo(all, gameCode).sorted()
        if (related.isNotEmpty()) {
            addNote(downloads, getString(R.string.pack_web_related))
            addCards(related)
        }
        if (all.isNotEmpty()) {
            action(downloads, button(getString(R.string.pack_web_browse_all), Pixel.YELLOW) {
                if (!active() || operation != null) return@button
                clear(downloads)
                downloads.addView(label(getString(R.string.plus_web_downloads)), spaced(8))
                addNote(downloads, getString(R.string.pack_web_all_title))
                addCards(all.sorted())
            }, wrap = true)
        }
    }

    private fun addCards(packs: List<CatalogPack>) {
        for (pack in packs) {
            val status = PackCatalog.status(pack, packStore.byId(pack.id)?.version, BuildConfig.VERSION_NAME)
            val statusText = when (status) {
                CatalogStatus.NEW -> null
                CatalogStatus.INSTALLED -> getString(R.string.pack_web_installed)
                CatalogStatus.UPDATE -> getString(R.string.pack_web_update)
                CatalogStatus.NEEDS_NEWER_APP -> getString(R.string.pack_web_needs_app, pack.minAppVersion.orEmpty())
            }
            val meta = listOfNotNull("v${pack.version}", pack.author.ifBlank { null },
                if (pack.canModifyGame) getString(R.string.pack_modifies_game) else null, statusText).joinToString(" · ")
            val (card, _) = menuCard(R.drawable.ic_settings, Pixel.PINK, pack.title, "$meta\n${pack.description}") {
                if (!active() || operation != null) return@menuCard
                if (status == CatalogStatus.NEEDS_NEWER_APP) Toast.makeText(this, statusText, Toast.LENGTH_SHORT).show()
                else download(pack)
            }
            action(downloads, card)
        }
    }

    private fun begin(kind: Operation, text: String): Int? {
        if (!active() || operation != null) return null
        operation = kind
        operationCancellation = BuildCancellation()
        val ticket = ++operationGeneration
        clear(operationStatus)
        operationStatus.addView(busyRow(text), spaced(12))
        updateActions()
        return ticket
    }

    private fun current(kind: Operation, ticket: Int) = active() && operation == kind && ticket == operationGeneration

    private fun end(error: String? = null) {
        operationCancellation?.cancel()
        operationCancellation = null
        operation = null
        clear(operationStatus)
        if (error != null) addNote(operationStatus, error, error = true)
        updateActions()
    }

    private fun create(target: WebCompanionCreation.Source) {
        val ticket = begin(Operation.CREATE, getString(R.string.plus_web_creating)) ?: return
        val token = operationCancellation!!
        val request = ++creationRequestCode
        try {
            creationLauncher(this, target, request, token) { error ->
                if (current(Operation.CREATE, ticket)) end(error?.let { getString(R.string.plus_builder_failed, it) })
            }
        } catch (error: Exception) {
            if (current(Operation.CREATE, ticket)) end(getString(R.string.plus_builder_failed, error.message.orEmpty()))
        }
    }

    private fun download(pack: CatalogPack) {
        val ticket = begin(Operation.DOWNLOAD, getString(R.string.pack_web_downloading, pack.title)) ?: return
        io.execute {
            val result = downloader.install(pack)
            runOnUiThread {
                if (!current(Operation.DOWNLOAD, ticket)) return@runOnUiThread
                when (result) {
                    is Fetch.Ok -> complete(result.value)
                    is Fetch.Failed -> end(failText(result))
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (!active() || operation != Operation.CREATE || requestCode != creationRequestCode) return
        val target = source ?: run { end(); return }
        if (resultCode != RESULT_OK) { end(); return }
        val id = WebCompanionCreation.createdPackId(data, target)
        val manifest = id?.let(packStore::byId)
        if (manifest == null) end(getString(R.string.plus_builder_failed, "Invalid creation result"))
        else complete(manifest)
    }

    private fun complete(manifest: PackManifest) {
        if (!active()) return
        val target = source
        if (target != null && !manifest.matches(target.entry.gameCode, target.crc32)) {
            end(getString(R.string.plus_web_pack_mismatch))
            return
        }
        val result = Intent().putExtra(EXTRA_PACK_ID, manifest.id).putExtra(EXTRA_REQUEST_ID, requestId)
        if (target != null) WebCompanionCreation.attachSource(result, target.entry, target.crc32)
        Toast.makeText(this, manifest.name, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK, result)
        finish()
    }

    private fun close() {
        closed = true
        metadataCancellation.cancel()
        operationCancellation?.cancel()
        io.shutdownNow()
    }

    override fun finish() {
        close()
        super.finish()
    }

    override fun onDestroy() {
        close()
        super.onDestroy()
    }

    private enum class Operation { DOWNLOAD, CREATE }

    companion object {
        const val EXTRA_GAME_CODE = "game_code"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PACK_ID = "pack_id"
        const val EXTRA_REQUEST_ID = "plus.web.request"
    }
}
