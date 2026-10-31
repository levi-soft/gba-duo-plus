package com.khumam.gbaduo.companion

import org.junit.Assert.*
import org.junit.Test

class RetainedScriptSnapshotTest {
    @Test fun failedTickKeepsCurrentWideControlsWithoutReplayingNavigation() {
        val retained = RetainedScriptSnapshot()
        retained.accept(mapOf("hp" to 20, "_wide" to 1, "_wideLabel" to "ON",
            "ui" to mapOf("set" to mapOf("mainTab" to "Map"))))
        val failed = retained.accept(mapOf("_scriptError" to "timeout", "_wide" to 0, "_wideLabel" to "OFF"))
        assertEquals(20, failed["hp"])
        assertEquals(0, failed["_wide"])
        assertEquals("OFF", failed["_wideLabel"])
        assertFalse(failed.containsKey("ui"))
        val unavailable = retained.accept(mapOf("_scriptError" to "timeout", "_wide" to 0, "_wideLabel" to "N/A"))
        assertEquals("N/A", unavailable["_wideLabel"])
        assertEquals(20, unavailable["hp"])
    }

    @Test fun failedTickRetainsTheWholeViewAndExposesCurrentErrorAndOriginalFrame() {
        val retained = RetainedScriptSnapshot()
        val good = mapOf("_frameCounter" to 10L, "hp" to 20, "mapCells" to listOf(mapOf("tile" to "grass")))
        assertEquals(good, retained.accept(good))
        val failed = retained.accept(mapOf("_frameCounter" to 22L, "_scriptError" to "timeout", "hp" to 0))
        assertEquals(20, failed["hp"]); assertEquals(good["mapCells"], failed["mapCells"])
        assertEquals(22L, failed["_frameCounter"]); assertEquals(10L, failed["_scriptDataFrame"])
        assertEquals(true, failed["_scriptStale"]); assertEquals("timeout", failed["_scriptError"])
        assertEquals(10L, retained.accept(mapOf("_frameCounter" to 34L, "_scriptError" to "another error"))["_scriptDataFrame"])
    }

    @Test fun recoveryAndConfirmedEmptySelectionReplaceTheRetainedView() {
        val retained = RetainedScriptSnapshot()
        retained.accept(mapOf("unitPresent" to true, "hp" to 20))
        retained.accept(mapOf("_scriptError" to "timeout"))
        val empty = mapOf("unitPresent" to false, "hp" to "---")
        assertEquals(empty, retained.accept(empty))
        val failed = retained.accept(mapOf("_scriptError" to "timeout"))
        assertEquals(false, failed["unitPresent"]); assertEquals("---", failed["hp"])
        assertFalse(retained.accept(mapOf("hp" to 19)).containsKey("_scriptStale"))
    }

    @Test fun navigationIsNotReplayedAndResetNeverLeaksAnotherPackOrSaveState() {
        val retained = RetainedScriptSnapshot()
        val initial = mapOf("hp" to 20, "ui" to mapOf("set" to mapOf("mainTab" to "Map")))
        assertEquals(initial, retained.accept(initial))
        assertFalse(retained.accept(mapOf("_scriptError" to "timeout")).containsKey("ui"))
        retained.clear()
        val failed = retained.accept(mapOf("_scriptError" to "new pack error"))
        assertFalse(failed.containsKey("hp")); assertEquals("new pack error", failed["_scriptError"])
        assertEquals(true, failed["_scriptStale"])
    }
}
