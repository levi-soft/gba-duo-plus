package com.khumam.gbaduo.emu

/** Verified FE map profiles. Cursor movement uses D-pad pulses, never RAM writes. */
internal class FeMapCursorNavigator(
    private val u8: (Long) -> Int,
    private val u16: (Long) -> Int,
    private val u32: (Long) -> Long,
    private val profile: CursorProfile = CursorProfile.FE7,
) {
    private var target = 0
    private var identity = 0L
    private var turn = 0
    private var chapter = 0
    private var age = 0
    private var cooldown = 0

    val activeTarget: Int get() = target
    fun cancel() { target = 0 }

    fun focus(id: Int, supportedRom: Boolean): Boolean {
        cancel()
        val address = profile.unitAddress(id) ?: return false
        if (!supportedRom || !idle() || !validUnit(id, address)) return false
        target = id; identity = u32(address); turn = u16(profile.turn); chapter = u8(profile.chapter)
        age = 0; cooldown = 0
        return true
    }

    fun keys(physical: Int): Int {
        if (physical != 0) { cancel(); return physical }
        if (target == 0) return 0
        val address = profile.unitAddress(target) ?: return 0.also { cancel() }
        if (++age > 600 || !idle() || u32(address) != identity || !validUnit(target,address) ||
            u16(profile.turn) != turn || u8(profile.chapter) != chapter) { cancel(); return 0 }
        val x = u16(profile.cursor); val y = u16(profile.cursor+2)
        val tx = u8(address+profile.unitPosition); val ty = u8(address+profile.unitPosition+1)
        if (x == tx && y == ty) { cancel(); return 0 }
        // One pressed frame followed by three released frames. This is standard
        // game input: camera scrolling and cursor side effects remain game-owned.
        if (cooldown > 0) { cooldown--; return 0 }
        cooldown = 3
        return when { x < tx -> GbaKeys.RIGHT; x > tx -> GbaKeys.LEFT; y < ty -> GbaKeys.DOWN; else -> GbaKeys.UP }
    }

    private fun idle(): Boolean {
        val w=u16(profile.size); val h=u16(profile.size+2)
        if(w !in 1..64 || h !in 1..64 || u16(profile.cursor) >= w || u16(profile.cursor+2) >= h) return false
        if(u8(profile.chapter+1) != 0 || u8(profile.bm+1) != 0 || u8(profile.bm+2) != 0) return false
        // Only the free map-cursor repeat. All movement/menu/forecast/event
        // states use another PC or block this process.
        for(i in 0..63) {
            val p=profile.procs+i*108
            if(u32(p)==profile.playerScript) return u32(p+4)==profile.idlePc && u8(p+0x28)==0
        }
        return false
    }

    private fun validUnit(id:Int,a:Long):Boolean {
        val w=u16(profile.size); val h=u16(profile.size+2)
        val x=u8(a+profile.unitPosition); val y=u8(a+profile.unitPosition+1)
        val state=if(profile.unitStateBytes==2) u16(a+12).toLong() else u32(a+12)
        if(u32(a) !in 0x08000000L..0x09FFFFFFL || u32(a+4) !in 0x08000000L..0x09FFFFFFL || u8(a+11)!=id || u8(a+profile.unitHp)==0 || state and profile.unavailableMask != 0L || x>=w || y>=h) return false
        if(id>=128 && state and 0x200L != 0L) return false // hidden by fog
        val rows=u32(profile.size+4)
        if(rows !in 0x02000000L..0x0203FF00L) return false
        val row=u32(rows+y*4)
        return row>=0x02000000L && row+w<=0x02040000L && u8(row+x)==id
    }

    companion object {
        fun unitAddress(id:Int):Long? = when(id) {
            in 1..62 -> 33733968L+(id-1)*72
            in 65..84 -> 33742032L+(id-65)*72
            in 129..178 -> 33738432L+(id-129)*72
            else -> null
        }
    }
}
