package com.khumam.gbaduo.companion.builder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.khumam.gbaduo.library.Platform
import com.khumam.gbaduo.library.RomEntry
import com.khumam.gbaduo.storage.GameStorage
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Local generation behind the official web catalog, using its single result path. */
object WebCompanionCreation {
    const val EXTRA_PACK_ID = "pack_id"
    private const val PREFIX = "web.builder.source."
    private const val EXTRA_OPERATION = "web.builder.operation"
    private val sourceKeys = listOf("uri", "fileName", "title", "code", "platform", "crc", "size", "modified")
    private val operations = ConcurrentHashMap<String, Operation>()
    private val packIdPattern = Regex("^[a-z0-9-]{1,64}$")

    data class Source(val entry: RomEntry, val crc32: Long) {
        init { require(valid(entry, crc32)) { "Invalid local GBA source" } }
    }

    /** Invalid sources leave a normal catalog intent; stale source extras are removed. */
    fun attachSource(intent: Intent, entry: RomEntry, crc32: Long): Intent {
        sourceKeys.forEach { intent.removeExtra(PREFIX + it) }
        if (!valid(entry, crc32)) return intent
        return intent.putExtra(PREFIX + "uri", entry.uri.toString())
            .putExtra(PREFIX + "fileName", entry.fileName).putExtra(PREFIX + "title", entry.title)
            .putExtra(PREFIX + "code", entry.gameCode).putExtra(PREFIX + "platform", entry.platform.id)
            .putExtra(PREFIX + "crc", crc32).putExtra(PREFIX + "size", entry.size)
            .putExtra(PREFIX + "modified", entry.lastModified)
    }

    fun source(intent: Intent): Source? = runCatching {
        require(sourceKeys.all { intent.hasExtra(PREFIX + it) })
        val entry = RomEntry(Uri.parse(intent.getStringExtra(PREFIX + "uri") ?: error("Missing URI")),
            intent.getStringExtra(PREFIX + "fileName") ?: error("Missing filename"),
            intent.getStringExtra(PREFIX + "title") ?: error("Missing title"),
            intent.getStringExtra(PREFIX + "code") ?: error("Missing game code"),
            Platform.byId(intent.getStringExtra(PREFIX + "platform")) ?: error("Missing platform"),
            intent.getLongExtra(PREFIX + "size", -1), intent.getLongExtra(PREFIX + "modified", -1))
        Source(entry, intent.getLongExtra(PREFIX + "crc", -1))
    }.getOrNull()

    fun find(context: Context, source: Source, cancellation: BuildCancellation): BuilderProfile? = find(
        source, BuilderDistribution(File(context.cacheDir, "companion-builder-distribution")),
        URI(AutomaticCompanionActivity.DEFAULT_CATALOG_URL), cancellation)

    internal fun find(source: Source, distribution: BuilderDistribution, uri: URI, cancellation: BuildCancellation): BuilderProfile? {
        cancellation.check()
        val profile = distribution.catalog(uri, cancellation).profile(source.entry.gameCode, source.crc32)
        cancellation.check(); return profile
    }

    fun resultIntent(packId: String, source: Source): Intent {
        require(packIdPattern.matches(packId)) { "Invalid installed companion ID" }
        return attachSource(Intent(), source.entry, source.crc32).putExtra(EXTRA_PACK_ID, packId)
    }

    fun createdPackId(result: Intent?, expected: Source): String? {
        if (result == null || source(result) != expected) return null
        return runCatching { result.getStringExtra(EXTRA_PACK_ID)?.takeIf(packIdPattern::matches) }.getOrNull()
    }

    fun launch(activity: Activity, source: Source, requestCode: Int,
        cancellation: BuildCancellation = BuildCancellation(), onAborted: (String?) -> Unit = {}) {
        val entry = source.entry
        val storage = GameStorage.forGame(activity.filesDir, entry.platform, entry.title, entry.gameCode, source.crc32)
        val previous = storage.packId
        AutomaticCompanionEntry.prepare(activity, entry, storage, cancellation, onAborted) { rom, romName ->
            cancellation.check()
            require(storage.packId == previous) { "Companion selection changed while preparing the ROM" }
            val key = UUID.randomUUID().toString()
            operations[key] = Operation(source, previous, cancellation, romName)
            cancellation.onCancel { operations.remove(key) }
            val request = attachSource(AutomaticCompanionActivity.intent(activity, rom, entry.title, entry.gameCode, source.crc32),
                entry, source.crc32).putExtra(EXTRA_OPERATION, key)
            try {
                cancellation.check(); activity.startActivityForResult(request, requestCode)
            } catch (error: Exception) { operations.remove(key); throw error }
        }
    }

    internal data class Operation(val source: Source, val previousAssignment: String?, val cancellation: BuildCancellation, val romFileName: String)
    internal fun isResultRequest(intent: Intent): Boolean = intent.hasExtra(EXTRA_OPERATION)

    /** Process-restored requests cannot bypass the live parent's cancellation boundary. */
    internal fun takeOperation(intent: Intent): Operation {
        val key = intent.getStringExtra(EXTRA_OPERATION) ?: error("Missing creation request")
        val operation = operations.remove(key) ?: error("Creation request expired; return to the catalog and try again")
        require(source(intent) == operation.source) { "Creation source changed" }
        operation.cancellation.check()
        return operation
    }

    private fun valid(entry: RomEntry, crc32: Long): Boolean = runCatching {
        require(entry.platform == Platform.GBA && crc32 in 0..0xffffffffL)
        require(entry.gameCode.matches(Regex("^[A-Z0-9]{4}$")))
        require(entry.fileName.isNotBlank() && entry.fileName.length <= 1024 && entry.fileName.none { it.isISOControl() })
        require(entry.fileName.endsWith(".gba", true) || entry.fileName.endsWith(".zip", true))
        require(entry.title.isNotBlank() && entry.title.length <= 1024 && entry.title.none { it.isISOControl() })
        require(entry.size >= 0 && entry.lastModified >= 0)
        val uri = entry.uri ?: error("Missing ROM URI")
        require(uri.toString().length <= 8192)
        val parsed = URI(uri.toString())
        require(parsed.isAbsolute && !parsed.isOpaque && parsed.rawQuery == null && parsed.rawFragment == null && parsed.userInfo == null)
        require(!parsed.path.isNullOrBlank() && parsed.path.startsWith('/'))
        when (parsed.scheme) {
            "content" -> require(!parsed.rawAuthority.isNullOrBlank())
            "file" -> require(parsed.rawAuthority.isNullOrEmpty())
            else -> error("ROM URI must be local")
        }
        true
    }.getOrDefault(false)
}
