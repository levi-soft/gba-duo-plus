package com.khumam.gbaduo.debug

/** Read-only GBA ROM/graphics inspection; called only on the emulator thread. */
internal object StudioMemory {
    init { System.loadLibrary("gbaduo") }
    external fun romSize(): Long
    external fun read(address: Long, length: Int): ByteArray
}
