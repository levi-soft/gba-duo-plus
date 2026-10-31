package com.khumam.gbaduo.emu

import com.khumam.gbaduo.emu.EmuThread.WriteAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class GameScriptAccessTest {

    @Test
    fun consent_gates_frame_writes() {
        assertTrue(GameScriptAccess.mayWrite(WriteAccess.ALLOWED) { true })
        assertFalse(GameScriptAccess.mayWrite(WriteAccess.ASK) { true })
        assertFalse(GameScriptAccess.mayWrite(WriteAccess.NONE) { true })
    }

    @Test
    fun safetyStateFailureRefusesWritesAndReadOnlyCallsNeverAttemptSaving() {
        var saves = 0
        val failedSave = { saves++; false }
        assertFalse(GameScriptAccess.mayWrite(WriteAccess.NONE, failedSave))
        assertFalse(GameScriptAccess.mayWrite(WriteAccess.ASK, failedSave))
        assertEquals(0, saves)
        assertFalse(GameScriptAccess.mayWrite(WriteAccess.ALLOWED, failedSave))
        assertEquals(1, saves)
        assertTrue(GameScriptAccess.mayWrite(WriteAccess.ALLOWED) { saves++; true })
        assertEquals(2, saves)
    }

    @Test
    fun asksOnceWhenAFrameHookWaitsOnAnUndecidedPack() {
        assertTrue(GameScriptAccess.needsConsent(WriteAccess.ASK, hasFrameHook = true, alreadyAsked = false))
        assertFalse(GameScriptAccess.needsConsent(WriteAccess.ASK, hasFrameHook = true, alreadyAsked = true))
    }

    @Test
    fun neverAsksWhenNothingCouldWrite() {
        assertFalse(GameScriptAccess.needsConsent(WriteAccess.ASK, hasFrameHook = false, alreadyAsked = false))
        assertFalse(GameScriptAccess.needsConsent(WriteAccess.NONE, hasFrameHook = true, alreadyAsked = false))
        assertFalse(GameScriptAccess.needsConsent(WriteAccess.ALLOWED, hasFrameHook = true, alreadyAsked = false))
    }
}
