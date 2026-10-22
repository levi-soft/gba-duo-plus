package com.khumam.gbaduo.achievements

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageView
import android.view.Gravity
import com.khumam.gbaduo.R
import com.khumam.gbaduo.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class RetroAchievementsSession(context: Context, private val onUnlock: (JSONObject) -> Unit, private val onNotice: (String) -> Unit) {
    private val images = AchievementImages(File(context.cacheDir, "ra-badges"))
    private val credentials = AchievementCredentials(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private val network = Executors.newFixedThreadPool(2)
    @Volatile private var closed = false
    @Volatile private var status = JSONObject().put("message", "Starting RetroAchievements...")
    private var statusFrames = 0

    /** Called after NativeCore.load, on the same thread that owns the core. */
    fun start() {
        NativeAchievements.start()
        refreshStatus()
        credentials.load()?.let { (user, token) -> NativeAchievements.login(user, token, true) }
        refreshStatus()
    }

    fun service(paused: Boolean) {
        while (true) (commands.poll() ?: break).invoke()
        if (paused) NativeAchievements.frame(true)
        while (true) {
            val bytes = NativeAchievements.pollRequest() ?: break
            val request = JSONObject(bytes.toString(Charsets.UTF_8))
            bytes.fill(0)
            network.execute { http(request) }
        }
        while (true) {
            val bytes = NativeAchievements.pollEvent() ?: break
            val event = JSONObject(bytes.toString(Charsets.UTF_8))
            bytes.fill(0)
            if (event.optString("type") == "auth") {
                runCatching { credentials.save(event.getString("username"), event.getString("token")) }
                    .onFailure { main.post { onNotice("Signed in for this session. Could not save the encrypted login.") } }
                val signedIn = JSONObject().put("type", "popup").put("title", "Signed in to RetroAchievements").put("subtitle", event.getString("username"))
                main.post { if (!closed) onUnlock(signedIn) }
            } else if (event.optString("type") in listOf("unlock", "popup")) main.post { if (!closed) onUnlock(event) }
            else main.post { if (!closed) onNotice(event.optString("text")) }
            refreshStatus()
        }
        if (++statusFrames >= 30) { statusFrames = 0; refreshStatus() }
    }

    private fun refreshStatus() { status = JSONObject(NativeAchievements.status().toString(Charsets.UTF_8)) }

    private fun http(request: JSONObject) {
        val id = request.getLong("id")
        var response = ByteArray(0)
        var code = -2 // retryable transport failure; rcheevos owns retries and pending unlocks.
        var connection: HttpsURLConnection? = null
        try {
            val original = URL(request.getString("url"))
            require(original.host == "retroachievements.org" || original.host == "www.retroachievements.org")
            require(original.protocol == "https" && original.port in listOf(-1, 443) && original.userInfo == null)
            connection = original.openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "GBA-Duo-Plus/${BuildConfig.VERSION_NAME.substringBefore('-')} (Android ${android.os.Build.VERSION.RELEASE})")
            if (!request.isNull("post")) {
                val post = request.getString("post").toByteArray(Charsets.UTF_8)
                try {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", request.getString("contentType"))
                    connection.setFixedLengthStreamingMode(post.size)
                    connection.outputStream.use { it.write(post) }
                } finally { post.fill(0) }
            }
            code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 8 * 1024 * 1024) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                response = output.toByteArray()
            }
            if (response.size > 8 * 1024 * 1024) { response.fill(0); response = ByteArray(0); code = -1 }
        } catch (_: Exception) {
            // Never log the request URL, POST body or server response: these can contain credentials.
        } finally { connection?.disconnect() }
        if (!closed) {
            val body = response; val result = code
            commands.add { NativeAchievements.response(id, body, result); body.fill(0) }
        } else response.fill(0)
    }

    fun saveProgress(path: String) {
        NativeAchievements.saveProgress()?.let { bytes -> runCatching { File("$path.ra").writeBytes(bytes) }; bytes.fill(0) }
    }
    fun loadProgress(path: String) {
        val bytes = runCatching { File("$path.ra").takeIf { it.length() in 1..4_194_304 }?.readBytes() }.getOrNull()
        NativeAchievements.loadProgress(bytes)
        bytes?.fill(0)
    }

    fun stop() {
        closed = true
        network.shutdownNow()
        images.close()
        commands.clear()
        NativeAchievements.stop()
    }

    /** Account information is entered on the device, never passed through Studio/ADB. */
    fun show(activity: Activity, onDismiss: () -> Unit) {
        val snapshot = status
        val text = buildString {
            append(snapshot.optString("message"))
            if (snapshot.optBoolean("loggedIn")) {
                append("\n\n").append(snapshot.optString("username"))
                if (snapshot.optString("game").isNotEmpty()) append("\n").append(snapshot.optString("game"))
                append("\n").append(snapshot.optInt("unlocked")).append(" / ").append(snapshot.optInt("total")).append(" unlocked")
            }
            append("\n\nSave states allowed.")
        }
        val builder = AlertDialog.Builder(activity).setTitle("RetroAchievements").setIcon(R.drawable.retroachievements_mark).setMessage(text)
            .setNegativeButton("Close") { _, _ -> onDismiss() }
            .setOnCancelListener { onDismiss() }
        if (snapshot.optBoolean("loggedIn")) {
            builder.setPositiveButton("Achievements") { _, _ -> showAchievements(activity, onDismiss) }
            builder.setNeutralButton("Sign out") { _, _ ->
                credentials.clear(); commands.add { NativeAchievements.logout(); refreshStatus() }; onDismiss()
            }
        } else builder.setPositiveButton("Sign in") { _, _ -> showLogin(activity, onDismiss) }
        val dialog = builder.show()
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { dialog.getButton(it)?.setTextColor(android.graphics.Color.WHITE) }
    }

    private fun showLogin(activity: Activity, onDismiss: () -> Unit) {
        val username = EditText(activity).apply { hint = "Username"; isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD }
        val password = EditText(activity).apply { hint = "Password"; isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val form = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 8); addView(username); addView(password) }
        val dialog = AlertDialog.Builder(activity).setTitle("Sign in to RetroAchievements").setView(form)
            .setNegativeButton("Cancel") { _, _ -> password.text.clear(); onDismiss() }
            .setPositiveButton("Sign in", null).setOnCancelListener { password.text.clear(); onDismiss() }.create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val user = username.text.toString().trim(); val secret = password.text.toString()
            if (user.isBlank() || secret.isEmpty()) { password.error = "Enter your username and password"; return@setOnClickListener }
            password.text.clear()
            commands.add { NativeAchievements.login(user, secret, false) }
            dialog.dismiss(); onDismiss()
        } }
        dialog.show()
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun showAchievements(activity: Activity, onDismiss: () -> Unit) {
        commands.add {
            val list = JSONArray(NativeAchievements.achievementList().toString(Charsets.UTF_8))
            main.post {
                if (activity.isFinishing || activity.isDestroyed) { onDismiss(); return@post }
                val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12) }
                for (n in 0 until list.length()) {
                    val a = list.getJSONObject(n)
                    val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 12, 0, 12) }
                    val badge = ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = if (a.getBoolean("unlocked")) "Unlocked badge" else "Locked badge" }
                    val size = (64 * activity.resources.displayMetrics.density).toInt()
                    row.addView(badge, LinearLayout.LayoutParams(size, size).apply { rightMargin = 20 })
                    images.load(badge, a.optString("badge"))
                    val text = buildString {
                        append(if (a.getBoolean("unlocked")) "✓ " else "○ ")
                        append(a.getString("title")).append(" · ").append(a.getInt("points")).append(" points\n")
                        append(a.getString("description")).append("\n")
                        if (a.optString("progress").isNotEmpty()) append(a.getString("progress")).append("\n")
                        append("\n")
                    }
                    row.addView(TextView(activity).apply { this.text = text; textSize = 16f }, LinearLayout.LayoutParams(0, -2, 1f))
                    content.addView(row)
                }
                if (list.length() == 0) content.addView(TextView(activity).apply { text = "No achievements loaded for this ROM." })
                AlertDialog.Builder(activity).setTitle("Achievements").setView(ScrollView(activity).apply { addView(content) })
                    .setPositiveButton("Close") { _, _ -> onDismiss() }.setOnCancelListener { onDismiss() }.show()
            }
        }
    }
}
