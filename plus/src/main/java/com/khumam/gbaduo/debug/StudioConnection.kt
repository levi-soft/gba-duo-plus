package com.khumam.gbaduo.debug

import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Plus-owned transport lifecycle. The host calls service only between emulator frames. */
internal class StudioConnection(backend: BridgeProtocol.Backend) : Closeable {
    private val protocol = BridgeProtocol(backend)
    private val requests = ArrayBlockingQueue<FutureTask<JSONObject>>(16)
    private var bridge: DebugBridge? = null

    fun service(enabled: Boolean) {
        if (enabled && bridge == null) {
            bridge = DebugBridge(::request, "gbaduo-plus-studio").also { it.start() }
        }
        if (!enabled && bridge != null) close()
        repeat(4) { (requests.poll() ?: return).run() }
    }

    private fun request(request: JSONObject): JSONObject {
        val task = FutureTask { protocol.handle(request) }
        fun failure(message: String) = JSONObject().put("protocol", 1)
            .put("id", request.opt("id") ?: JSONObject.NULL).put("ok", false).put("error", message)
        if (!requests.offer(task)) return failure("Bridge queue full")
        return try { task.get(5, TimeUnit.SECONDS) }
        catch (error: Exception) {
            task.cancel(false)
            failure("Emulator request timed out or session ended")
        }
    }

    override fun close() {
        bridge?.close()
        bridge = null
        while (true) (requests.poll() ?: break).cancel(false)
    }
}
