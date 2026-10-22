package com.khumam.gbaduo.debug

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BridgeProtocolTest {
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
