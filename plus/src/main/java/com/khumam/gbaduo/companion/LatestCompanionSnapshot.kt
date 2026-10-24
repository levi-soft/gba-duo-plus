package com.khumam.gbaduo.companion

/** One pending frame, shared by the emu producer and UI consumer. Preserve one-shot UI sets. */
internal class LatestCompanionSnapshot {
    private var latest: Map<String, Any?>? = null
    @Synchronized fun offer(next: Map<String, Any?>) {
        val before = latest?.get("ui") as? Map<*, *>
        val after = next["ui"] as? Map<*, *>
        val oldSet = before?.get("set") as? Map<*, *>
        val newSet = after?.get("set") as? Map<*, *>
        latest = if (before != null && (after == null || oldSet != null)) {
            val merged = LinkedHashMap<String, Any?>()
            before.forEach { (key,value) -> if(key is String) merged[key]=value }
            after?.forEach { (key,value) -> if(key is String) merged[key]=value }
            if(oldSet != null) merged["set"] = LinkedHashMap<Any?, Any?>(oldSet).apply { if(newSet!=null) putAll(newSet) }
            next + ("ui" to merged)
        } else next
    }
    @Synchronized fun take(): Map<String, Any?>? = latest.also { latest = null }
}
