package com.khumam.gbaduo.emu

import com.khumam.gbaduo.companion.PackManifest

/** Resolves once per pack change. Legacy packs retain their verified host profiles. */
internal object CompanionCursorProfile {
    fun resolve(pack: PackManifest?, code: String, crc: Long): CursorProfile? {
        if (pack == null || !pack.matches(code, crc)) return null
        return pack.cursorNavigation ?: CursorProfile.forRom(code, "%08X".format(crc))
    }
}

/** A single bounded standard-button pulse, requested only by an explicit Lua tap. */
internal class CompanionButtonInput {
    private var buttons = 0
    private var remaining = 0
    val active: Boolean get() = remaining > 0

    fun cancel() { buttons = 0; remaining = 0 }

    fun request(command: Int) {
        cancel()
        val keys = command and 0x3FF
        val frames = command ushr 10
        if (keys == 0 || frames !in 1..30 || keys and 0x30 == 0x30 || keys and 0xC0 == 0xC0) return
        buttons = keys
        remaining = frames
    }

    fun keys(physical: Int): Int {
        if (physical != 0) { cancel(); return physical }
        if (!active) return 0
        remaining--
        return buttons
    }
}
