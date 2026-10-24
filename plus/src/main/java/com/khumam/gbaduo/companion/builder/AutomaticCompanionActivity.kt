package com.khumam.gbaduo.companion.builder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.storage.GameStorage
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.zip.CRC32

/** Plus-only local builder. Neither the page nor JavaScript receives a real ROM URI. */
class AutomaticCompanionActivity : Activity() {
    private val token = BuildCancellation()
    private val io = Executors.newSingleThreadExecutor()
    private val bridgeLock = Any()
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var button: Button
    private var web: WebView? = null
    private var distribution: ResolvedBuilder? = null
    private var input: File? = null
    private var work: File? = null
    private var stream: FileOutputStream? = null
    private var expectedBytes = -1L
    private var receivedBytes = 0L
    private var phase = Phase.PREPARING
    @Volatile private var config = "{}"
    @Volatile private var successful = false

    private enum class Phase { PREPARING, RECEIVING, INSTALLING, FINISHED, FAILED }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32); setBackgroundColor(Color.rgb(22, 30, 44)) }
        layout.addView(TextView(this).apply { setTextColor(Color.WHITE); textSize = 24f; text = "Create companion" })
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 17f; setPadding(0, 24, 0, 24) }
        layout.addView(status)
        progress = ProgressBar(this).apply { isIndeterminate = true }; layout.addView(progress)
        button = Button(this).apply { text = "Cancel"; setOnClickListener { finish() } }; layout.addView(button)
        setContentView(layout)
        try {
            val path = intent.getStringExtra(EXTRA_ROM) ?: error("Missing ROM")
            val rom = File(path).canonicalFile
            require(rom.path.startsWith(File(cacheDir, "companion-builder-input").canonicalPath + File.separator) && rom.isFile) { "Invalid local ROM" }
            require(rom.length() in 0xB0L..MAX_ROM_BYTES) { "Invalid ROM size" }; input = rom
            val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().take(160)
            val gameCode = intent.getStringExtra(EXTRA_CODE).orEmpty(); require(Regex("^[A-Z0-9]{4}$").matches(gameCode))
            val crc = intent.getLongExtra(EXTRA_CRC, -1); require(crc in 0..0xffffffffL)
            val catalog = URI(intent.getStringExtra(EXTRA_CATALOG) ?: DEFAULT_CATALOG_URL); BuilderCatalog.origin(catalog)
            val storage = GameStorage.forGame(filesDir, Platform.GBA, title, gameCode, crc)
            val originalAssignment = storage.packId
            work = File(cacheDir, "companion-builder-jobs/${UUID.randomUUID()}").apply { mkdirs() }
            status.text = "Checking ROM and finding a compatible companion…"
            io.execute {
                try {
                    validateRom(rom, gameCode, crc)
                    val resolved = BuilderDistribution(File(cacheDir, "companion-builder-distribution")).resolve(catalog, token)
                    distribution = resolved
                    val profile = resolved.catalog.profile(gameCode, crc)
                        ?: error("No companion profile is available for this ROM. You can still import a ZIP manually.")
                    config = JSONObject().put("profileId", profile.id).put("gameCode", gameCode)
                        .put("crc32", "%08X".format(crc)).put("romUrl", "/__plus/rom.gba")
                        .put("language", "en").toString()
                    token.check()
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) startPage(layout, resolved, rom, storage, gameCode, crc, originalAssignment)
                    }
                } catch (error: Exception) { showError(error) }
            }
        } catch (error: Exception) { showError(error) }
    }

    private fun validateRom(file: File, code: String, expectedCrc: Long) {
        val crc = CRC32(); file.inputStream().use { stream ->
            val header = ByteArray(0xB0); require(stream.read(header) == header.size)
            require(String(header, 0xAC, 4, Charsets.US_ASCII) == code) { "ROM game code changed" }; crc.update(header)
            val buffer = ByteArray(32768)
            while (true) { token.check(); val n = stream.read(buffer); if (n < 0) break; crc.update(buffer, 0, n) }
        }
        require(crc.value == expectedCrc) { "ROM checksum changed" }
    }

    private fun startPage(layout: LinearLayout, resolved: ResolvedBuilder, rom: File, storage: GameStorage, code: String, crc: Long, previous: String?) {
        token.check()
        status.text = "Creating companion from your local ROM…"
        val browser = WebView(this); web = browser
        browser.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false; allowContentAccess = false; domStorageEnabled = false; databaseEnabled = false
            javaScriptCanOpenWindowsAutomatically = false; setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            blockNetworkLoads = true; cacheMode = WebSettings.LOAD_NO_CACHE
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
        browser.setDownloadListener { _, _, _, _, _ -> showError(IllegalArgumentException("Downloads are disabled inside the builder")) }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = intercept(request, resolved.directory, rom)
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel(); showError(IllegalArgumentException("Builder connection was blocked")) }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (request.isForMainFrame) showError(IllegalArgumentException("Could not start the local builder")) }
        }
        browser.addJavascriptInterface(Bridge(storage, code, crc, previous), "PlusBuilder")
        // Attached so the renderer stays active; all visible UI is native.
        browser.alpha = 0f; browser.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        layout.addView(browser, LinearLayout.LayoutParams(1, 1))
        browser.loadUrl(ORIGIN + "/" + resolved.catalog.bundle.entrypoint)
    }

    private fun intercept(request: WebResourceRequest, directory: File, rom: File): WebResourceResponse {
        val uri = request.url
        if (token.cancelled || request.method != "GET" || uri.scheme != "https" || uri.encodedAuthority != HOST || uri.query != null || uri.fragment != null) return blocked()
        return try {
            val file = if (uri.encodedPath == "/__plus/rom.gba") rom else {
                require(!uri.encodedPath.orEmpty().contains('%'))
                BuilderFiles.safeFile(directory, uri.path.orEmpty().removePrefix("/"))
            }
            require(file.isFile)
            val mime = when (file.extension.lowercase()) { "html" -> "text/html"; "js" -> "application/javascript"; "json" -> "application/json"; "css" -> "text/css"; "png" -> "image/png"; "svg" -> "image/svg+xml"; "otf" -> "font/otf"; "ttf" -> "font/ttf"; else -> "application/octet-stream" }
            val headers = mutableMapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff", "Content-Length" to file.length().toString())
            if (mime == "text/html") headers["Content-Security-Policy"] = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'"
            WebResourceResponse(mime, null, 200, "OK", headers, file.inputStream())
        } catch (_: Exception) { blocked() }
    }
    private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))

    private inner class Bridge(private val storage: GameStorage, private val code: String, private val crc: Long, private val previous: String?) {
        @JavascriptInterface fun configuration(): String = config
        @JavascriptInterface fun cancelled(): Boolean = token.cancelled
        @JavascriptInterface fun progress(message: String) {
            if (message.length > 512 || token.cancelled) return
            runOnUiThread { if (!isFinishing && !isDestroyed && !successful) status.text = message }
        }
        @JavascriptInterface fun beginZip(size: Long) = guarded {
            synchronized(bridgeLock) {
                token.check(); require(phase == Phase.PREPARING && size in 1..GeneratedPackTransaction.MAX_ZIP_BYTES) { "Invalid builder output" }
                expectedBytes = size; receivedBytes = 0; stream = File(work!!, "generated.zip").outputStream(); phase = Phase.RECEIVING
            }
        }
        @JavascriptInterface fun appendZip(encoded: String) = guarded {
            synchronized(bridgeLock) {
                token.check(); require(phase == Phase.RECEIVING && encoded.length in 4..65536 && encoded.length % 4 == 0 && Regex("^[A-Za-z0-9+/]*={0,2}$").matches(encoded)) { "Invalid builder output chunk" }
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                require(bytes.size <= 49152 && receivedBytes + bytes.size <= expectedBytes) { "Builder output exceeds declared size" }
                stream!!.write(bytes); receivedBytes += bytes.size
            }
        }
        @JavascriptInterface fun finishZip() = guarded {
            synchronized(bridgeLock) {
                token.check(); require(phase == Phase.RECEIVING && receivedBytes == expectedBytes) { "Builder output is incomplete" }
                stream!!.close(); stream = null; phase = Phase.INSTALLING
            }
            io.execute {
                try {
                    val manifest = GeneratedPackTransaction.install(File(work!!, "generated.zip"), File(filesDir, "packs"), storage, code, crc, previous, token) { commit ->
                        val result = CompletableFuture<Unit>()
                        runOnUiThread {
                            try {
                                if (isFinishing || isDestroyed) token.cancel()
                                commit(); result.complete(Unit)
                            } catch (error: Exception) { result.completeExceptionally(error) }
                        }
                        try { result.join() } catch (error: CompletionException) { throw (error.cause as? Exception ?: error) }
                    }
                    synchronized(bridgeLock) { successful = true; phase = Phase.FINISHED }
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            setResult(RESULT_OK); this@AutomaticCompanionActivity.progress.visibility = View.GONE
                            status.text = "Companion created and assigned: " + manifest.name
                            button.text = "Done"
                        }
                    }
                } catch (error: Exception) { showError(error) }
            }
        }
        @JavascriptInterface fun fail(message: String) { showError(IllegalArgumentException(message.take(512))) }
        private fun guarded(action: () -> Unit) { try { action() } catch (error: Exception) { showError(error) } }
    }

    private fun showError(error: Exception) {
        if (successful || !token.cancel()) return
        synchronized(bridgeLock) { phase = Phase.FAILED; runCatching { stream?.close() }; stream = null }
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                progress.visibility = View.GONE
                status.text = "Could not create companion: " + (error.message ?: error.javaClass.simpleName)
                button.text = "Close"
            }
        }
    }

    override fun onDestroy() {
        token.cancel()
        synchronized(bridgeLock) { runCatching { stream?.close() }; stream = null }
        web?.apply { stopLoading(); removeJavascriptInterface("PlusBuilder"); destroy() }; web = null
        io.execute { distribution?.close(); work?.deleteRecursively(); input?.delete() }
        io.shutdown(); super.onDestroy()
    }

    companion object {
        const val DEFAULT_CATALOG_URL = "https://levi-soft.github.io/gba-duo-companion-builder/catalog.json"
        private const val HOST = "builder.gbaduo.invalid"
        private const val ORIGIN = "https://$HOST"
        private const val MAX_ROM_BYTES = 32 * 1024 * 1024L
        private const val EXTRA_ROM = "builder.rom"
        private const val EXTRA_TITLE = "builder.title"
        private const val EXTRA_CODE = "builder.code"
        private const val EXTRA_CRC = "builder.crc"
        private const val EXTRA_CATALOG = "builder.catalog"
        fun launch(context: Context, romFile: File, gameTitle: String, gameCode: String, crc32: Long, catalogUrl: String = DEFAULT_CATALOG_URL) {
            context.startActivity(Intent(context, AutomaticCompanionActivity::class.java).putExtra(EXTRA_ROM, romFile.absolutePath)
                .putExtra(EXTRA_TITLE, gameTitle).putExtra(EXTRA_CODE, gameCode).putExtra(EXTRA_CRC, crc32).putExtra(EXTRA_CATALOG, catalogUrl))
        }
    }
}
