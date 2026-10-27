package com.khumam.gbaduo.artwork

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.library.ui.Fonts
import com.khumam.gbaduo.library.ui.Pixel
import com.khumam.gbaduo.library.ui.PixelBoxDrawable
import com.khumam.gbaduo.settings.PixelPage
import com.khumam.gbaduo.settings.ScrapingActivity
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/** Plus image selection. Searching never assigns an image; only Apply does. */
class ArtworkActivity : PixelPage() {
    private val gate = ArtworkRequestGate()
    private val io = Executors.newSingleThreadExecutor()
    private val thumbnails = Executors.newFixedThreadPool(2)
    private lateinit var repository: ArtworkRepository
    private lateinit var rom: ArtworkRomInfo
    private lateinit var platform: Platform
    private lateinit var status: TextView
    private var searchText = ""
    internal var providerFactory: () -> ArtworkProvider = { LibretroArtworkProvider() }
    internal var imageDownloader: (ArtworkCandidate) -> ByteArray = { ArtworkHttp.download(it) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        page("Artwork")
        repository = ArtworkRepository(this)
        val crc = intent.getLongExtra("artwork.crc", -1)
        // Per-game settings already know platform/CRC but do not read the ROM again.
        // Size is optional metadata for this picker; 0 means unknown, never a ZIP size.
        val bytes = intent.getLongExtra("artwork.size", 0)
        platform = Platform.byId(intent.getStringExtra("artwork.platform")) ?: run { finish(); return }
        if (crc !in 0..0xffffffffL || bytes !in 0..32L * 1024 * 1024) { finish(); return }
        rom = ArtworkRomInfo(ArtworkIdentity(platform.id, crc), intent.getStringExtra("artwork.title").orEmpty(),
            intent.getStringExtra("artwork.file").orEmpty(), intent.getStringExtra("artwork.code").orEmpty(), bytes)
        searchText = state?.getString("search") ?: rom.title
        showMenu()
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putString("search", searchText)
        super.onSaveInstanceState(state)
    }

    private fun clear(message: String, scrollY: Int = 0): Long {
        val token = gate.begin()
        content.removeAllViews()
        note(rom.title, 0).apply { textSize = 19f; setTextColor(Pixel.YELLOW) }
        status = note(message, 8)
        content.post { if (gate.isCurrent(token)) (content.parent as? ScrollView)?.scrollTo(0, scrollY) }
        return token
    }

    private fun showMenu() {
        val token = clear("Choose a source or import your own image. Your current artwork stays selected until you apply a replacement.")
        val current = squareImage(200)
        content.addView(current, LinearLayout.LayoutParams(dp(200), dp(200)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(14) })
        io.execute {
            val bitmap = runCatching { repository.loadBitmap(rom.identity) }.getOrNull()
            runOnUiThread { if (gate.isCurrent(token)) { current.setImageBitmap(bitmap); current.contentDescription = if (bitmap == null) "No custom artwork" else "Current artwork" } }
        }
        val query = field("Game name", InputType.TYPE_CLASS_TEXT).apply { setText(searchText) }
        val search = button("Find artwork", Pixel.YELLOW) {
            searchText = query.text.toString().trim()
            if (searchText.isBlank()) { status.text = "Enter a game name."; return@button }
            findGames()
        }
        content.addView(search, spaced(14))
        content.addView(button("Choose image…", Pixel.LAVENDER) { chooseImage() }, spaced(12))
        content.addView(button("Artwork sources", Pixel.LAVENDER) { startActivity(Intent(this, ScrapingActivity::class.java)) }, spaced(12))
        focusFirst(query)
    }

    private fun findGames() {
        val query = searchText
        work("Searching Libretro...", {
            val source = providerFactory()
            source to source.search(query, platform)
        }) { result ->
            val (source, games) = result
            clear(if (games.isEmpty()) "No games found. Try a shorter title, or choose an image manually." else "Choose the correct game. Search results are not automatically assigned to this ROM.")
            for (game in games.take(50)) content.addView(button(game.title + (game.year?.let { " · $it" } ?: ""), Pixel.LAVENDER) { findImages(source, game) }, spaced(12))
            content.addView(button("Change search", Pixel.YELLOW) { showMenu() }, spaced(16))
        }
    }

    private fun findImages(source: ArtworkProvider, game: ArtworkGame) {
        work("Loading available artwork…", { source.images(game, platform) }) { images ->
            if (source is LibretroArtworkProvider && images.size == 1) {
                previewRemote(images.single(), ::findGames)
            } else {
                showImages(source, game, images, 24)
            }
        }
    }

