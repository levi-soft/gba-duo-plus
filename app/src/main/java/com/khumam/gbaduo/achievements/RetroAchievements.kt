package com.khumam.gbaduo.achievements

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.khumam.gbaduo.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * RetroAchievements for GBADuo, built on rcheevos (achievements.c).
 *
 * - Login: username + password once; only the token the server returns is stored.
 *   Nothing touches the network unless you're logged in.
 * - Playing: [gameStarted] / [frame] / [paused] / [gameStopped] are called by EmuThread on
 *   the emu thread. While a game runs, server responses are queued and handed to rcheevos
 *   on that thread before the next frame, because processing them reads emulated memory.
 * - Softcore only for now (see achievements.c).
 *
 * The `@JvmStatic` callbacks below are called from native code; their names and
 * signatures must match achievements.c.
 */
object RetroAchievements {
    private const val TAG = "GBADuoRA"
    private const val TIMEOUT_MS = 30_000

    // Event types from achievements.c.
    const val EVENT_ACHIEVEMENT = 1
    const val EVENT_GAME_COMPLETED = 2
    const val EVENT_SERVER_ERROR = 3
    const val EVENT_DISCONNECTED = 4
    const val EVENT_RECONNECTED = 5

    sealed class State {
        object LoggedOut : State()
        object LoggingIn : State()
        data class LoggedIn(val username: String, val displayName: String, val score: Int) : State()
        data class Failed(val message: String) : State()
    }

    /** What happened in the running game, for on-screen popups. Delivered on the main thread. */
    interface GameListener {
        fun onGameLoaded(title: String?, total: Int, unlocked: Int, imageUrl: String?, error: String?)
        fun onEvent(type: Int, title: String?, description: String?, points: Int, badgeUrl: String?)
    }

    @Volatile var state: State = State.LoggedOut
        private set

    private val main = Handler(Looper.getMainLooper())
    private val http: ExecutorService = Executors.newFixedThreadPool(2) { r -> Thread(r, "gbaduo-ra-http") }
    private val stateListeners = CopyOnWriteArrayList<(State) -> Unit>()
    private val gameListeners = CopyOnWriteArrayList<GameListener>()

    private var credentials: AchievementCredentials? = null
    private val nativeLock = Any()
    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private var loadRequested = false
    @Volatile private var gameSummary = "No achievements loaded for this ROM."
    private var userAgent = "GBADuo/${BuildConfig.VERSION_NAME}"
    private var initialized = false

    /** True while a game session owns rc_client: responses then wait for the emu thread. */
    @Volatile private var gameActive = false
    private val pendingResponses = ConcurrentLinkedQueue<Triple<Long, ByteArray?, Int>>()
    private var lastIdleAt = 0L

