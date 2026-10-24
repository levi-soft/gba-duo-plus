package com.khumam.gbaduo.debug

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class BridgeProtocolTest {
    @Test fun `inspection advertises capabilities and validates every range before reading`() {
        var inspected = 0
        val enhanced = BridgeProtocol(object : BridgeProtocol.Backend {
            override fun info() = JSONObject().put("platform", "gba")
            override fun romSize() = 0x1000000L
            override fun read(address: Long, length: Int) = error("Not a RAM request")
            override fun inspect(address: Long, length: Int): ByteArray { inspected++; return ByteArray(length) { 7 } }
            override fun data(): JSONObject? = null
        })
        val info = enhanced.handle(JSONObject("""{"command":"info"}""")).getJSONObject("result")
        assertEquals(2, info.getInt("bridgeVersion"))
        assertTrue(info.getJSONObject("capabilities").getBoolean("inspect"))
        val bad = listOf(0x04000000L, 0x09000000L, Long.MAX_VALUE, -1L, 0x06018000L)
        for (address in bad) {
            val ranges = JSONArray().put(JSONObject().put("address", 0x08000000).put("length", 1))
                .put(JSONObject().put("address", address).put("length", 1))
            assertFalse(enhanced.handle(JSONObject().put("command", "inspect").put("ranges", ranges)).getBoolean("ok"))
        }
        assertEquals(0, inspected)
        val result = enhanced.handle(JSONObject("""{"command":"inspect","ranges":[{"address":134217728,"length":4},{"address":83886080,"length":32}]}"""))
        assertTrue(result.getBoolean("ok")); assertEquals(2, inspected)
        assertFalse(enhanced.handle(JSONObject("""{"command":"inspect","ranges":[{"address":134217728.5,"length":1}]}""")).getBoolean("ok"))
        assertTrue(BridgeProtocol.validInspectionRange(0x08FFFFFF, 1, 0x1000000))
        assertFalse(BridgeProtocol.validInspectionRange(0x08FFFFFF, 2, 0x1000000))
        assertTrue(BridgeProtocol.validInspectionRange(0x050003FF, 1, 0x1000000))
        assertFalse(BridgeProtocol.validInspectionRange(0x070003FF, 2, 0x1000000))
    }
    private var reads = 0
    private val protocol = BridgeProtocol(object : BridgeProtocol.Backend {
        override fun info() = JSONObject().put("platform", "gba").put("gameCode", "AE7E").put("frame", 42)
        override fun read(address: Long, length: Int): ByteArray { reads++; return ByteArray(length) { (it and 255).toByte() } }
        override fun data(): JSONObject? = null
    })

    @Test fun `captures both RAM areas at a single backend frame`() {
        val result = protocol.handle(JSONObject("""{"command":"capture","id":1}"""))
        assertTrue(result.getBoolean("ok"))
        assertEquals(42, result.getJSONObject("result").getInt("frame"))
        val ranges = result.getJSONObject("result").getJSONArray("ranges")
        assertEquals(0x40000, ranges.getJSONObject(0).getInt("length"))
        assertEquals(0x8000, ranges.getJSONObject(1).getInt("length"))
        assertEquals(2, reads)
    }

    @Test fun `rejects writes and invalid ranges before touching memory`() {
        for (request in listOf("""{"command":"write"}""", """{"command":"read","ranges":[{"address":67108864,"length":4}]}""", """{"command":"read","ranges":[{"address":33554432,"length":4},{"address":33554432,"length":65536}]}""")) {
            assertFalse(protocol.handle(JSONObject(request)).getBoolean("ok"))
        }
        assertEquals(0, reads)
        assertFalse(BridgeProtocol.validRange(Long.MAX_VALUE, 16))
        assertFalse(BridgeProtocol.validRange(0x0203FFFF, 2))
        assertTrue(BridgeProtocol.validRange(0x0203FFFF, 1))
    }

    @Test fun `reports errors as errors rather than returning fake zero-filled RAM`() {
        val broken = BridgeProtocol(object : BridgeProtocol.Backend {
            override fun info(): JSONObject = error("No core loaded")
            override fun read(address: Long, length: Int) = byteArrayOf()
            override fun data(): JSONObject? = null
        })
        assertFalse(broken.handle(JSONObject("""{"command":"info"}""")).getBoolean("ok"))
    }
}
