-keep class com.khumam.gbaduo.emu.NativeCore { *; }
-keep class com.khumam.gbaduo.debug.StudioMemory { *; }
# Plus local builder runner calls these annotated methods from its pinned page.
-keepclassmembers class com.khumam.gbaduo.companion.builder.** {
    @android.webkit.JavascriptInterface <methods>;
}

# achievements.c finds this class and its @JvmStatic callbacks (serverCall, onLogin,
# onGameLoad, onEvent) by name over JNI.
-keep class com.khumam.gbaduo.achievements.RetroAchievements {
    public static <methods>;
}
