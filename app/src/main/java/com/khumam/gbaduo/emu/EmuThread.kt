package com.khumam.gbaduo.emu

import android.util.Log
import com.khumam.gbaduo.BuildConfig
import com.khumam.gbaduo.debug.BridgeProtocol
import com.khumam.gbaduo.debug.StudioConnection
import com.khumam.gbaduo.debug.StudioMemory
import com.khumam.gbaduo.companion.CompanionSnapshot
import com.khumam.gbaduo.companion.LuaScript
import com.khumam.gbaduo.companion.NativeCoreMemoryReader
import com.khumam.gbaduo.companion.PackManifest
import com.khumam.gbaduo.companion.WideState
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.UUID
import org.json.JSONObject
import com.khumam.gbaduo.achievements.RetroAchievements

/**
 * Owns the native core for one play session. Runs frames paced to the GBA's
 * native rate (or faster under fast-forward) on its own thread.
 */
class EmuThread(
    romBytes: ByteArray,
    private val savePath: String,
    private val gameView: GameView,
    private val audioOut: AudioOut,
    private val input: InputMapper,
) : Thread("gbaduo-emu") {

    @Volatile private var running = false
    private val coreSession = CoreSession()
    @Volatile var paused = false
    @Volatile var ffMultiplier = 2
    /** Initialized from this edition's saved Studio setting before the session starts. */
    @Volatile var studioConnectionEnabled = false

    private val frameNanos = (1_000_000_000.0 / 59.7275).toLong()

    /** Only until the core has its own copy: a whole ROM (up to 32 MB) shouldn't be held twice. */
    @Volatile private var romBytes: ByteArray? = romBytes

    private data class StateRequest(val path: String, val save: Boolean, val finished: ((Boolean) -> Unit)?)
    private val pendingSaveState = java.util.concurrent.atomic.AtomicReference<StateRequest?>(null)
    @Volatile var lastStateResult: Boolean? = null

    @Volatile var loaded = false
        private set

    /** Set by PlayerActivity once a pack is assigned; null means no companion data to compute. */
    @Volatile var activePack: PackManifest? = null

    /** Must be set alongside [activePack] — where its `manifest.scriptFile` (if any) lives. */
    @Volatile var activePackDir: File? = null

    /** Called on the emu thread (not main thread) with each new snapshot. */
    @Volatile var onSnapshot: ((Map<String, Any?>) -> Unit)? = null

    /** Called on the emu thread with what a script action returned (UI actions for the companion screen). */
    @Volatile var onActionResult: ((Any?) -> Unit)? = null

    /** Whether the active pack's actions may change the game's memory (`mem.write*`). */
    enum class WriteAccess {
        /** No: the pack doesn't ask, the setting is off, or the player said no. Writes are refused. */
        NONE,

        /** Yes: the player allowed this pack for this game. */
        ALLOWED,

        /** Not decided yet: the next action waits while [onWriteConsentNeeded] asks the player. */
        ASK,
    }

    /** The player turned Wide view on for this game (it also needs the pack's game.lua to define `wide()`). */
    @Volatile var wideEnabled = false

    /** The active pack's game.lua defines `wide()`: Wide view can be turned on. Set on the emu thread. */
    @Volatile var wideAvailable = false
        private set

    /** Set by PlayerActivity with the pack; see [answerWriteConsent]. */
    @Volatile var writeAccess = WriteAccess.NONE

    /**
     * Called on this thread, once, when an action is waiting for the player to allow
     * (or refuse) writes. Answer with [answerWriteConsent]; until then actions stay queued.
     */
    @Volatile var onWriteConsentNeeded: (() -> Unit)? = null

    @Volatile private var consentAsked = false

    /** A save state to write right before the first allowed action runs (see [answerWriteConsent]). */
    @Volatile private var safetyStatePath: String? = null

    /**
     * Called on this thread when the game's save data may have changed on disk: the
     * save file's size or time changed (checked every [SAVE_CHECK_FRAMES]), or a save
     * state was written. Used to copy saves out to the save folder.
     */
    @Volatile var onSaveDataChanged: (() -> Unit)? = null

    /** Called on this thread as it ends, after the core is unloaded (its save written): every time, even after a failure. */
    @Volatile var onStopped: (() -> Unit)? = null

    /** Frames the core has run since the game started, fast-forwarded ones included; read by the performance overlay. */
    @Volatile var framesRun = 0L
        private set

    private val saveFile = File(savePath)
    private var saveSeen = 0L to 0L
    private var framesSinceSaveCheck = 0

    private var framesSinceSnapshot = 0
    private var scriptLoadedForPack: PackManifest? = null
    private var scriptActive = false

    private var gameScriptLoadedForPack: PackManifest? = null
    private var gameFrameHook = false
    private var gameWideHook = false
    private var wideRequested = WideView.FRAME_WIDTH
    private var gameErrorLogCountdown = 0

    @Volatile private var uiState: Map<String, Any?> = emptyMap()
    @Volatile private var uiStateChanged = true
    @Volatile private var snapshotRequested = false
    private val pendingCalls = ConcurrentLinkedQueue<Pair<String, List<Any?>>>()
    private val debugSession = UUID.randomUUID().toString()
    private var debugGameCode = ""
    private var debugCrc = ""
    private var debugPlatform = ""
    private var latestDebugData: Map<String, Any?>? = null
    private var latestDebugFrame: Long? = null
    private val retainedScriptSnapshot = com.khumam.gbaduo.companion.RetainedScriptSnapshot()
    private var effectiveInputKeys = 0
    private var navigatorTarget = 0
    private val studioConnection = StudioConnection(object : BridgeProtocol.Backend {
        override fun info() = JSONObject().put("sessionId", debugSession)
            .put("gameCode", debugGameCode).put("crc32", debugCrc)
            .put("platform", debugPlatform)
            .put("frame", NativeCore.frameCounter()).put("paused", paused)
            .put("packId", activePack?.id ?: JSONObject.NULL)
            .put("dataFrame", latestDebugFrame ?: JSONObject.NULL)
            .put("inputKeys", input.peekKeys()).put("fastForward", input.fastForward())
            .put("effectiveInputKeys", effectiveInputKeys).put("cursorNavigation", navigatorTarget)
        override fun read(address: Long, length: Int) = NativeCore.readRam(address, length)
        override fun romSize() = StudioMemory.romSize()
        override fun inspect(address: Long, length: Int) = StudioMemory.read(address, length)
        override fun data() = latestDebugData?.let { JSONObject(it) }
    })

    override fun run() {
        try {
            coreSession.runExclusive { play() }
        } finally {
            onStopped?.invoke()
        }
    }

    private fun play() {
        loaded = loadCore()
        if (!loaded) return
        saveSeen = saveFile.length() to saveFile.lastModified()
        RetroAchievements.gameStarted()

        // Cache the platform before Wide view changes the GBA output width.
        debugPlatform = if (NativeCore.videoWidth() == WideView.FRAME_WIDTH) "gba" else "gb"
        debugGameCode = NativeCore.gameCode().removePrefix("AGB-")
        debugCrc = "%08X".format(NativeCore.crc32())
        val romCrc = NativeCore.crc32()
        var inputPack: PackManifest? = null
        var cursorNavigator: FeMapCursorNavigator? = null
        val buttonInput = CompanionButtonInput()

        try {
        gameView.setVideoSize(NativeCore.videoWidth(), NativeCore.videoHeight())
        gameView.frameBuffer = NativeCore.videoBuffer()
        audioOut.start()
        running = true

        var nextFrameAt = System.nanoTime()
        while (running && !coreSession.stopped) {
            studioConnection.service(studioConnectionEnabled)
            val pack = activePack
            if (pack !== inputPack) {
                cursorNavigator?.cancel()
                buttonInput.cancel()
                NativeCore.takeScriptFocusUnit()
                NativeCore.takeScriptButtons()
                inputPack = pack
                cursorNavigator = CompanionCursorProfile.resolve(pack, debugGameCode, romCrc)?.let {
                    FeMapCursorNavigator(NativeCore::rawRead8, NativeCore::rawRead16, NativeCore::rawRead32, it)
                }
            }
            RetroAchievements.serviceCommands()
            if (paused) {
                cursorNavigator?.cancel()
                buttonInput.cancel()
                // Taps on the companion screen still get their data while the game is paused.
                if (snapshotRequested) serviceCompanion(activePack)
                NativeCore.takeScriptFocusUnit() // discard taps made while paused
                NativeCore.takeScriptButtons()
                RetroAchievements.paused()
                Thread.sleep(16)
                nextFrameAt = System.nanoTime()
                continue
            }
            val changingState = pendingSaveState.get() != null
            if (changingState) { cursorNavigator?.cancel(); buttonInput.cancel(); retainedScriptSnapshot.clear() }
            servicePendingStateRequest()
            val focusUnit = NativeCore.takeScriptFocusUnit()
            val buttons = NativeCore.takeScriptButtons()
            if (!changingState && pack === scriptLoadedForPack && pack?.matches(debugGameCode, romCrc) == true) {
                if (focusUnit != 0) { buttonInput.cancel(); cursorNavigator?.focus(focusUnit, true) }
                if (buttons != 0) { cursorNavigator?.cancel(); buttonInput.request(buttons) }
            }

            val fastForward = input.fastForward()
            val speed = if (fastForward) ffMultiplier.coerceAtLeast(2) else 1

            applyWide()
            repeat(speed) {
                val physical = input.keys()
                val effective = if (buttonInput.active) buttonInput.keys(physical) else cursorNavigator?.keys(physical) ?: physical
                if (BuildConfig.DEBUG && effective != effectiveInputKeys) android.util.Log.d("GbaDuoInput", "frame=" + NativeCore.frameCounter() + " physical=" + physical + " effective=" + effective + " target=" + (cursorNavigator?.activeTarget ?: 0) + " ff=" + input.fastForward())
                effectiveInputKeys = effective; navigatorTarget = cursorNavigator?.activeTarget ?: 0
                NativeCore.runFrame(effective)
                framesRun++
                runGameFrame()
                RetroAchievements.frame()
            }
            gameView.postFrame()
            audioOut.pump(mute = speed != 1)

            serviceCompanion(activePack)
            checkSaveFile()

            nextFrameAt += frameNanos / speed
            val sleepNanos = nextFrameAt - System.nanoTime()
            if (sleepNanos > 0) {
                Thread.sleep(sleepNanos / 1_000_000, (sleepNanos % 1_000_000).toInt())
            } else {
                nextFrameAt = System.nanoTime()
            }
        }

        } finally {
            studioConnection.close()
            LuaScript.unload()
            NativeCore.unloadGameScript()
            RetroAchievements.gameStopped()
            audioOut.stop()
            NativeCore.unload()
            loaded = false
        }
    }

    /** mGBA writes the save file itself shortly after the game saves; notice when it has. */
    private fun checkSaveFile() {
        if (++framesSinceSaveCheck < SAVE_CHECK_FRAMES) return
        framesSinceSaveCheck = 0
        val now = saveFile.length() to saveFile.lastModified()
        if (now != saveSeen) {
            saveSeen = now
            onSaveDataChanged?.invoke()
        }
    }

    /** In its own frame so nothing in [run] keeps the ROM reachable once the core has copied it. */
    private fun loadCore(): Boolean {
        val rom = romBytes ?: return false
        romBytes = null
        return NativeCore.load(rom, savePath)
    }

    /**
     * Hands UI state and queued taps to the pack's script, then builds a snapshot
     * every `tickHz`, or right away after a tap so the screen reacts quickly.
     */
    private fun serviceCompanion(pack: PackManifest?) {
        syncScriptForPack(pack)
        pack ?: return
        var due = false
        if (snapshotRequested) {
            snapshotRequested = false
            if (scriptActive) {
                if (uiStateChanged) {
                    uiStateChanged = false
                    LuaScript.setUi(uiState)
                }
                while (true) {
                    if (writeAccess == WriteAccess.ASK) {
                        // The pack may write: nothing runs until the player has answered.
                        if (pendingCalls.isNotEmpty() && !consentAsked) {
                            consentAsked = true
                            onWriteConsentNeeded?.invoke()
                        }
                        break
                    }
                    val (name, args) = pendingCalls.poll() ?: break
                    val mayWrite = GameScriptAccess.mayWrite(writeAccess, ::saveSafetyState)
                    val result = LuaScript.call(name, args, mayWrite)
                    if (result != null) {
                        onActionResult?.invoke(result)
                    } else {
                        LuaScript.lastError()?.let { Log.w(TAG, "actions.$name(): $it") }
                    }
                }
            } else {
                pendingCalls.clear()
            }
            due = true
        }
        framesSinceSnapshot++
        val interval = (60 / pack.tickHz).coerceAtLeast(1)
        // Automatic pack reads/Lua work share the emulation thread. Defer them
        // during physical cursor movement, just like the UI draw; taps still run immediately.
        val directions = GbaKeys.UP or GbaKeys.DOWN or GbaKeys.LEFT or GbaKeys.RIGHT
        if (!due && input.peekKeys() and directions != 0) return
        if (due || framesSinceSnapshot >= interval) {
            framesSinceSnapshot = 0
            val snapshotStarted = System.nanoTime()
            val snapshot = retainedScriptSnapshot.accept(
                CompanionSnapshot.build(pack, NativeCoreMemoryReader, NativeCore.frameCounter(), scriptActive, WideState(wideEnabled, wideAvailable))
            )
            if (BuildConfig.DEBUG) android.util.Log.d("GbaDuoPerf", "snapshotMs=" + (System.nanoTime() - snapshotStarted) / 1_000_000)
            latestDebugData = snapshot
            latestDebugFrame = NativeCore.frameCounter()
            onSnapshot?.invoke(snapshot)
        }
    }

    /** Thread-safe: the next frame builds a fresh snapshot for the companion screen (a built-in value changed). */
    fun requestSnapshot() {
        snapshotRequested = true
    }

    /** Thread-safe: the companion screen's UI state changed (the script sees it as `ui`). */
    fun setUiState(state: Map<String, Any?>) {
        uiState = state
        uiStateChanged = true
        snapshotRequested = true
    }

    /**
     * Thread-safe: the player's answer to [onWriteConsentNeeded]; runs the actions that
     * were waiting. With [safetyState], a save state is written there first, on this
     * thread, before the pack's first write can happen (even while the game is paused).
     */
    fun answerWriteConsent(access: WriteAccess, safetyState: String? = null) {
        safetyStatePath = safetyState
        writeAccess = access
        consentAsked = false
        snapshotRequested = true
    }

    /** Thread-safe: queues a call to the script's `actions.<name>(args...)`, run before the next snapshot. */
    fun callAction(name: String, args: List<Any?>) {
        pendingCalls += name to args
        snapshotRequested = true
    }

    /** Loads/unloads the pack's Lua script (if any) exactly once per pack change, on this thread. */
    private fun syncScriptForPack(pack: PackManifest?) {
        if (pack === scriptLoadedForPack) return
        retainedScriptSnapshot.clear()
        scriptLoadedForPack = pack
        scriptActive = false
        LuaScript.unload()

        val scriptFile = pack?.scriptFile ?: return
        val packDir = activePackDir ?: return
        val source = runCatching { File(packDir, scriptFile).readText() }.getOrNull() ?: return
        scriptActive = LuaScript.load(source, packDir.absolutePath)
        if (scriptActive) {
            // A fresh Lua state has no `ui` yet.
            uiStateChanged = false
            LuaScript.setUi(uiState)
        }
    }

    /** Loads/unloads the pack's game.lua (if any) once per pack change, on this thread. */
    private fun syncGameScriptForPack(pack: PackManifest?) {
        if (pack === gameScriptLoadedForPack) return
        gameScriptLoadedForPack = pack
        gameFrameHook = false
        gameWideHook = false
        wideAvailable = false
        NativeCore.unloadGameScript()

        val file = pack?.gameScriptFile ?: return
        val packDir = activePackDir ?: return
        val source = runCatching { File(packDir, file).readText() }.getOrNull() ?: return
        if (!NativeCore.loadGameScript(source, packDir.absolutePath)) {
            Log.w(TAG, "game.lua did not load: ${NativeCore.lastGameError()}")
            return
        }
        gameFrameHook = NativeCore.gameHas("frame")
        gameWideHook = NativeCore.gameHas("wide")
        wideAvailable = gameWideHook
    }

    /**
     * Between frames: the picture width the core should draw. Wide only while the player has it on and
     * game.lua defines `wide()`; otherwise the game's own 240. A wide picture is its own shape, so while it
     * is on the aspect ratio setting steps aside (square pixels, whatever Settings says).
     */
    private fun applyWide() {
        syncGameScriptForPack(activePack)
        val (surfaceW, surfaceH) = gameView.surfaceSize
        val wanted = WideView.wantedWidth(wideEnabled, gameWideHook, surfaceW, surfaceH, gameView.integerScaling)
        if (wanted == wideRequested) return
        wideRequested = wanted
        NativeCore.setWide(wanted)
        gameView.setVideoSize(NativeCore.videoWidth(), NativeCore.videoHeight())
        gameView.setAspectOverride(if (NativeCore.videoWidth() > WideView.FRAME_WIDTH) AspectRatio.ORIGINAL else null)
    }

    /** After each emulated frame: game.lua's `frame()`, which may change the game once the player has allowed it. */
    private fun runGameFrame() {
        syncGameScriptForPack(activePack)
        if (!gameFrameHook) return
        if (GameScriptAccess.needsConsent(writeAccess, hasFrameHook = true, alreadyAsked = consentAsked)) {
            consentAsked = true
            onWriteConsentNeeded?.invoke()
        }
        val mayWrite = GameScriptAccess.mayWrite(writeAccess, ::saveSafetyState)
        val width = NativeCore.videoWidth()
        val left = ((width - GBA_FRAME_WIDTH) / 2).coerceAtLeast(0)
        if (!NativeCore.gameFrame(NativeCore.frameCounter(), width, left, mayWrite) && gameErrorLogCountdown-- <= 0) {
            gameErrorLogCountdown = GAME_ERROR_LOG_FRAMES
            Log.w(TAG, "game.lua frame(): ${NativeCore.lastGameError()}")
        }
    }

    private fun saveSafetyState(): Boolean {
        val path = safetyStatePath ?: return true
        safetyStatePath = null
        File(path).parentFile?.mkdirs()
        if (NativeCore.saveState(path)) {
            RetroAchievements.saveProgress(path)
            onSaveDataChanged?.invoke()
            return true
        }
        writeAccess = WriteAccess.NONE
        Log.w(TAG, "pack writes refused: could not create the safety state")
        return false
    }

    /** Thread-safe; services the request on the emu thread between frames. */
    fun requestState(path: String, save: Boolean, finished: ((Boolean) -> Unit)? = null) {
        pendingSaveState.getAndSet(StateRequest(path, save, finished))?.finished?.invoke(false)
    }

    private fun servicePendingStateRequest() {
        val request = pendingSaveState.getAndSet(null) ?: return
        val (path, save, finished) = request
        lastStateResult = if (save) NativeCore.saveState(path) else NativeCore.loadState(path)
        if (lastStateResult == true) {
            if (save) RetroAchievements.saveProgress(path) else RetroAchievements.loadProgress(path)
        }
        if (save && lastStateResult == true) onSaveDataChanged?.invoke()
        finished?.invoke(lastStateResult == true)
    }

    fun shutdown() {
        coreSession.stop()
        running = false
        pendingSaveState.getAndSet(null)?.finished?.invoke(false)
    }

    private companion object {
        const val TAG = "EmuThread"

        /** About every 3 seconds. */
        const val SAVE_CHECK_FRAMES = 180

        /** game.lua errors are logged at most this often (5 seconds). */
        const val GAME_ERROR_LOG_FRAMES = 300

        const val GBA_FRAME_WIDTH = 240
    }
}
