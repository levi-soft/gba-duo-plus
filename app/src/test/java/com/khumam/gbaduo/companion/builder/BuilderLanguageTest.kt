package com.khumam.gbaduo.companion.builder

import com.khumam.gbaduo.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "vi")
class BuilderLanguageTest {
    @Test fun builderMenuMatchesEnglishPlusEvenOnVietnameseAndroid() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("Find and create companion", context.getString(R.string.plus_builder_create))
        assertEquals("Automatic companion · Plus", context.getString(R.string.plus_builder_title))
        assertEquals("Cancel", context.getString(R.string.plus_builder_cancel))
    }

    @Test fun packManagementRemainsEnglishOnVietnameseAndroid() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("Manage installed packs…", context.getString(R.string.plus_packs_manage))
        assertEquals("Delete pack", context.getString(R.string.plus_packs_delete))
        assertEquals("Cancel", context.getString(R.string.plus_packs_cancel))
        assertEquals("No companion packs installed for this game", context.getString(R.string.plus_packs_empty))
        assertEquals("Companion pack deleted", context.getString(R.string.plus_packs_deleted))
        assertEquals("Delete FE7 (v1.0.0)?\n\nThis removes the installed pack and unassigns it from every game using it. Your ROMs, saves and original ZIP files are kept.",
            context.getString(R.string.plus_packs_delete_confirm, "FE7", "1.0.0"))
    }
}
