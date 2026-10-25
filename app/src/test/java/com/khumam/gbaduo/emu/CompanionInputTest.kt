package com.khumam.gbaduo.emu

import com.khumam.gbaduo.companion.PackManifest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CompanionInputTest {
    private fun config(profile: CursorProfile = CursorProfile.FE6_EMBER): JSONObject = JSONObject().apply {
        put("version", 1); put("kind", "fe-map")
        for (field in listOf("cursor", "size", "chapter", "turn", "bm", "procs", "playerScript", "idlePc", "blue", "green", "red", "unavailableMask")) {
            put(field, "0x%08X".format(profile.javaClass.getDeclaredField(field).apply { isAccessible = true }.getLong(profile)))
        }
        put("unitPosition", profile.unitPosition); put("unitHp", profile.unitHp); put("unitStateBytes", profile.unitStateBytes)
    }

    private fun manifest(cursor: JSONObject? = config(), crc: Boolean = true): JSONObject = JSONObject("""
        {"id":"test","formatVersion":2,"match":{"gameCode":["TEST"],"crc32":["12345678"]}}
    """).apply {
        if (cursor != null) put("cursorNavigation", cursor)
        if (!crc) getJSONObject("match").remove("crc32")
    }

    @Test fun newRomUsesPackContractWithoutHostWhitelist() {
        val pack = PackManifest.parse(manifest().toString())
        assertNull(CursorProfile.forRom("TEST", "12345678"))
        assertEquals(CursorProfile.FE6_EMBER, CompanionCursorProfile.resolve(pack, "TEST", 0x12345678L))
        assertNull(CompanionCursorProfile.resolve(pack, "TEST", 0x12345679L))
        assertNull(CompanionCursorProfile.resolve(pack, "NOPE", 0x12345678L))
        assertNull(CompanionCursorProfile.resolve(null, "TEST", 0x12345678L))
        for (profile in listOf(CursorProfile.FE7, CursorProfile.FE8, CursorProfile.FE8_US, CursorProfile.FE6_TEN, CursorProfile.FE6_EMBER)) {
            assertEquals(profile, CursorProfileParser.parse(config(profile)))
        }
    }

    @Test fun missingConfigRetainsLegacyProfileOnlyForMatchingPack() {
        val pack = PackManifest.parse(manifest(null).apply {
            getJSONObject("match").put("gameCode", org.json.JSONArray("[\"AE7E\"]"))
            getJSONObject("match").put("crc32", org.json.JSONArray("[\"2A524221\"]"))
        }.toString())
        assertEquals(CursorProfile.FE7, CompanionCursorProfile.resolve(pack, "AE7E", 0x2A524221L))
        assertNull(CompanionCursorProfile.resolve(pack, "AFEJ", 0x197DE10BL))
        val generic = PackManifest.parse(manifest(null, false).toString())
        assertTrue(generic.matches("TEST", 1))
        assertNull(generic.cursorNavigation)
    }

    @Test fun malformedNavigationIsRejectedRatherThanFallingBack() {
        for ((key, value) in listOf("version" to 2, "unitHp" to 17.9, "version" to "1", "kind" to "unknown", "cursor" to "0x04000000",
            "size" to "0x0203FFFC", "procs" to "0x0203FFFF", "blue" to "0x0203FFF0",
            "idlePc" to "0x087FF000", "unitStateBytes" to 3, "unitHp" to 72,
            "unitPosition" to 12, "unavailableMask" to "0x10000", "typo" to 1)) {
            assertThrows("$key=$value", PackManifest.InvalidManifestException::class.java) {
                PackManifest.parse(manifest(config().put(key, value)).toString())
            }
        }
        assertThrows(PackManifest.InvalidManifestException::class.java) { PackManifest.parse(manifest(crc = false).toString()) }
        assertThrows(PackManifest.InvalidManifestException::class.java) { PackManifest.parse(manifest().put("cursorNavigation", JSONObject.NULL).toString()) }
    }

    @Test fun genericButtonsReleaseAndCancelWithoutUsingGameSpecificAddresses() {
        val input = CompanionButtonInput()
        for (bit in 0..9) {
            input.request((3 shl 10) or (1 shl bit))
            repeat(3) { assertEquals(1 shl bit, input.keys(0)) }
            assertEquals(0, input.keys(0)); assertFalse(input.active)
        }
        input.request((30 shl 10) or 1); assertEquals(GbaKeys.LEFT, input.keys(GbaKeys.LEFT)); assertFalse(input.active)
        input.request((30 shl 10) or 1); input.cancel(); assertEquals(0, input.keys(0))
        for (command in listOf(0, 1, (31 shl 10) or 1, (1 shl 10) or 0x30, (1 shl 10) or 0xC0, -1)) {
            input.request(command); assertFalse(input.active); assertEquals(0, input.keys(0))
        }
        input.request((1 shl 10) or (GbaKeys.A or GbaKeys.RIGHT))
        assertEquals(GbaKeys.A or GbaKeys.RIGHT, input.keys(0)); assertEquals(0, input.keys(0))
    }
}