    /** Safe to call often (every screen's onCreate). Logs back in with a stored token. */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        credentials = AchievementCredentials(context.applicationContext)
        // Never take a screen down with it: without the native library (e.g. JVM tests),
        // achievements just stay off.
        val ok = runCatching { RaNative.init() }.onFailure { Log.e(TAG, "rc_client unavailable", it) }.getOrDefault(false)
        if (!ok) {
            Log.e(TAG, "rc_client init failed")
            return
        }
        initialized = true
        // <product>/<version> (<system>) <extensions>, as RetroAchievements asks.
        userAgent = "GBA-Duo-Plus/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
            RaNative.userAgentClause()
        val stored = credentials?.load()
        val user = stored?.first
        val token = stored?.second
        if (user != null && token != null) {
            setState(State.LoggingIn)
            RaNative.loginWithToken(user, token)
        }
    }

    val storedUsername: String? get() = credentials?.load()?.first
    val isLoggedIn: Boolean get() = state is State.LoggedIn

    fun login(username: String, password: String) {
        if (!initialized) return
        setState(State.LoggingIn)
        nativeCommand { RaNative.loginWithPassword(username.trim(), password) }
    }

    fun logout() {
        if (!initialized) return
        nativeCommand { RaNative.logout(); loadRequested = false }
        credentials?.clear()
        setState(State.LoggedOut)
    }

    fun addStateListener(listener: (State) -> Unit) { stateListeners += listener; listener(state) }
    fun removeStateListener(listener: (State) -> Unit) { stateListeners -= listener }
    fun addGameListener(listener: GameListener) { gameListeners += listener }
    fun removeGameListener(listener: GameListener) { gameListeners -= listener }

    // ---- emu thread -----------------------------------------------------------------

    /** After the core has loaded the ROM. Starts a session only if you're logged in. */
    fun gameStarted() {
        if (!initialized) return
        val s = state
        synchronized(nativeLock) {
            gameActive = true
            loadRequested = s is State.LoggedIn || s is State.LoggingIn
            if (loadRequested) RaNative.loadGame()
        }
    }

    /** After every emulated frame. */
    fun frame() {
        if (!gameActive) return
        synchronized(nativeLock) { deliverPending(); RaNative.doFrame() }
    }

    /** While the game is paused; idles about once a second. */
    fun paused() {
        if (!gameActive) return
        synchronized(nativeLock) {
            deliverPending()
            val now = System.currentTimeMillis()
            if (now - lastIdleAt >= 1000) {
                lastIdleAt = now
                RaNative.idle()
            }
        }
    }

    /** Before the core unloads. */
    fun gameStopped() {
        if (!gameActive) return
        synchronized(nativeLock) {
            RaNative.unloadGame()
            gameActive = false
            loadRequested = false
            deliverPending()
            commands.clear()
        }
    }

    private fun deliverPending() {
        while (true) {
            val (handle, body, status) = pendingResponses.poll() ?: break
            RaNative.serverResponse(handle, body, status)
            body?.fill(0)
        }
    }

    private fun nativeCommand(command: () -> Unit) = synchronized(nativeLock) {
        if (gameActive) commands.add(command) else command()
    }
    /** Called between frames, including while a game menu is open. */
    fun serviceCommands() = synchronized(nativeLock) {
        while (true) (commands.poll() ?: break).invoke()
    }
    fun saveProgress(path: String) {
        if (!initialized) return
        RaNative.saveProgress()?.let { data ->
            try { runCatching { java.io.File("$path.ra").writeBytes(data) } } finally { data.fill(0) }
        }
    }
    fun loadProgress(path: String) {
        if (!initialized) return
        val data = runCatching { java.io.File("$path.ra").takeIf { it.length() in 1..4_194_304 }?.readBytes() }.getOrNull()
        try { RaNative.loadProgress(data) } finally { data?.fill(0) }
    }

    fun show(activity: android.app.Activity, onDismiss: () -> Unit) {
        if (!isLoggedIn) {
            android.app.AlertDialog.Builder(activity).setTitle("RetroAchievements")
                .setMessage("Sign in from Settings > RetroAchievements.")
                .setPositiveButton("Settings") { _, _ ->
                    onDismiss()
                    activity.startActivity(android.content.Intent(activity, com.khumam.gbaduo.settings.RetroAchievementsActivity::class.java))
                }.setNegativeButton("Close") { _, _ -> onDismiss() }
                .setOnCancelListener { onDismiss() }.show()
            return
        }
        nativeCommand {
            val list = org.json.JSONArray(RaNative.achievementList().toString(Charsets.UTF_8))
            main.post {
                if (activity.isFinishing || activity.isDestroyed) { onDismiss(); return@post }
                val column = android.widget.LinearLayout(activity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL; setPadding(24, 12, 24, 12)
                }
                val images = AchievementImages(java.io.File(activity.cacheDir, "ra-badges"))
                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    val row = android.widget.LinearLayout(activity).apply { setPadding(0, 12, 0, 12) }
                    val badge = android.widget.ImageView(activity)
                    val size = (56 * activity.resources.displayMetrics.density).toInt()
                    row.addView(badge, android.widget.LinearLayout.LayoutParams(size, size))
                    images.load(badge, item.optString("badge"))
                    row.addView(android.widget.TextView(activity).apply {
                        text = (if (item.optBoolean("unlocked")) "Unlocked: " else "Locked: ") +
                            item.optString("title") + " (" + item.optInt("points") + ")\n" + item.optString("description")
                        textSize = 16f
                    }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                    column.addView(row)
                }
                if (list.length() == 0) column.addView(android.widget.TextView(activity).apply { text = gameSummary })
                android.app.AlertDialog.Builder(activity).setTitle("RetroAchievements")
                    .setView(android.widget.ScrollView(activity).apply { addView(column) })
                    .setPositiveButton("Close") { _, _ -> onDismiss() }
                    .setOnCancelListener { onDismiss() }.show().setOnDismissListener { images.close() }
            }
        }
    }

    // ---- called from achievements.c ---------------------------------------------

    @JvmStatic
    fun serverCall(handle: Long, url: String, postData: String?, contentType: String?) {
        http.execute {
            var body: ByteArray? = null
            var status = -2 // Retryable transport error; rcheevos handles reconnect/retry.
            var connection: HttpURLConnection? = null
            runCatching {
                val address = URL(url)
                require(address.protocol == "https" && address.host in listOf("retroachievements.org", "www.retroachievements.org"))
                require(address.port in listOf(-1, 443) && address.userInfo == null)
                val active = address.openConnection() as HttpURLConnection
                connection = active
                active.instanceFollowRedirects = false
                active.connectTimeout = TIMEOUT_MS
                active.readTimeout = TIMEOUT_MS
                active.setRequestProperty("User-Agent", userAgent)
                if (postData != null) {
                    active.requestMethod = "POST"
                    active.doOutput = true
                    active.setRequestProperty("Content-Type", contentType ?: "application/x-www-form-urlencoded")
                    val bytes = postData.toByteArray()
                    try { active.outputStream.use { it.write(bytes) } } finally { bytes.fill(0) }
                }
                status = active.responseCode
                body = (if (status >= 400) active.errorStream else active.inputStream)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= 8 * 1024 * 1024) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    require(output.size() <= 8 * 1024 * 1024)
                    output.toByteArray()
                }
            }.onFailure { status = -2 /* No request, token or response content enters logs. */ }
            connection?.disconnect()
            synchronized(nativeLock) {
                if (gameActive) pendingResponses += Triple(handle, body, status)
                else { RaNative.serverResponse(handle, body, status); body?.fill(0) }
            }
        }
    }

    @JvmStatic
    fun onLogin(result: Int, error: String?, username: String?, displayName: String?, token: String?, score: Int) {
        if (result == 0 && username != null && token != null) {
            runCatching { credentials?.save(username, token) }
            setState(State.LoggedIn(username, displayName ?: username, score))
            if (gameActive && !loadRequested) nativeCommand {
                if (!loadRequested) { loadRequested = true; RaNative.loadGame() }
            }
        } else {
            // A rejected token means it expired or was revoked: ask for the password again.
            if (result == -34 || result == -35) credentials?.clear()
            setState(State.Failed(error ?: "Login failed"))
        }
    }

    @JvmStatic
    fun onGameLoad(result: Int, error: String?, title: String?, gameId: Int, total: Int, unlocked: Int, imageUrl: String?) {
        main.post {
            gameSummary = if (total > 0) "$title: $unlocked / $total unlocked" else "No achievements found for this ROM."
            gameListeners.forEach { it.onGameLoaded(title, total, unlocked, imageUrl, if (result == 0) null else error ?: "Unknown error") }
        }
    }

    @JvmStatic
    fun onEvent(type: Int, title: String?, description: String?, points: Int, badgeUrl: String?) {
        main.post { gameListeners.forEach { it.onEvent(type, title, description, points, badgeUrl) } }
    }

    private fun setState(newState: State) {
        state = newState
        main.post { stateListeners.forEach { it(newState) } }
    }

}
