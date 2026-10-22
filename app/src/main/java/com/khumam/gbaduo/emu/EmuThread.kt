package com.khumam.gbaduo.emu

import android.util.Log
import com.khumam.gbaduo.BuildConfig
import com.khumam.gbaduo.debug.BridgeProtocol
import com.khumam.gbaduo.debug.DebugBridge
import com.khumam.gbaduo.companion.CompanionSnapshot
import com.khumam.gbaduo.companion.LuaScript
import com.khumam.gbaduo.companion.NativeCoreMemoryReader
import com.khumam.gbaduo.companion.PackManifest
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.json.JSONObject
import com.khumam.gbaduo.achievements.RetroAchievementsSession

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
    private val achievements: RetroAchievementsSession? = null,
) : Thread("gbaduo-emu") {

    @Volatile private var running = false
    @Volatile var paused = false
    @Volatile var ffMultiplier = 2
    /** Explicit per-session opt-in for Studio's read-only RAM tools. */
    @Volatile var studioConnectionEnabled = false

    private val frameNanos = (1_000_000_000.0 / 59.7275).toLong()

    /** Only until the core has its own copy: a whole ROM (up to 32 MB) shouldn't be held twice. */
    @Volatile private var romBytes: ByteArray? = romBytes

    private val pendingSaveState = java.util.concurrent.atomic.AtomicReference<Pair<String, Boolean>?>(null)
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

    private var framesSinceSnapshot = 0
    private var scriptLoadedForPack: PackManifest? = null
    private var scriptActive = false

    @Volatile private var uiState: Map<String, Any?> = emptyMap()
    @Volatile private var uiStateChanged = true
    @Volatile private var snapshotRequested = false
    private val pendingCalls = ConcurrentLinkedQueue<Pair<String, List<Any?>>>()
    private val debugRequests = ArrayBlockingQueue<FutureTask<JSONObject>>(16)
    private val debugSession = UUID.randomUUID().toString()
    private var debugGameCode = ""
    private var debugCrc = ""
    private var latestDebugData: Map<String, Any?>? = null
    private var latestDebugFrame: Long? = null
    private var effectiveInputKeys = 0
    private var navigatorTarget = 0
    private val debugProtocol = BridgeProtocol(object : BridgeProtocol.Backend {
        override fun info() = JSONObject().put("sessionId", debugSession)
            .put("gameCode", debugGameCode).put("crc32", debugCrc)
            .put("platform", if (NativeCore.videoWidth() == 240) "gba" else "gb")
            .put("frame", NativeCore.frameCounter()).put("paused", paused)
            .put("packId", activePack?.id ?: JSONObject.NULL)
            .put("dataFrame", latestDebugFrame ?: JSONObject.NULL)
            .put("inputKeys", input.keys()).put("fastForward", input.fastForward())
            .put("effectiveInputKeys", effectiveInputKeys).put("cursorNavigation", navigatorTarget)
        override fun read(address: Long, length: Int) = NativeCore.readRam(address, length)
        override fun data() = latestDebugData?.let { JSONObject(it) }
    })

    private fun requestDebug(request: JSONObject): JSONObject {
        val task = FutureTask { debugProtocol.handle(request) }
        fun failure(message: String) = JSONObject().put("protocol", 1)
            .put("id", request.opt("id") ?: JSONObject.NULL).put("ok", false).put("error", message)
        if (!debugRequests.offer(task)) return failure("Bridge queue full")
        return try { task.get(5, TimeUnit.SECONDS) }
        catch (error: Exception) {
            task.cancel(false)
            failure("Emulator request timed out or session ended")
        }
    }

    private fun serviceDebugRequests() {
        // Bound the work per frame even if a client sends requests continuously.
        repeat(4) { (debugRequests.poll() ?: return).run() }
    }

    override fun run() {
        loaded = loadCore()
        if (!loaded) return
        achievements?.start()

        debugGameCode = NativeCore.gameCode().removePrefix("AGB-")
        debugCrc = "%08X".format(NativeCore.crc32())
        val cursorProfile = CursorProfile.forRom(debugGameCode,debugCrc)
        val cursorNavigator = Fe7CursorNavigator(NativeCore::rawRead8, NativeCore::rawRead16, NativeCore::rawRead32,cursorProfile ?: CursorProfile.FE7)
        val supportsCursor = cursorProfile != null
        var bridge: DebugBridge? = null

        try {
        gameView.setVideoSize(NativeCore.videoWidth(), NativeCore.videoHeight())
        gameView.frameBuffer = NativeCore.videoBuffer()
        audioOut.start()
        running = true

        var nextFrameAt = System.nanoTime()
        while (running) {
            val wantsBridge = BuildConfig.DEBUG || studioConnectionEnabled
            if (wantsBridge && bridge == null) bridge = DebugBridge(::requestDebug, "gbaduo-plus-studio").also { it.start() }
            if (!wantsBridge && bridge != null) { bridge.close(); bridge = null }
            serviceDebugRequests()
            if (activePack !== scriptLoadedForPack) cursorNavigator.cancel()
            achievements?.service(paused)
            if (paused) {
                cursorNavigator.cancel()
                // Taps on the companion screen still get their data while the game is paused.
                if (snapshotRequested) serviceCompanion(activePack)
                NativeCore.takeScriptFocusUnit() // discard taps made while paused
                Thread.sleep(16)
                nextFrameAt = System.nanoTime()
                continue
            }
            if (pendingSaveState.get() != null) cursorNavigator.cancel()
            servicePendingStateRequest()
            val focusUnit = NativeCore.takeScriptFocusUnit()
            if (focusUnit != 0) cursorNavigator.focus(focusUnit, supportsCursor)

            val fastForward = input.fastForward()
            val speed = if (fastForward) ffMultiplier.coerceAtLeast(2) else 1

            repeat(speed) {
                val physical = input.keys()
                val effective = cursorNavigator.keys(physical)
                if (BuildConfig.DEBUG && effective != effectiveInputKeys) android.util.Log.d("GbaDuoInput", "frame=" + NativeCore.frameCounter() + " physical=" + physical + " effective=" + effective + " target=" + cursorNavigator.activeTarget + " ff=" + input.fastForward())
                effectiveInputKeys = effective; navigatorTarget = cursorNavigator.activeTarget
                NativeCore.runFrame(effective)
                achievements?.let { com.khumam.gbaduo.achievements.NativeAchievements.frame(false) }
            }
            gameView.postFrame()
            audioOut.pump(mute = speed != 1)

            serviceCompanion(activePack)

            nextFrameAt += frameNanos / speed
            val sleepNanos = nextFrameAt - System.nanoTime()
            if (sleepNanos > 0) {
                Thread.sleep(sleepNanos / 1_000_000, (sleepNanos % 1_000_000).toInt())
            } else {
                nextFrameAt = System.nanoTime()
            }
        }

        } finally {
            bridge?.close()
            while (true) (debugRequests.poll() ?: break).cancel(false)
            LuaScript.unload()
            achievements?.stop()
            audioOut.stop()
            NativeCore.unload()
            loaded = false
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
                    val (name, args) = pendingCalls.poll() ?: break
                    val result = LuaScript.call(name, args)
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
        if (!due && input.keys() and directions != 0) return
        if (due || framesSinceSnapshot >= interval) {
            framesSinceSnapshot = 0
            val snapshotStarted = System.nanoTime()
            val snapshot = CompanionSnapshot.build(pack, NativeCoreMemoryReader, NativeCore.frameCounter(), scriptActive)
            if (BuildConfig.DEBUG) android.util.Log.d("GbaDuoPerf", "snapshotMs=" + (System.nanoTime() - snapshotStarted) / 1_000_000)
            latestDebugData = snapshot
            latestDebugFrame = NativeCore.frameCounter()
            onSnapshot?.invoke(snapshot)
        }
    }

    /** Thread-safe: the companion screen's UI state changed (the script sees it as `ui`). */
    fun setUiState(state: Map<String, Any?>) {
        uiState = state
        uiStateChanged = true
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

    /** Thread-safe; services the request on the emu thread between frames. */
    fun requestState(path: String, save: Boolean) {
        pendingSaveState.set(path to save)
    }

    private fun servicePendingStateRequest() {
        val request = pendingSaveState.getAndSet(null) ?: return
        val (path, save) = request
        lastStateResult = if (save) NativeCore.saveState(path) else NativeCore.loadState(path)
        if (lastStateResult == true) {
            if (save) achievements?.saveProgress(path) else achievements?.loadProgress(path)
        }
    }

    fun shutdown() {
        running = false
    }

    private companion object {
        const val TAG = "EmuThread"
    }
}
