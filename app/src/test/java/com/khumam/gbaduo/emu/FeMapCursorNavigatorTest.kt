package com.khumam.gbaduo.emu

import org.junit.Assert.*
import org.junit.Test

class FeMapCursorNavigatorTest {
    private class MapState(val profile:CursorProfile=CursorProfile.FE7) {
        val bytes=ByteArray(0x40000)
        fun put(a:Long,n:Long,size:Int){repeat(size){bytes[(a-0x02000000).toInt()+it]=(n shr (it*8)).toByte()}}
        fun read(a:Long,size:Int):Long=(0 until size).fold(0L){v,i->v or ((bytes[(a-0x02000000).toInt()+i].toLong() and 255) shl (i*8))}
        val nav=FeMapCursorNavigator({read(it,1).toInt()},{read(it,2).toInt()},{read(it,4)},profile)
        init {
            put(profile.size,15,2);put(profile.size+2,10,2)
            put(profile.procs,profile.playerScript,4);put(profile.procs+4,profile.idlePc,4)
            put(profile.size+4,0x02030000,4)
            repeat(10){put(0x02030000L+it*4,0x02030100L+it*16,4)}
        }
        fun unit(id:Int,x:Int=5,y:Int=3){
            val a=profile.unitAddress(id)!!
            put(a,0x08001000,4);put(a+4,0x08002000,4);put(a+11,id.toLong(),1);put(a+profile.unitHp,20,1)
            put(a+profile.unitPosition,x.toLong(),1);put(a+profile.unitPosition+1,y.toLong(),1);put(0x02030100L+y*16+x,id.toLong(),1)
        }
    }

    @Test fun movesToEveryFactionUsingOnlyDirectionsAndReleasesOnArrival(){
        val relocated = CursorProfile.FE6_EMBER.copy(playerScript=0x08800000,idlePc=0x08800070)
        for(profile in listOf(CursorProfile.FE7,CursorProfile.FE8,CursorProfile.FE8_US,CursorProfile.FE6_TEN,CursorProfile.FE6_EMBER,relocated))for(id in listOf(1,65,129)){
            val m=MapState(profile);m.unit(id);val before=m.bytes.copyOf()
            assertTrue(m.nav.focus(id,true));assertArrayEquals(before,m.bytes)
            var x=0;var y=0
            repeat(60){when(m.nav.keys(0)){
                GbaKeys.RIGHT->x++;GbaKeys.LEFT->x--;GbaKeys.DOWN->y++;GbaKeys.UP->y--
            };m.put(profile.cursor,x.toLong(),2);m.put(profile.cursor+2,y.toLong(),2)}
            assertEquals(5,x);assertEquals(3,y);assertEquals(0,m.nav.keys(0))
        }
    }
    @Test fun fe8RejectsMenusAndCancelsForPlayerInputOrTurnChanges(){
        val m=MapState(CursorProfile.FE8);m.unit(2)
        assertTrue(m.nav.focus(2,true))
        assertEquals(GbaKeys.LEFT,m.nav.keys(GbaKeys.LEFT));assertEquals(0,m.nav.activeTarget)
        m.put(m.profile.procs+4,0x087ABE80,4);assertFalse(m.nav.focus(2,true))
        m.put(m.profile.procs+4,m.profile.idlePc,4);assertTrue(m.nav.focus(2,true))
        m.put(m.profile.turn,2,2);assertEquals(0,m.nav.keys(0));assertEquals(0,m.nav.activeTarget)
        assertEquals(CursorProfile.FE8,CursorProfile.forRom("BE8P","B3005195"))
        assertNull(CursorProfile.forRom("BE8E","B3005195"))
        assertEquals(CursorProfile.FE8_US,CursorProfile.forRom("BE8E","A47246AE"))
        assertEquals(CursorProfile.FE8_US,CursorProfile.forRom("BE8E","3CD932D7"))
        assertEquals(CursorProfile.FE8_US,CursorProfile.forRom("BE8E","BD53D5F3"))
        assertNull(CursorProfile.forRom("BE8P","00000000"))
        assertEquals(CursorProfile.FE7,CursorProfile.forRom("AE7E","32CD990D"))
        assertNull(CursorProfile.forRom("BE8E","32CD990D"))
    }
    @Test fun rejectsUnsupportedRomBusyStatesHiddenUnitsAndInvalidIds(){
        val m=MapState();m.unit(129)
        assertFalse(m.nav.focus(129,false));assertFalse(m.nav.focus(63,true))
        m.put(0x02024E2C,0x08B933EC,4);assertFalse(m.nav.focus(129,true))
        m.put(0x02024E2C,0x08B933E4,4);m.put(0x02024E50,1,1);assertFalse(m.nav.focus(129,true))
        m.put(0x02024E50,0,1);m.put(FeMapCursorNavigator.unitAddress(129)!!+12,0x200,4);assertFalse(m.nav.focus(129,true))
    }
    @Test fun manualInputAndPhaseChangesCancelWithoutConfirmingAnything(){
        val m=MapState();m.unit(1);assertTrue(m.nav.focus(1,true))
        assertEquals(GbaKeys.A,m.nav.keys(GbaKeys.A));assertEquals(0,m.nav.keys(0))
        assertTrue(m.nav.focus(1,true));m.put(0x0202BC07,0x80,1);assertEquals(0,m.nav.keys(0))
        m.put(0x0202BC07,0,1);assertEquals(0,m.nav.keys(0))
    }
    @Test fun fe6AcceptsOnlyTheExactPatchAndReadsNativeStateCoordinatesAndHp(){
        val profile=CursorProfile.FE6_TEN
        assertEquals(profile,CursorProfile.forRom("AFEJ","197DE10B"))
        assertNull(CursorProfile.forRom("AFEJ","BD53D5F3"))
        assertNull(CursorProfile.forRom("BE8E","197DE10B"))
        assertEquals(CursorProfile.FE6_EMBER,CursorProfile.forRom("AFEJ","636F7B05"))
        assertEquals(CursorProfile.FE6_EMBER,CursorProfile.forRom("AFEJ","29BC2257"))
        assertNull(CursorProfile.forRom("BE8E","636F7B05"))
        assertNull(CursorProfile.forRom("AFEJ","00000000"))
        val m=MapState(profile);m.unit(129,5,3)
        val a=profile.unitAddress(129)!!
        // The high half of a u32 at +12 contains the nonzero x/y coordinates.
        // It must never be interpreted as a unit-state field in FE6.
        assertTrue(m.read(a+12,4) and 0x10000L != 0L)
        assertTrue(m.nav.focus(129,true))
        assertEquals(GbaKeys.RIGHT,m.nav.keys(0))
        val before=m.bytes.copyOf()
        for(state in listOf(4L,8L,0x20L,0x200L)){
            m.put(a+12,state,2);assertFalse(m.nav.focus(129,true))
        }
        m.put(a+12,0,2);m.put(a+17,0,1);assertFalse(m.nav.focus(129,true))
        before.copyInto(m.bytes);assertTrue(m.nav.focus(129,true))
        m.put(profile.procs+4,profile.idlePc+8,4);assertEquals(0,m.nav.keys(0))
        assertEquals(0,m.nav.activeTarget)
    }
}
