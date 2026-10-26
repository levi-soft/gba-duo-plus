package com.khumam.gbaduo.player

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import com.khumam.gbaduo.R
import com.khumam.gbaduo.BuildConfig
import com.khumam.gbaduo.achievements.RetroAchievements
import com.khumam.gbaduo.achievements.AchievementPopups
import com.khumam.gbaduo.companion.CompanionActivity
import com.khumam.gbaduo.companion.CompanionController
import com.khumam.gbaduo.companion.CompanionPlacement
import com.khumam.gbaduo.companion.CompanionPresentation
import com.khumam.gbaduo.companion.CompanionSettings
import com.khumam.gbaduo.companion.PackFingerprint
import com.khumam.gbaduo.companion.PackManifest
import com.khumam.gbaduo.companion.PackStore
import com.khumam.gbaduo.emu.AudioOut
import com.khumam.gbaduo.emu.EmuThread
import com.khumam.gbaduo.emu.GameView
import com.khumam.gbaduo.emu.InputMapper
import com.khumam.gbaduo.emu.ShaderPreset
import com.khumam.gbaduo.library.GameTitle
import com.khumam.gbaduo.library.RomArchive
import com.khumam.gbaduo.library.RomHeader
import com.khumam.gbaduo.library.RomIndex
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.companion.PreparedCompanion
import com.khumam.gbaduo.player.singlescreen.SingleScreenPlayerHost
import com.khumam.gbaduo.player.singlescreen.SingleScreenQuickActions
import com.khumam.gbaduo.player.singlescreen.SingleScreenWindows
import com.khumam.gbaduo.player.singlescreen.StartupTasks
import com.khumam.gbaduo.storage.GameStorage
import com.khumam.gbaduo.storage.LegacyMigration
import com.khumam.gbaduo.storage.SaveMirror
import java.io.File

class PlayerActivity : Activity() {

    private lateinit var input: InputMapper
    private lateinit var emu: EmuThread
    private lateinit var audioOut: AudioOut
    private var quickMenu: QuickMenu? = null
    private var companionScreen: CompanionController? = null
    private var companionPresentation: CompanionPresentation? = null
    private var companionActivity: CompanionActivity.Session? = null
    private var resumed = false
    private var achievementPopups: AchievementPopups? = null
    private var saveMirror: SaveMirror? = null
    private var singleScreen: SingleScreenPlayerHost? = null
    private var startupTasks: StartupTasks? = null
    private var singleScreenActions: SingleScreenQuickActions? = null

    /**
     * Whether this is Android's top resumed activity: the one on the top focused display,
     * which is where controller key events go. Not the same as [hasWindowFocus], which is
     * per display: the game keeps window focus on its own screen even while key events
     * go to the other one.
     */
    private var topResumed = false

    private val prefs by lazy { getSharedPreferences("player", MODE_PRIVATE) }
    private var savedShader: ShaderPreset
        get() = ShaderPreset.byId(prefs.getString("shader_id", null))
        set(value) = prefs.edit().putString("shader_id", value.id).apply()
    private var savedSwapAb: Boolean
        get() = prefs.getBoolean("swap_ab", false)
        set(value) = prefs.edit().putBoolean("swap_ab", value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        if (BuildConfig.SINGLE_SCREEN) {
            window.setDecorFitsSystemWindows(false)
            val surface = GameView(this).apply { id = R.id.game_view; preserveEGLContextOnPause = true }
            singleScreen = SingleScreenPlayerHost(this, surface, onKeys = { keys ->
                if (::input.isInitialized) {
                    input.setTouchKeys(keys)
                    if (input.keys() and directionMask == 0) requestCompanionDraw()
                }
            }, onMenu = ::showQuickMenu,
                onQuickSave = { singleScreen?.releaseControls(); singleScreenActions?.save() },
                onQuickLoad = { singleScreen?.releaseControls(); singleScreenActions?.load() },
                onFastForward = { enabled -> if (::input.isInitialized) input.setTouchFastForward(enabled) }
            ).also { setContentView(it) }
            startupTasks = StartupTasks(post = { action -> companionHandler.post { action() } })
        } else setContentView(R.layout.activity_player)

        val romUriString = intent.getStringExtra(EXTRA_ROM_URI)
        val romFileName = intent.getStringExtra(EXTRA_ROM_FILE_NAME)
        if (romUriString == null || romFileName == null) {
            finish()
            return
        }

        val ready: (Result<GameSource>) -> Unit = { result ->
            result.onSuccess(::startGame).onFailure {
                Toast.makeText(this, getString(R.string.load_failed, romFileName), Toast.LENGTH_LONG).show()
                finish()
            }
        }
        if (BuildConfig.SINGLE_SCREEN) startupTasks?.submit({ readGameSource(romUriString, romFileName) }, ready)
        else ready(runCatching { readGameSource(romUriString, romFileName) })
    }

