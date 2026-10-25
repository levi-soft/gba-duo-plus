package com.khumam.gbaduo.emu

data class CursorProfile(
    val cursor:Long,val size:Long,val chapter:Long,val turn:Long,val bm:Long,
    val procs:Long,val playerScript:Long,val idlePc:Long,
    val blue:Long,val green:Long,val red:Long,
    val unitPosition:Int=16,val unitHp:Int=19,val unitStateBytes:Int=4,
    val unavailableMask:Long=0x1000CL,
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
        // FE6 uses the native 16-bit state and x/y at +14/+15. Reading its
        // state as u32 would incorrectly treat coordinates as availability bits.
        val FE6_TEN=CursorProfile(0x0202AA1C,0x0202D200,0x0202AA56,0x0202AA58,0x0202AA08,
            0x02023CC4,0x085C56A8,0x085C5718,0x0202AB78,0x0202CAF8,0x0202BCE8,
            unitPosition=14,unitHp=17,unitStateBytes=2,unavailableMask=0x2C)
        // Both inspected Ember variants relocate the player-phase script while
        // retaining the native FE6 unit banks and free-cursor repeat command.
        val FE6_EMBER=FE6_TEN.copy(playerScript=0x087FF4B0,idlePc=0x087FF520)
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
            code=="AFEJ" && crc=="197DE10B" -> FE6_TEN
            code=="AFEJ" && (crc=="636F7B05" || crc=="29BC2257") -> FE6_EMBER
            else -> null
        }
    }
}
