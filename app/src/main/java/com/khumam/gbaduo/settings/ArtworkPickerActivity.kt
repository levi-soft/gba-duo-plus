package com.khumam.gbaduo.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import com.khumam.gbaduo.R
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.library.artwork.Artwork
import com.khumam.gbaduo.library.artwork.ArtworkImages
import com.khumam.gbaduo.library.artwork.ArtworkPrefs
import com.khumam.gbaduo.library.artwork.Sgdb
import com.khumam.gbaduo.library.artwork.SgdbGame
import com.khumam.gbaduo.library.artwork.SgdbGrid
import com.khumam.gbaduo.library.artwork.SteamGridDb
import com.khumam.gbaduo.library.artwork.SteamGridDb.CoverShape
import com.khumam.gbaduo.library.artwork.UrlHttp
import com.khumam.gbaduo.library.ui.Pixel
import com.khumam.gbaduo.library.ui.PixelBoxDrawable
import com.khumam.gbaduo.storage.GameSettings
import com.khumam.gbaduo.storage.GameSettingsKeys
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Game settings > Find on SteamGridDB: search by name, pick the game, then pick the cover
 * from its thumbnails. The pick is kept as "picked by you", so scraping never replaces it.
 */
class ArtworkPickerActivity : PixelPage() {

    private lateinit var gameDir: String
    private lateinit var platform: Platform
    private var crc = 0L
    private lateinit var api: SteamGridDb
    private lateinit var query: EditText
    private lateinit var results: LinearLayout
    private lateinit var shapePicker: Spinner
    private var shape = CoverShape.SQUARE
    private var selectedGame: SgdbGame? = null
    @Volatile private var requestId = 0
    private val io = Executors.newFixedThreadPool(3)
    internal var apiFactory: () -> SteamGridDb? = {
        ArtworkPrefs.sgdbKey(this)?.let { SteamGridDb(it, UrlHttp()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dir = intent.getStringExtra(GameSettingsActivity.EXTRA_GAME_DIR)
        val source = apiFactory()
        val platformId = Platform.byId(intent.getStringExtra(GameSettingsActivity.EXTRA_PLATFORM_ID))
        if (dir == null || platformId == null) {
            finish()
            return
        }
        if (source == null) {
            startActivity(Intent(this, SteamGridDbActivity::class.java))
            finish()
            return
        }
        gameDir = dir
        platform = platformId
        crc = intent.getLongExtra(GameSettingsActivity.EXTRA_CRC, 0)
        api = source
        shape = savedInstanceState?.getString("coverShape")?.let { name ->
            CoverShape.entries.firstOrNull { it.name == name }
        } ?: CoverShape.SQUARE

        page(getString(R.string.artwork_find), listOf("▲ ▼ ◀ ▶" to "Choose", "A" to "Pick", "B" to "Back"))
        content.addView(label(getString(R.string.artwork_cover_shape)), spaced(0))
        shapePicker = Spinner(this).apply {
            contentDescription = getString(R.string.artwork_cover_shape)
            adapter = ArrayAdapter(this@ArtworkPickerActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.artwork_shape_square), getString(R.string.artwork_shape_portrait), getString(R.string.artwork_shape_all)))
            setSelection(shape.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val next = CoverShape.entries[position]
                    if (next == shape) return
                    shape = next
                    selectedGame?.let(::loadGrids)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        content.addView(shapePicker, spaced(8))
        query = field(getString(R.string.artwork_search), InputType.TYPE_CLASS_TEXT)
        query.setText(intent.getStringExtra(GameSettingsActivity.EXTRA_TITLE).orEmpty())
        val go = button(getString(R.string.artwork_search_go), Pixel.YELLOW) { search() }
        content.addView(go, spaced(12, ViewGroup.LayoutParams.WRAP_CONTENT))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results, spaced(8))
        focusFirst(go)
        search()
    }

    private fun message(text: String, error: Boolean = true) {
        results.removeAllViews()
        results.addView(note(text, top = 8).also { content.removeView(it) }.apply {
            setTextColor(if (error) Pixel.CORAL else Pixel.ON_INK_SOFT)
        })
    }

    private fun busy(text: String) {
        results.removeAllViews()
        results.addView(busyRow(text), spaced(12))
    }

    private fun search() {
        if (!shapePicker.isEnabled) return
        val name = query.text.toString().trim()
        if (name.isEmpty()) return
        selectedGame = null
        val request = ++requestId
        busy(getString(R.string.artwork_searching))
        io.execute {
            if (request != requestId) return@execute
            val result = api.search(name)
            runOnUiThread {
                if (isDestroyed || request != requestId) return@runOnUiThread
                when (result) {
                    is Sgdb.Failed -> message(failText(result.reason))
                    is Sgdb.Ok -> if (result.value.isEmpty()) message(getString(R.string.artwork_no_results)) else showGames(result.value.take(8))
                }
            }
        }
    }

    private fun failText(reason: Sgdb.Reason) = getString(
        when (reason) {
            Sgdb.Reason.KEY_REJECTED -> R.string.scraping_key_rejected
            Sgdb.Reason.SLOW_DOWN -> R.string.scraping_slow_down
            Sgdb.Reason.NOT_FOUND -> R.string.artwork_no_results
            Sgdb.Reason.OFFLINE -> R.string.scraping_offline
        },
    )

    private fun showGames(games: List<SgdbGame>) {
        results.removeAllViews()
        var first: View? = null
        for (game in games) {
            val summary = game.year?.toString().orEmpty()
            val (card, _) = menuCard(R.drawable.ic_settings, Pixel.PINK, game.name, summary) { loadGrids(game) }
            content.removeView(card)
            results.addView(card, spaced(12))
            if (first == null) first = card
        }
        first?.requestFocus()
    }

    private fun loadGrids(game: SgdbGame) {
        selectedGame = game
        val selectedShape = shape
        val request = ++requestId
        busy(getString(R.string.artwork_searching))
        io.execute {
            if (request != requestId) return@execute
            val result = api.grids(game.id, selectedShape)
            runOnUiThread {
                if (isDestroyed || request != requestId) return@runOnUiThread
                when (result) {
                    is Sgdb.Failed -> message(failText(result.reason))
                    is Sgdb.Ok -> if (result.value.isEmpty()) message(getString(R.string.artwork_shape_empty), error = false) else showGrids(result.value.take(24))
                }
            }
        }
    }

    private fun showGrids(grids: List<SgdbGrid>) {
        val request = requestId
        results.removeAllViews()
        results.addView(label(getString(R.string.artwork_pick_hint)), spaced(4))
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), dp(12), dp(14)) }
        var first: View? = null
        for (grid in grids) {
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Select ${grid.width} x ${grid.height} cover"
                background = PixelBoxDrawable(this@ArtworkPickerActivity, Pixel.LAVENDER, shadowDp = 4f, shadowColor = SHADE, focusedFill = Pixel.YELLOW)
                setPadding(dp(5), dp(5), dp(9), dp(9))
                isFocusable = true
                isClickable = true
                setOnClickListener { choose(grid) }
            }
            val height = if (grid.width > 0 && grid.height > 0) (120f * grid.height / grid.width).roundToInt().coerceIn(68, 220) else 120
            strip.addView(thumb, LinearLayout.LayoutParams(dp(120), dp(height)).apply { marginEnd = dp(12) })
            if (first == null) first = thumb
            io.execute {
                if (request != requestId) return@execute
                val bytes = (api.download(grid.thumb) as? Sgdb.Ok)?.value ?: return@execute
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@execute
                runOnUiThread { if (!isDestroyed && request == requestId) thumb.setImageBitmap(bitmap) else bitmap.recycle() }
            }
        }
        results.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) })
        first?.requestFocus()
    }

    private fun choose(grid: SgdbGrid) {
        shapePicker.isEnabled = false
        query.isEnabled = false
        val request = ++requestId
        busy(getString(R.string.artwork_downloading))
        io.execute {
            val stored = (api.download(grid.url) as? Sgdb.Ok)?.value?.let(ArtworkImages::encode)?.also {
                Artwork.store(this).put(platform.id, crc, it, "steamgriddb", grid.id.toString())
                GameSettings(File(gameDir, "settings.json")).put(GameSettingsKeys.ARTWORK, GameSettingsKeys.ARTWORK_PICKED)
            }
            runOnUiThread {
                if (isDestroyed || request != requestId) return@runOnUiThread
                if (stored != null) {
                    Toast.makeText(this, R.string.artwork_saved, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    shapePicker.isEnabled = true
                    query.isEnabled = true
                    message(getString(R.string.artwork_save_failed))
                }
            }
        }
    }

    override fun onDestroy() {
        requestId++
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("coverShape", shape.name)
        super.onSaveInstanceState(outState)
    }
}