    private fun showImages(source: ArtworkProvider, game: ArtworkGame, images: List<ArtworkCandidate>, count: Int, scrollY: Int = 0) {
        val token = clear(if (images.isEmpty()) "No artwork found for this game." else "${game.title}\nChoose an image to preview. Images keep their original proportions.", scrollY)
        val ordered = images.sortedBy { if (it.width > 0 && it.width == it.height) 0 else 1 }
        val shown = ordered.take(count)
        val columns = if (resources.configuration.screenWidthDp >= 720) 3 else 2
        // PixelPage has 28dp + 34dp horizontal gutters; include every card's
        // 8dp spacing so narrow secondary displays do not overflow by a few pixels.
        val cardSize = ((resources.configuration.screenWidthDp - 62 - columns * 8) / columns).coerceIn(80, 240)
        shown.chunked(columns).forEach { rowImages ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL }
            rowImages.forEach { candidate ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    isFocusable = true; isClickable = true
                    background = PixelBoxDrawable(this@ArtworkActivity, Pixel.LAVENDER, focusedFill = Pixel.YELLOW, focusedBorder = Pixel.PINK)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    contentDescription = "Preview artwork by ${candidate.author ?: source.id}"
                    setOnClickListener {
                        val position = (content.parent as? ScrollView)?.scrollY ?: 0
                        previewRemote(candidate) { showImages(source, game, images, count, position) }
                    }
                }
                val image = squareImage(cardSize - 16)
                card.addView(image, LinearLayout.LayoutParams(dp(cardSize - 16), dp(cardSize - 16)))
                card.addView(TextView(this).apply {
                    text = candidate.author ?: "${candidate.width} × ${candidate.height}"
                    typeface = Fonts.body(this@ArtworkActivity, 500); textSize = 12f; setTextColor(Pixel.INK)
                    maxLines = 2; gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                row.addView(card, LinearLayout.LayoutParams(dp(cardSize), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
                thumbnails.execute {
                    if (!gate.isCurrent(token)) return@execute
                    val bitmap = runCatching {
                        val thumbnail = candidate.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { candidate.copy(url = it, thumbnailUrl = null) } ?: candidate
                        val decoded = ArtworkImageStore.decode(imageDownloader(thumbnail))
                        val largest = max(decoded.width, decoded.height)
                        if (largest > 256) {
                            val ratio = 256f / largest
                            Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).roundToInt().coerceAtLeast(1),
                                (decoded.height * ratio).roundToInt().coerceAtLeast(1), true).also { if (it !== decoded) decoded.recycle() }
                        } else decoded
                    }.getOrNull()
                    runOnUiThread {
                        if (gate.isCurrent(token) && bitmap != null) image.setImageBitmap(bitmap)
                        else bitmap?.recycle()
                    }
                }
            }
            content.addView(row, spaced(12))
        }
        if (shown.size < images.size) content.addView(button("Show more images", Pixel.YELLOW) { showImages(source, game, images, count + 24, (content.parent as? ScrollView)?.scrollY ?: 0) }, spaced(16))
        content.addView(button("Change search", Pixel.LAVENDER) { showMenu() }, spaced(16))
    }

    private fun previewRemote(candidate: ArtworkCandidate, back: () -> Unit) {
        work("Downloading preview…", { imageDownloader(candidate).let { it to ArtworkImageStore.decode(it) } }) { (bytes, bitmap) ->
            showPreview(bytes, bitmap, candidate, false, back)
        }
    }

    private fun chooseImage() {
        gate.begin() // A pending provider request must not replace the screen behind the picker.
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, PICK_IMAGE)
    }

    @Deprecated("Activity result API retained for the host Activity baseline")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        work("Preparing selected image…", {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val size = input.read(buffer); if (size < 0) break
                    require(out.size().toLong() + size <= ArtworkImageStore.MAX_INPUT_BYTES) { "Image exceeds 16 MiB" }
                    out.write(buffer, 0, size)
                }
                out.toByteArray()
            } ?: error("Could not read selected image")
            bytes to ArtworkImageStore.decode(bytes)
        }) { (bytes, bitmap) -> showPreview(bytes, bitmap, null, true, ::showMenu) }
    }

    private fun showPreview(bytes: ByteArray, bitmap: Bitmap, candidate: ArtworkCandidate?, manual: Boolean, back: () -> Unit) {
        clear("Preview · ${bitmap.width} × ${bitmap.height}\n" + if (manual) "Selected image from your device" else "Source: ${candidate?.provider}\nAuthor: ${candidate?.author ?: "Not listed"}")
        val side = minOf(360, resources.configuration.screenWidthDp - 70).coerceAtLeast(100)
        content.addView(squareImage(side).apply { setImageBitmap(bitmap); contentDescription = "Artwork preview, original aspect ratio" },
            LinearLayout.LayoutParams(dp(side), dp(side)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(14) })
        note("The library displays this image inside the official cartridge label.")
        val apply = button("Apply artwork", Pixel.YELLOW) {
            workOwned("Saving artwork…", { request -> repository.applyIfOwned(rom.identity, bytes, candidate, manual) { gate.claim(request) } }) {
                setResult(RESULT_OK); finish()
            }
        }
        content.addView(apply, spaced(16))
        publicSource(candidate)?.let { address -> content.addView(button("View source", Pixel.LAVENDER) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address))) }
        }, spaced(12)) }
        content.addView(button("Choose another image", Pixel.LAVENDER) { back() }, spaced(12))
        focusFirst(apply)
    }

    private fun publicSource(candidate: ArtworkCandidate?): String? = candidate?.sourcePage?.takeIf { address ->
        runCatching {
            val uri = URI(address)
            uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) && uri.host in listOf("steamgriddb.com", "www.steamgriddb.com", "github.com", "thumbnails.libretro.com") &&
                uri.rawQuery == null
        }.getOrDefault(false)
    }

    private fun squareImage(size: Int) = ImageView(this).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = false
        setBackgroundColor(SHADE)
        minimumWidth = dp(size); minimumHeight = dp(size)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun <T> work(message: String, job: () -> T, success: (T) -> Unit) = workOwned(message, { _: Long -> job() }, success)
    private fun <T> workOwned(message: String, job: (Long) -> T, success: (T) -> Unit) {
        val token = gate.begin(); status.text = message
        io.execute {
            val result = runCatching { if (!gate.isCurrent(token)) error("Cancelled"); job(token) }
            runOnUiThread {
                if (gate.isCurrent(token) && !isFinishing && !isDestroyed) result.onSuccess(success).onFailure { status.text = it.message ?: "Artwork request failed. Please try again." }
            }
        }
    }

    override fun finish() {
        gate.close()
        super.finish()
    }

    override fun onDestroy() {
        gate.close(); io.shutdownNow(); thumbnails.shutdownNow()
        super.onDestroy()
    }

    private companion object { const val PICK_IMAGE = 711 }
}
