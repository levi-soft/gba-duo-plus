package com.khumam.gbaduo.debug

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Read-only protocol. Its backend is called exclusively between emulator frames. */
class BridgeProtocol(private val backend: Backend) {
    interface Backend {
        fun info(): JSONObject
        fun read(address: Long, length: Int): ByteArray
        fun data(): JSONObject?
    }

    fun handle(request: JSONObject): JSONObject {
        val response = JSONObject().put("protocol", 1).put("id", request.opt("id") ?: JSONObject.NULL)
        return try {
            val info = backend.info()
            val result = when (request.getString("command")) {
                "info" -> info
                "data" -> info.put("data", backend.data() ?: JSONObject.NULL)
                "read" -> {
                    require(info.getString("platform") == "gba") { "RAM reading currently supports GBA only" }
                    val ranges = request.getJSONArray("ranges")
                    require(ranges.length() in 1..64) { "Expected 1..64 ranges" }
                    var total = 0L
                    // Validate the entire request before reading anything.
                    for (i in 0 until ranges.length()) {
                        val range = ranges.getJSONObject(i)
                        val length = range.getInt("length")
                        require(validRange(range.getLong("address"), length)) { "Invalid GBA RAM range" }
                        total += length
                    }
                    require(total <= 65536) { "Read requests are limited to 64 KiB" }
                    val output = JSONArray()
                    for (i in 0 until ranges.length()) {
                        val range = ranges.getJSONObject(i)
                        output.put(readRange(range.getLong("address"), range.getInt("length")))
                    }
                    info.put("ranges", output)
                }
                "capture" -> {
                    require(info.getString("platform") == "gba") { "RAM capture currently supports GBA only" }
                    info.put("ranges", JSONArray()
                        .put(readRange(0x02000000, 0x40000).put("name", "ewram"))
                        .put(readRange(0x03000000, 0x8000).put("name", "iwram")))
                }
                else -> throw IllegalArgumentException("Unknown read-only command")
            }
            response.put("ok", true).put("result", result)
        } catch (error: Exception) {
            response.put("ok", false).put("error", error.message ?: "Bridge request failed")
        }
    }

    private fun readRange(address: Long, length: Int): JSONObject {
        val bytes = backend.read(address, length)
        check(bytes.size == length) { "Incomplete RAM read" }
        return JSONObject().put("address", address).put("length", length)
            .put("base64", Base64.getEncoder().encodeToString(bytes))
    }

    companion object {
        fun validRange(address: Long, length: Int): Boolean {
            if (length <= 0 || length > 0x40000) return false
            return (address >= 0x02000000 && address < 0x02040000 && length <= 0x02040000 - address) ||
                (address >= 0x03000000 && address < 0x03008000 && length <= 0x03008000 - address)
        }
    }
}
