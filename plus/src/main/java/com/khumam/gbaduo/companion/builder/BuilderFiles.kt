package com.khumam.gbaduo.companion.builder

import java.io.File
import java.io.Closeable
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream

class BuildCancellation {
    @Volatile var cancelled: Boolean = false; private set
    @Volatile var committed: Boolean = false; private set
    private val terminal = Any()
    private val cancellationListeners = mutableSetOf<() -> Unit>()
    /** False means installation already committed, or cancellation already happened. */
    fun cancel(): Boolean {
        val listeners = synchronized(terminal) {
            if (committed || cancelled) return false
            cancelled = true
            cancellationListeners.toList().also { cancellationListeners.clear() }
        }
        listeners.forEach { runCatching { it() } }
        return true
    }
    internal fun onCancel(listener: () -> Unit): Closeable {
        val alreadyCancelled = synchronized(terminal) {
            if (!cancelled && !committed) cancellationListeners += listener
            cancelled
        }
        if (alreadyCancelled) listener()
        return Closeable { synchronized(terminal) { cancellationListeners -= listener } }
    }
    fun check() { if (cancelled || Thread.currentThread().isInterrupted) throw CancellationException("Cancelled") }
    fun commit(action: () -> Unit) = synchronized(terminal) {
        check(); action(); committed = true; cancellationListeners.clear()
    }
}

object BuilderFiles {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun readBounded(input: InputStream, limit: Long, token: BuildCancellation): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(32768); var total = 0L
        while (true) { token.check(); val n = input.read(buffer); if (n < 0) break; total += n; require(total <= limit) { "Download exceeds size limit" }; out.write(buffer, 0, n) }
        return out.toByteArray()
    }
    fun atomicWrite(file: File, bytes: ByteArray, writeTemporary: ((File, ByteArray) -> Unit)? = null) {
        file.parentFile?.mkdirs(); val temp = File(file.parentFile, ".${file.name}-${java.util.UUID.randomUUID()}")
        try {
            if (writeTemporary == null) temp.outputStream().use { output -> output.write(bytes); output.fd.sync() }
            else writeTemporary(temp, bytes)
            // Both paths are siblings. Fail closed if this filesystem cannot atomically
            // promote the finished file; never truncate or delete the previous target.
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
    fun safeFile(root: File, relative: String): File {
        require(relative.isNotEmpty() && !relative.startsWith('/') && !relative.contains('\\') && !relative.contains('\u0000') && !relative.contains(':')) { "Unsafe file path" }
        require(relative.split('/').none { it == ".." || it == "." }) { "Unsafe file path" }
        val file = File(root, relative).canonicalFile
        require(file.path.startsWith(root.canonicalPath + File.separator)) { "File escapes builder directory" }
        return file
    }
    fun extract(zip: File, root: File, limit: Long, maxEntries: Int, token: BuildCancellation): Long {
        root.mkdirs(); val seen = mutableSetOf<String>(); var total = 0L; var entries = 0
        ZipInputStream(zip.inputStream()).use { stream ->
            while (true) {
                token.check(); val entry = stream.nextEntry ?: break
                require(++entries <= maxEntries) { "Too many bundle files" }
                val path = entry.name.trimEnd('/'); val file = safeFile(root, path)
                require(seen.add(file.path)) { "Duplicate bundle entry" }
                if (entry.isDirectory) file.mkdirs() else {
                    file.parentFile?.mkdirs(); file.outputStream().use { out ->
                        val buffer = ByteArray(32768)
                        while (true) { token.check(); val n = stream.read(buffer); if (n < 0) break; total += n; require(total <= limit) { "Unpacked bundle exceeds size limit" }; out.write(buffer, 0, n) }
                    }
                }
                stream.closeEntry()
            }
        }
        return total
    }
}
