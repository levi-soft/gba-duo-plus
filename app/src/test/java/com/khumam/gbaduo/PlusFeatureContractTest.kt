package com.khumam.gbaduo

import com.khumam.gbaduo.achievements.RaNative
import org.junit.Assert.*
import org.junit.Test

class PlusFeatureContractTest {
    @Test fun plusHasSeparateIdentityAndRetainsAchievementsWithoutHashSetter() {
        assertEquals("com.khumam.gbaduo.plus", BuildConfig.APPLICATION_ID)
        val methods = RaNative::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue(methods.containsAll(listOf("init", "loginWithPassword", "doFrame", "achievementList")))
        assertFalse(methods.contains("setHashOverride"))
    }

    @Test fun translationAndHashOverrideImplementationsAreAbsent() {
        for (name in listOf(
            "achievements.AchievementHashOverrides", "player.TranslationMenu",
            "companion.TranslationBackend", "companion.GoogleWebTranslation",
            "companion.GroqTranslation", "companion.TranslationCredentials"
        )) {
            assertThrows(ClassNotFoundException::class.java) {
                Class.forName("com.khumam.gbaduo.$name")
            }
        }
    }
}
