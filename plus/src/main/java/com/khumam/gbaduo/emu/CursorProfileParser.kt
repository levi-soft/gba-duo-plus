package com.khumam.gbaduo.emu

import org.json.JSONObject

/** Versioned pack metadata. Only the fixed FE map algorithm consumes these reads. */
internal object CursorProfileParser {
    fun parse(json: JSONObject): CursorProfile {
        fun integer(name: String): Int {
            val value = json.get(name)
            require(value is Int || value is Long) { "Invalid cursorNavigation.$name" }
            val number = (value as Number).toLong()
            require(number in Int.MIN_VALUE..Int.MAX_VALUE) { "Invalid cursorNavigation.$name" }
            return number.toInt()
        }
        require(integer("version") == 1) { "Unsupported cursorNavigation version" }
        require(json.getString("kind") == "fe-map") { "Unsupported cursorNavigation kind" }
        val fields = setOf("version", "kind", "cursor", "size", "chapter", "turn", "bm", "procs",
            "playerScript", "idlePc", "blue", "green", "red", "unitPosition", "unitHp",
            "unitStateBytes", "unavailableMask")
        require(json.keys().asSequence().all { it in fields }) { "Unknown cursorNavigation field" }
        fun hex(name: String): Long {
            val text = json.get(name)
            require(text is String && text.matches(Regex("0[xX][0-9a-fA-F]{1,8}"))) { "Invalid cursorNavigation.$name" }
            return text.substring(2).toLong(16)
        }
        fun ram(name: String, bytes: Int, alignment: Int): Long = hex(name).also {
            require(it >= 0x02000000L && it + bytes <= 0x02040000L && it % alignment == 0L) { "Invalid RAM range for $name" }
        }
        fun rom(name: String): Long = hex(name).also {
            require(it in 0x08000000L..0x09FFFFF8L && it % 4 == 0L) { "Invalid ROM address for $name" }
        }
        fun offset(name: String, bytes: Int): Int = integer(name).also {
            require(it in 14..(72 - bytes)) { "Invalid unit offset for $name" }
        }
        val stateBytes = integer("unitStateBytes")
        require(stateBytes == 2 || stateBytes == 4) { "Invalid unitStateBytes" }
        val position = offset("unitPosition", 2)
        val hp = offset("unitHp", 1)
        require(position >= 12 + stateBytes && hp >= 12 + stateBytes && hp !in position..position + 1) { "Overlapping unit fields" }
        val mask = hex("unavailableMask")
        require(mask in 1..if (stateBytes == 2) 0xFFFFL else 0xFFFFFFFFL) { "Invalid unavailableMask" }
        val script = rom("playerScript")
        val pc = rom("idlePc")
        require(pc in script + 8 until script + 0x600) { "Idle PC outside player script" }
        return CursorProfile(ram("cursor", 4, 2), ram("size", 8, 4), ram("chapter", 2, 1),
            ram("turn", 2, 2), ram("bm", 3, 1), ram("procs", 64 * 108, 4), script, pc,
            ram("blue", 62 * 72, 4), ram("green", 20 * 72, 4), ram("red", 50 * 72, 4),
            position, hp, stateBytes, mask)
    }
}
