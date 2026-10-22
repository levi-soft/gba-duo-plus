package com.khumam.gbaduo.library

import org.junit.Assert.*
import org.junit.Test

class ExternalRomPathTest {
    @Test fun mapsPrimaryAliasesAndUnicodeFilenames() {
        for (root in listOf("/sdcard", "/storage/emulated/0", "/storage/self/primary")) {
            assertEquals("primary:Roms/gba/Fire Emblem (USA).gba", ExternalRomPath.documentId("$root/Roms/gba/Fire Emblem (USA).gba"))
        }
        assertEquals("primary:Roms/Trò chơi.gbc", ExternalRomPath.documentId("/sdcard/Roms/Trò chơi.gbc"))
    }
    @Test fun mapsSdCardAndNormalizesDotSegments() {
        assertEquals("7FDF-BE45:Roms/game.zip", ExternalRomPath.documentId("/storage/7FDF-BE45/Roms/./sub/../game.zip"))
        assertEquals("7FDF-BE45:Roms/game.gb", ExternalRomPath.documentId("/mnt/media_rw/7FDF-BE45/Roms/game.gb"))
    }
    @Test fun confinesPathsToTheGrantedFolder() {
        assertTrue(ExternalRomPath.withinTree("primary:Roms/gba/game.gba", "primary:Roms/gba"))
        assertFalse(ExternalRomPath.withinTree("primary:Roms/gba-other/game.gba", "primary:Roms/gba"))
        assertFalse(ExternalRomPath.withinTree("7FDF-BE45:Roms/game.gba", "primary:Roms"))
        assertEquals("primary:other.gba", ExternalRomPath.documentId("/sdcard/Roms/../other.gba"))
        assertFalse(ExternalRomPath.withinTree("primary:other.gba", "primary:Roms"))
        assertNull(ExternalRomPath.documentId("relative/game.gba"))
        assertNull(ExternalRomPath.documentId("/../../sdcard/game.gba"))
    }
}
