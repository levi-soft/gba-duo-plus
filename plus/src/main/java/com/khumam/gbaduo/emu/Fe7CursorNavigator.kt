package com.khumam.gbaduo.emu

/** Verified FE map profiles. Cursor movement uses D-pad pulses, never RAM writes. */
internal class Fe7CursorNavigator(
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
        val tx = u8(address+16); val ty = u8(address+17)
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
        val x=u8(a+16); val y=u8(a+17); val state=u32(a+12)
        if(u32(a) !in 0x08000000L..0x09FFFFFFL || u32(a+4) !in 0x08000000L..0x09FFFFFFL || u8(a+11)!=id || u8(a+19)==0 || state and 0x1000CL != 0L || x>=w || y>=h) return false
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

internal data class CursorProfile(
    val cursor:Long,val size:Long,val chapter:Long,val turn:Long,val bm:Long,
    val procs:Long,val playerScript:Long,val idlePc:Long,
    val blue:Long,val green:Long,val red:Long,
) {
    fun unitAddress(id:Int):Long? = when(id) {
        in 1..62 -> blue+(id-1)*72
        in 65..84 -> green+(id-65)*72
        in 129..178 -> red+(id-129)*72
        else -> null
    }
    companion object {
        val FE7=CursorProfile(0x0202BBCC,0x0202E3D8,0x0202BC06,0x0202BC08,0x0202BBB8,
            0x02024E28,0x08B93374,0x08B933E4,33733968,33742032,33738432)
        val FE8=CursorProfile(0x0202BCC4,0x0202E4D4,0x0202BCFE,0x0202BD00,0x0202BCB0,
            0x02024E68,0x087ABDB8,0x087ABE38,0x0202BE4C,0x0202DDCC,0x0202CFBC)
        val FE8_US=FE8.copy(playerScript=0x0859AAD8,idlePc=0x0859AB58)
        fun forRom(code:String,crc:String):CursorProfile? = when {
            code=="AE7E" && crc=="2A524221" -> FE7
            // Vietnamese asset/gameplay mod: cursor processes and unit slots
            // match AE7E byte-for-byte; companion metadata remains separate.
            code=="AE7E" && crc=="E15C3A39" -> FE7
            code=="AE7E" && crc=="32CD990D" -> FE7 // Vietnamese ROM: map cursor process matches the US ROM.
            code=="BE8P" && crc=="B3005195" -> FE8
            code=="BE8E" && crc=="A47246AE" -> FE8_US
            code=="BE8E" && crc=="3CD932D7" -> FE8_US // inspected Vietnamese patch
            code=="BE8E" && crc=="BD53D5F3" -> FE8_US // FE6 mod: same cursor process and deployment table
            else -> null
        }
    }
}
