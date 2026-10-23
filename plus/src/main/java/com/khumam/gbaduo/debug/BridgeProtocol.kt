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
        fun romSize(): Long = 0
        fun inspect(address: Long, length: Int): ByteArray = error("Inspection is not supported by this backend")
    }

    fun handle(request: JSONObject): JSONObject {
        val response = JSONObject().put("protocol", 1).put("id", request.opt("id") ?: JSONObject.NULL)
        return try {
            val info = backend.info()
            val romSize = backend.romSize()
            val inspection = info.optString("platform") == "gba" && romSize in 1..0x02000000L
            info.put("bridgeVersion", 2).put("romSize", romSize).put("capabilities", JSONObject()
                .put("ram", info.optString("platform") == "gba").put("inspect", inspection)
                .put("maxRanges", 64).put("maxReadBytes", 65536).put("maxRangeBytes", 4096))
            val result = when (request.getString("command")) {
                "info" -> info
                "data" -> info.put("data", backend.data() ?: JSONObject.NULL)
                "inspect" -> {
                    require(inspection) { "GBA ROM inspection is unavailable" }
                    val ranges = request.getJSONArray("ranges")
                    require(ranges.length() in 1..64) { "Expected 1..64 ranges" }
                    var total = 0L
                    for (i in 0 until ranges.length()) {
                        val range = ranges.getJSONObject(i)
                        require(range.get("address") is Int || range.get("address") is Long) { "Address must be an integer" }
                        require(range.get("length") is Int || range.get("length") is Long) { "Length must be an integer" }
                        val address = range.getLong("address")
                        val length = range.getLong("length")
                        require(length in 1..4096 && validInspectionRange(address, length.toInt(), romSize)) { "Invalid inspection range" }
                        total += length
                    }
                    require(total <= 65536) { "Inspection is limited to 64 KiB per request" }
                    val output = JSONArray()
                    for (i in 0 until ranges.length()) {
                        val range = ranges.getJSONObject(i)
                        val address = range.getLong("address"); val length = range.getInt("length")
                        output.put(encodedRange(address, length, backend.inspect(address, length)))
                    }
                    info.put("ranges", output)
                }
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
        return encodedRange(address, length, backend.read(address, length))
    }

    private fun encodedRange(address: Long, length: Int, bytes: ByteArray): JSONObject {
        check(bytes.size == length) { "Incomplete RAM read" }
        return JSONObject().put("address", address).put("length", length)
            .put("base64", Base64.getEncoder().encodeToString(bytes))
    }

    companion object {
        fun validInspectionRange(address: Long, length: Int, romSize: Long): Boolean {
            if (length !in 1..65536) return false
            val regions = listOf(0x02000000L to 0x02040000L, 0x03000000L to 0x03008000L,
                0x05000000L to 0x05000400L, 0x06000000L to 0x06018000L, 0x07000000L to 0x07000400L)
            if (regions.any { (start, end) -> address >= start && address < end && length <= end - address }) return true
            return romSize in 1..0x02000000L && address >= 0x08000000L && address < 0x08000000L + romSize &&
                length <= 0x08000000L + romSize - address
        }
        fun validRange(address: Long, length: Int): Boolean {
            if (length <= 0 || length > 0x40000) return false
            return (address >= 0x02000000 && address < 0x02040000 && length <= 0x02040000 - address) ||
                (address >= 0x03000000 && address < 0x03008000 && length <= 0x03008000 - address)
        }
    }
}
