package com.khumam.gbaduo.artwork

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.khumam.gbaduo.library.RomEntry
import com.khumam.gbaduo.library.RomLibrary
import com.khumam.gbaduo.library.ui.CartridgeView
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Resolves only visible entries on one bounded worker; carousel draw callbacks only see bitmaps. */
class ArtworkBinder(context: Context, private val library: RomLibrary) {
    private val repository = ArtworkRepository(context)
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(12), ThreadPoolExecutor.DiscardOldestPolicy())
    private val bindings = WeakHashMap<CartridgeView, Binding>()
    private val identities = HashMap<String, ArtworkIdentity>() // Worker-only; RomLibrary persists hash results.
    private val bitmaps = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val generation = AtomicLong()
    @Volatile private var closed = false

    private class Binding(val entry: RomEntry, val generation: Long) {
        @Volatile var active = true
    }

    fun bind(view: CartridgeView, entry: RomEntry) {
        bindings.remove(view)?.active = false
        view.artworkRenderer = null
        view.invalidate()
        if (closed) return
        val binding = Binding(entry, generation.get())
        bindings[view] = binding
        view.tag = binding // The host clears this when its RecyclerView recycles a card.
        val target = WeakReference(view)
        worker.execute {
            if (!current(binding)) return@execute
            val bitmap = runCatching {
                if (!repository.hasAnyArtwork()) return@runCatching null
                val uri = entry.uri?.toString() ?: return@runCatching null
                val source = uri + ":" + entry.size + ":" + entry.lastModified
                val identity = identities[source] ?: library.crc32(entry)?.let { ArtworkIdentity(entry.platform.id, it) }?.also { identities[source] = it }
                    ?: return@runCatching null
                if (!current(binding)) return@runCatching null
                val cacheKey = binding.generation.toString() + ":" + identity.key
                bitmaps.get(cacheKey) ?: repository.loadBitmap(identity)?.also { if (current(binding)) bitmaps.put(cacheKey, it) }
            }.getOrNull()
            if (bitmap == null || !current(binding)) return@execute
            main.post {
                val destination = target.get()
                if (destination != null && current(binding) && bindings[destination] === binding && destination.tag === binding) {
                    destination.artworkRenderer = { canvas, bounds, selected ->
                        ArtworkRenderer.draw(canvas, bounds, bitmap, selected, destination.resources.displayMetrics.density)
                    }
                    destination.invalidate()
                }
            }
        }
    }

    fun refresh() {
        if (closed) return
        generation.incrementAndGet()
        bitmaps.evictAll()
        val visible = bindings.entries.filter { it.key.tag === it.value }.map { it.key to it.value.entry }
        visible.forEach { (view, entry) -> bind(view, entry) }
    }

    private fun current(binding: Binding) = !closed && binding.active && binding.generation == generation.get()

    fun close() {
        closed = true
        generation.incrementAndGet()
        worker.shutdownNow()
        bindings.forEach { (view, binding) -> binding.active = false; if (view.tag === binding) view.tag = null; view.artworkRenderer = null; view.invalidate() }
        bindings.clear()
        bitmaps.evictAll()
    }
}
