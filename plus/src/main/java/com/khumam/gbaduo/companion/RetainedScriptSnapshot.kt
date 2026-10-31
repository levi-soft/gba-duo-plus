package com.khumam.gbaduo.companion

/** Keep a complete view across failed Lua ticks; never replay one-shot UI actions. */
internal class RetainedScriptSnapshot {
    private var valid: Map<String, Any?>? = null

    fun clear() { valid = null }

    fun accept(next: Map<String, Any?>): Map<String, Any?> {
        if (next["_scriptError"] == null) {
            valid = next - "ui"
            return next
        }
        val previous = valid ?: return next + ("_scriptStale" to true)
        return previous + next.filterKeys { it == "_wide" || it == "_wideLabel" } + mapOf(
            "_frameCounter" to next["_frameCounter"],
            "_scriptError" to next["_scriptError"],
            "_scriptStale" to true,
            "_scriptDataFrame" to previous["_frameCounter"],
        )
    }
}
