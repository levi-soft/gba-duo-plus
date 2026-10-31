package com.khumam.gbaduo.companion

import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Installs a pack zip: extract to a temp dir, validate manifest+layout+id,
 * then atomically rename into place. On any failure the temp dir is removed
 * and whatever was already installed under that id (if anything) is untouched.
 */
object PackInstaller {

    class InstallException(message: String) : Exception(message)

    internal data class ExtractionLimits(
        val maxTotalBytes: Long = 256L * 1024 * 1024,
        val maxEntryBytes: Long = 64L * 1024 * 1024,
        val maxEntries: Int = 8192,
    ) {
        init { require(maxTotalBytes > 0 && maxEntryBytes > 0 && maxEntries > 0) }
    }

    private val limits = ExtractionLimits()

    fun install(zipFile: File, packsDir: File): PackManifest = install(zipFile, packsDir, limits)

    internal fun install(zipFile: File, packsDir: File, limits: ExtractionLimits): PackManifest {
        packsDir.mkdirs()
        val tempDir = File(packsDir, ".tmp-${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            extractZip(zipFile, tempDir, limits)

            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) throw InstallException("Pack is missing manifest.json")
            val manifest = try {
                PackManifest.parse(manifestFile.readText())
            } catch (e: PackManifest.InvalidManifestException) {
                throw InstallException(e.message ?: "Invalid manifest.json")
            }
            if (!PackManifest.isValidId(manifest.id)) {
                throw InstallException("Invalid pack id '${manifest.id}'")
            }
            requirePackFile(tempDir, manifest.layoutFile)
            manifest.scriptFile?.let { requirePackFile(tempDir, it) }
            manifest.styleFiles.forEach { requirePackFile(tempDir, it) }
            manifest.gameScriptFile?.let { requirePackFile(tempDir, it) }

            val finalDir = File(packsDir, manifest.id)
            if (finalDir.exists()) finalDir.deleteRecursively()
            if (!tempDir.renameTo(finalDir)) {
                throw InstallException("Could not install pack '${manifest.id}'")
            }
            return manifest
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw if (e is InstallException) e else InstallException(e.message ?: "Invalid pack")
        }
    }

    private fun requirePackFile(root: File, path: String) {
        val boundary = root.canonicalPath + File.separator
        val file = File(root, path).canonicalFile
        if (!file.path.startsWith(boundary)) throw InstallException("Unsafe pack path: $path")
        if (!file.isFile) throw InstallException("Pack is missing $path")
    }

    private fun extractZip(zipFile: File, destDir: File, limits: ExtractionLimits) {
        val destRoot = destDir.canonicalPath
        var count = 0
        var total = 0L
        val buffer = ByteArray(16 * 1024)
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (count >= limits.maxEntries) throw InstallException("Pack has too many entries")
                count++
                val outFile = File(destDir, entry.name)
                val resolved = outFile.canonicalPath
                if (resolved != destRoot && !resolved.startsWith(destRoot + File.separator)) {
                    throw InstallException("Unsafe zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    // closeEntry() would otherwise drain uncounted data hidden in a directory entry.
                    if (zis.read() != -1) throw InstallException("Pack directory entry contains data")
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out ->
                        var bytes = 0L
                        while (true) {
                            val read = zis.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            // Count actual streamed bytes, not optional or attacker-supplied ZIP sizes.
                            if (read.toLong() > limits.maxEntryBytes - bytes) throw InstallException("Pack entry is too large")
                            if (read.toLong() > limits.maxTotalBytes - total) throw InstallException("Pack total unpacked size is too large")
                            out.write(buffer, 0, read)
                            bytes += read
                            total += read
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