    private data class GameSource(val rom: RomArchive.Rom, val header: RomHeader, val crc32: Long, val storage: GameStorage)

    private fun readGameSource(uri: String, fileName: String): GameSource {
        val started = android.os.SystemClock.elapsedRealtime()
        val rom = contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            // ZipInputStream otherwise makes 512-byte reads across a document provider.
            val input = if (BuildConfig.SINGLE_SCREEN) stream.buffered(64 * 1024) else stream
            RomArchive.readRom(fileName, input)
        }
            ?: error("No readable ROM")
        if (BuildConfig.SINGLE_SCREEN) android.util.Log.i("SSStartup", "ROM read: ${android.os.SystemClock.elapsedRealtime() - started} ms")
        val header = RomHeader.parse(rom.fileName, rom.bytes.copyOf(minOf(rom.bytes.size, RomHeader.SIZE)))
        val crc32 = RomIndex.crc32Of(rom.bytes)
        val storage = GameStorage.forGame(filesDir, header.platform, header.title, header.gameCode, crc32)
        LegacyMigration.migrate(filesDir, fileName, crc32, storage)
        storage.ensureDirs()
        if (BuildConfig.SINGLE_SCREEN) android.util.Log.i("SSStartup", "ROM preparation: ${android.os.SystemClock.elapsedRealtime() - started} ms")
        return GameSource(rom, header, crc32, storage)
    }

    private fun startGame(source: GameSource) {
        if (isFinishing || isDestroyed) return
        val mirror = SaveMirror.forGame(this, source.storage)?.also {
            it.onUnavailable = {
                runOnUiThread {
                    if (!isFinishing) Toast.makeText(this, R.string.save_folder_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }
        if (BuildConfig.SINGLE_SCREEN) {
            startupTasks?.submit({ mirror?.syncIn() }) {
                saveMirror = mirror
                startEmulation(source)
            }
        } else {
            mirror?.syncIn()
            saveMirror = mirror
            startEmulation(source)
        }
    }

    private fun startEmulation(source: GameSource) {
        val (rom, header, crc32, storage) = source

        // With a save folder set, take in newer saves from it (another device) before the
        // core opens game.sav, then mirror changes out while playing.
        input = InputMapper().apply { swapAb = savedSwapAb }
        audioOut = AudioOut()
        val gameView = findViewById<GameView>(R.id.game_view)
        gameView.setShader(savedShader)
        RetroAchievements.init(this)
        achievementPopups = AchievementPopups(findViewById(android.R.id.content) as android.widget.FrameLayout).also { it.attach() }
        singleScreen?.setGameAspect(if (header.platform == Platform.GBA) 1.5f else 160f / 144f)
        emu = EmuThread(rom.bytes, storage.saveFile.absolutePath, gameView, audioOut, input)
        if (singleScreen != null) singleScreenActions = SingleScreenQuickActions(
            ready = { emu.loaded }, submit = { path, save, done -> emu.requestState(path, save, done) },
            path = SaveStates(storage).slotPath(0)
        ) { message ->
            if (!isFinishing && !isDestroyed) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        if (BuildConfig.SINGLE_SCREEN) emu.paused = !resumed
        saveMirror?.let { mirror ->
            emu.onSaveDataChanged = mirror::requestOut
        }
        // SS also releases audio here: destruction must not release a track while a slow
        // native startup is still using it. Both resources belong to this one session.
        emu.onStopped = {
            saveMirror?.finish()
            if (BuildConfig.SINGLE_SCREEN) audioOut.release()
        }
        emu.start()

        // The quick menu and companion screen talk to the core, so wait until the emu thread has loaded it.
        waitForLoadThenSetUp(gameView, header, crc32, storage)
    }

    override fun onNewIntent(incoming: Intent) {
        super.onNewIntent(incoming)
        if (incoming.getStringExtra(EXTRA_ROM_URI) == null) return
        val sameRom = incoming.getStringExtra(EXTRA_ROM_URI) == intent.getStringExtra(EXTRA_ROM_URI)
        setIntent(incoming)
        // Debug singleTask keeps one native core owner. Recreate destroys the old
        // emulation session before onCreate starts another ROM.
        if (!sameRom) recreate()
    }

    private fun waitForLoadThenSetUp(gameView: GameView, header: RomHeader, crc32: Long, storage: GameStorage) {
        gameView.post(object : Runnable {
            override fun run() {
                if (!emu.isAlive) {
                    if (singleScreen != null && !isFinishing && !isDestroyed) {
                        Toast.makeText(this@PlayerActivity, getString(R.string.load_failed, header.title), Toast.LENGTH_LONG).show()
                        finish()
                    }
                    return
                }
                if (emu.loaded) {
                    quickMenu = QuickMenu(
                        context = this@PlayerActivity,
                        emu = emu,
                        states = SaveStates(storage),
                        initialShader = savedShader,
                        initialSwapAb = savedSwapAb,
                        onSwapAb = { savedSwapAb = it; input.swapAb = it },
                        onSetFfSpeed = { emu.ffMultiplier = it },
                        onSetShader = { savedShader = it; gameView.setShader(it) },
                        onQuit = { finish() },
                        onAchievements = { done -> RetroAchievements.show(this@PlayerActivity, done) },
                    )
                    singleScreen?.gameLoaded()
                    setUpCompanion(header, crc32, storage)
                } else {
                    gameView.postDelayed(this, 16)
                }
            }
        })
    }

    /**
     * Shows the companion on the screen the game isn't on: a Presentation on the
     * second screen when the game is on the main one, or [CompanionActivity] on the
     * main screen when the game was opened on the second one.
     */
    private fun setUpCompanion(header: RomHeader, crc32: Long, storage: GameStorage) {
        val controller = CompanionController(this, header.title, header.gameCode, crc32)
        if (singleScreen != null) {
            companionScreen = controller
            startupTasks?.submit({
                val packsDir = File(filesDir, "packs")
                val manifest = storage.packId?.let { PackStore(packsDir).byId(it) }
                manifest?.let { pack ->
                    val directory = File(packsDir, pack.id)
                    Triple(pack, directory, runCatching { PreparedCompanion.load(this, pack, directory) })
                }
            }) { result ->
                singleScreen?.showCompanion(controller)
                result.onSuccess { assignment ->
                    if (assignment != null) {
                        val (manifest, directory, prepared) = assignment
                        prepared.onSuccess { sources ->
                            if (controller.showPreparedPack(manifest, directory, sources)) bindCompanion(controller, manifest, directory, header, storage)
                        }.onFailure { controller.showLoadError(manifest, it) }
                    }
                }.onFailure { android.util.Log.e("SingleScreen", "Could not resolve assigned companion", it) }
            }
            return
        }
        val displayManager = getSystemService(DisplayManager::class.java)
        val gameDisplay = display?.displayId ?: CompanionPlacement.MAIN_DISPLAY
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).map { it.displayId }
        when (val target = CompanionPlacement.choose(gameDisplay, presentationDisplays)) {
            is CompanionPlacement.Target.Presentation -> {
                val display = displayManager.getDisplay(target.displayId) ?: return
                companionPresentation = CompanionPresentation(this, display, controller, onKey = ::handleKeyEvent).also { it.show() }
            }
            is CompanionPlacement.Target.Activity -> {
                companionActivity = CompanionActivity.show(this, target.displayId, controller, onKey = ::handleKeyEvent, onBecameTop = ::keepFocusOnGame)
                    ?: return
            }
            CompanionPlacement.Target.None -> return
        }
        companionScreen = controller

        val packsDir = File(filesDir, "packs")
        val manifest = storage.packId?.let { PackStore(packsDir).byId(it) } ?: return
        val packDir = File(packsDir, manifest.id)
        if (!controller.showPack(manifest, packDir)) return

        bindCompanion(controller, manifest, packDir, header, storage)
    }

    private fun bindCompanion(controller: CompanionController, manifest: PackManifest, packDir: File, header: RomHeader, storage: GameStorage) {
        emu.activePackDir = packDir
        // What the pack's code is now: a write permission given to other code doesn't count.
        val fingerprint = if (manifest.canWriteMemory) PackFingerprint.of(packDir) else ""
        emu.writeAccess = writeAccessFor(manifest, storage, fingerprint)
        // Named as the library shows it ("The Legend of Zelda: The Minish Cap"), not by its header code.
        val gameName = GameTitle.display(intent.getStringExtra(EXTRA_ROM_FILE_NAME).orEmpty(), header.title)
        emu.onWriteConsentNeeded = { runOnUiThread { askWriteConsent(manifest, gameName, storage, fingerprint) } }
        emu.activePack = manifest
        emu.onSnapshot = { snapshot -> latestCompanionSnapshot.offer(snapshot); requestCompanionDraw() }
        // Taps on the companion screen reach the pack's Lua through the emu thread.
        emu.onActionResult = { result -> runOnUiThread { companionScreen?.applyScriptResult(result) } }
        controller.onUiChange = emu::setUiState
        controller.onScriptCall = emu::callAction
        emu.setUiState(controller.uiState)
    }

    /**
     * May [manifest]'s actions change this game? Only when the pack asks for it, the
     * "Allow packs to write to the game" setting is on, and the player allowed this pack
     * for this game; [EmuThread.WriteAccess.ASK] when they haven't been asked yet.
     */
    private fun writeAccessFor(manifest: PackManifest, storage: GameStorage, fingerprint: String): EmuThread.WriteAccess = when {
        !manifest.canWriteMemory || !CompanionSettings.allowWrites(this) -> EmuThread.WriteAccess.NONE
        else -> when (storage.writeConsent(manifest.id, fingerprint)) {
            true -> EmuThread.WriteAccess.ALLOWED
            false -> EmuThread.WriteAccess.NONE
            null -> EmuThread.WriteAccess.ASK
        }
    }

    /** The first time a pack is about to change a game: asks, pausing the game meanwhile. Saved per pack and game, for this version of the pack. */
    private fun askWriteConsent(manifest: PackManifest, gameTitle: String, storage: GameStorage, fingerprint: String) {
        if (isFinishing || isDestroyed) return
        val wasPaused = emu.paused
        emu.paused = true
        fun answer(allowed: Boolean) {
            storage.setWriteConsent(manifest.id, allowed, fingerprint)
            emu.answerWriteConsent(
                if (allowed) EmuThread.WriteAccess.ALLOWED else EmuThread.WriteAccess.NONE,
                // A save state to go back to, made just before the pack's first change.
                safetyState = if (allowed) storage.beforePackWriteState.absolutePath else null,
            )
            emu.paused = wasPaused || !resumed
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pack_write_title)
            .setMessage(getString(R.string.pack_write_message, manifest.name, gameTitle))
            .setPositiveButton(R.string.pack_write_allow) { _, _ -> answer(true) }
            .setNegativeButton(R.string.pack_write_deny) { _, _ -> answer(false) }
            .setCancelable(false)
            .show()
    }

    /**
     * Keeps the controller on the game. Android sends controller input to the focused
     * window, and focus can follow the "top" activity to the other screen: that happens
     * when [CompanionActivity] opens on the main screen, or is raised there again. The
     * companion's windows can't take focus themselves (and a [CompanionPresentation] is no
     * activity at all), so this is the one case to handle: each time, if the game is no
     * longer the top resumed activity a moment later, it moves itself back to the front
     * and takes the controller back.
     * Not while the quick menu is open (it has focus then) or the game is in the background.
     */
    private fun keepFocusOnGame() {
        val decor = window.decorView
        decor.removeCallbacks(reclaimFocus)
        decor.postDelayed(reclaimFocus, FOCUS_CHECK_DELAY_MS)
    }

    private val reclaimFocus = Runnable {
        if (resumed && !isFinishing && !topResumed && quickMenu?.isShowing != true) {
            // Restart this same instance on its own display: unlike moveTaskToFront (a no-op
            // when the task is already in front on its display), an activity start makes that
            // display the top focused one, and key events follow it there.
            val intent = Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(display?.displayId ?: CompanionPlacement.MAIN_DISPLAY)
            startActivity(intent, options.toBundle())
        }
    }

    /** Shared by this activity and the companion screen, so the game gets the controller whichever window has focus. */
    private val latestCompanionSnapshot = com.khumam.gbaduo.companion.LatestCompanionSnapshot()
    private val companionDrawQueued = java.util.concurrent.atomic.AtomicBoolean(false)
    private val companionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val directionMask = com.khumam.gbaduo.emu.GbaKeys.UP or com.khumam.gbaduo.emu.GbaKeys.DOWN or com.khumam.gbaduo.emu.GbaKeys.LEFT or com.khumam.gbaduo.emu.GbaKeys.RIGHT

    private fun requestCompanionDraw() {
        if (!::input.isInitialized || input.keys() and directionMask != 0) return
        if (!companionDrawQueued.compareAndSet(false, true)) return
        companionHandler.post {
            companionDrawQueued.set(false)
            if (!isFinishing && input.keys() and directionMask == 0) latestCompanionSnapshot.take()?.let { companionScreen?.update(it) }
        }
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L2 || event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (down && event.repeatCount == 0) showQuickMenu()
            return true
        }
        if (!::input.isInitialized) return false
        val handled = input.onKey(event.keyCode, down)
        if (handled && input.keys() and directionMask == 0) requestCompanionDraw()
        if (BuildConfig.DEBUG && handled && (event.repeatCount == 0 || !down)) android.util.Log.d("GbaDuoInput", "key=" + event.keyCode + " down=" + down + " device=" + event.deviceId + " lagMs=" + (android.os.SystemClock.uptimeMillis() - event.eventTime) + " mask=" + input.keys() + " ff=" + input.fastForward())
        return handled
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleKeyEvent(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        handleKeyEvent(event) || super.onKeyUp(keyCode, event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        (singleScreen != null && handleKeyEvent(event)) || super.dispatchKeyEvent(event)

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        topResumed = isTopResumedActivity
    }

    private fun showQuickMenu() {
        // A slow document provider must not trap the player on the loading screen.
        if (singleScreen != null && quickMenu == null) {
            finish()
            return
        }
        if (quickMenu?.isShowing == true) return
        singleScreen?.releaseControls()
        quickMenu?.show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        singleScreen?.releaseControls()
        singleScreen?.requestLayout()
        singleScreen?.post { if (!isDestroyed) SingleScreenWindows.hideBars(this) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A key released after focus moved elsewhere would never reach us and stay held.
        if (!hasFocus && ::input.isInitialized) input.releaseAll()
        if (!hasFocus) singleScreen?.releaseControls()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
        if (::emu.isInitialized) emu.paused = true
        singleScreen?.releaseControls()
        if (singleScreen != null) {
            if (::input.isInitialized) input.releaseAll()
            findViewById<GameView>(R.id.game_view).onPause()
        }
        saveMirror?.requestOut()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::emu.isInitialized) emu.paused = quickMenu?.isShowing == true
        if (singleScreen != null) findViewById<GameView>(R.id.game_view).onResume()
    }

    override fun onDestroy() {
        singleScreenActions?.close()
        startupTasks?.close()
        singleScreen?.close()
        companionScreen?.close()
        achievementPopups?.detach()
        if (::emu.isInitialized) {
            emu.shutdown()
            emu.join(1000)
        }
        if (::audioOut.isInitialized && (!BuildConfig.SINGLE_SCREEN || !::emu.isInitialized)) audioOut.release()
        window.decorView.removeCallbacks(reclaimFocus)
        companionPresentation?.dismiss()
        companionActivity?.let(CompanionActivity::close)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
        const val EXTRA_ROM_FILE_NAME = "rom_file_name"
        private const val FOCUS_CHECK_DELAY_MS = 400L
    }
}
