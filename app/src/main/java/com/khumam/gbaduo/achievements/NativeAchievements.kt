package com.khumam.gbaduo.achievements

/** Emulation-thread-only rcheevos bridge. Auth-bearing results never reach Companion snapshots. */
object NativeAchievements {
    external fun start()
    external fun login(username: String, secret: String, token: Boolean)
    external fun logout()
    external fun frame(paused: Boolean)
    external fun stop()
    external fun pollRequest(): ByteArray?
    external fun response(id: Long, body: ByteArray, status: Int)
    external fun pollEvent(): ByteArray?
    external fun status(): ByteArray
    external fun achievementList(): ByteArray
    external fun saveProgress(): ByteArray?
    external fun loadProgress(data: ByteArray?)
}
