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
}
