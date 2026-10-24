package com.khumam.gbaduo.debug

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import android.util.Log
import org.json.JSONObject
import java.io.Closeable

/** Read-only abstract Unix socket; accessible through ADB, never a LAN listener. */
class DebugBridge(private val execute: (JSONObject) -> JSONObject, private val socketName: String = "gbaduo-dev-bridge") : Closeable {
    @Volatile private var running = true
    @Volatile private var server: LocalServerSocket? = null
    @Volatile private var client: LocalSocket? = null
    private val worker = Thread({ serve() }, "gbaduo-debug-bridge").apply { isDaemon = true }

    fun start() = worker.start()

    private fun serve() {
        try {
            val listener = LocalServerSocket(socketName)
            server = listener
            if (!running) { listener.close(); return }
            Log.i("GBADuoBridge", "Read-only bridge ready")
            while (running) {
                listener.accept().use { socket ->
                    client = socket
                    try {
                        val uid = socket.peerCredentials.uid
                        // adbd on a normal device uses shell uid 2000.
                        require(uid == 2000 || uid == 0 || uid == Process.myUid()) { "Unauthorized peer" }
                        socket.soTimeout = 3000
                        val reader = socket.inputStream.bufferedReader(Charsets.UTF_8)
                        val line = StringBuilder()
                        while (true) {
                            val c = reader.read()
                            if (c < 0 || c == '\n'.code) break
                            require(line.length < 8192) { "Request too large" }
                            line.append(c.toChar())
                        }
                        if (line.isNotEmpty()) {
                            val response = execute(JSONObject(line.toString()))
                            socket.outputStream.write((response.toString() + "\n").toByteArray(Charsets.UTF_8))
                            socket.outputStream.flush()
                        }
                    } catch (error: Exception) {
                        if (running) Log.w("GBADuoBridge", error.message ?: "Request failed")
                    } finally { client = null }
                }
            }
        } catch (error: Exception) {
            if (running) Log.w("GBADuoBridge", "Bridge stopped: ${error.message}")
        } finally { runCatching { server?.close() } }
    }

    override fun close() {
        running = false
        runCatching { client?.close() }
        // Closing the listening descriptor alone may leave accept() holding a
        // reference to the abstract socket until another connection arrives.
        // Wake accept before closing, then wait for the old worker to release
        // the name before the next game starts its bridge.
        if (server != null) runCatching {
            LocalSocket().use { wake ->
                wake.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
                wake.shutdownOutput()
            }
        }
        runCatching { server?.close() }
        if (Thread.currentThread() !== worker) runCatching { worker.join(1000) }
    }
}
