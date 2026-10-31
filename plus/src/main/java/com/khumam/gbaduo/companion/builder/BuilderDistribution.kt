package com.khumam.gbaduo.companion.builder

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

class ResolvedBuilder(val catalog: BuilderCatalog, val directory: File, val fromCache: Boolean) : Closeable {
    override fun close() { directory.deleteRecursively() }
}

class BuilderDistribution(
    private val cache: File,
    private val fetch: (URI, Long, BuildCancellation) -> ByteArray = ::download,
) {
    /** Discovery deliberately does not download, verify or extract the builder bundle. */
    fun catalog(catalogUri: URI, token: BuildCancellation): BuilderCatalog {
        BuilderCatalog.origin(catalogUri); token.check()
        // A new discovery record must not replace the last verified catalog/bundle pair.
        val metadata = record(catalogUri, "metadata")
        try {
            val bytes = fetch(catalogUri, BuilderCatalog.MAX_CATALOG_BYTES, token)
            require(bytes.size <= BuilderCatalog.MAX_CATALOG_BYTES) { "Catalogue exceeds size limit" }
            val catalog = BuilderCatalog.parse(bytes.toString(Charsets.UTF_8), catalogUri)
            token.check(); BuilderFiles.atomicWrite(metadata, bytes)
            return catalog
        } catch (network: IOException) {
            token.check()
            val cached = listOf(metadata, record(catalogUri, "catalog")).firstOrNull {
                it.isFile && it.length() in 1..BuilderCatalog.MAX_CATALOG_BYTES
            } ?: throw network
            val catalog = BuilderCatalog.parse(cached.readText(), catalogUri)
            token.check(); return catalog
        }
    }

    private fun record(uri: URI, kind: String) = File(cache, "$kind-${BuilderFiles.sha256(uri.toString().toByteArray(Charsets.UTF_8))}.json")

    fun resolve(catalogUri: URI, token: BuildCancellation): ResolvedBuilder {
        BuilderCatalog.origin(catalogUri)
        val record = record(catalogUri, "catalog")
        var unclaimedDirectory: File? = null
        try {
            val bytes = fetch(catalogUri, BuilderCatalog.MAX_CATALOG_BYTES, token)
            require(bytes.size <= BuilderCatalog.MAX_CATALOG_BYTES)
            val catalog = BuilderCatalog.parse(bytes.toString(Charsets.UTF_8), catalogUri)
            val directory = bundle(catalog, token)
            unclaimedDirectory = directory
            BuilderFiles.atomicWrite(record, bytes)
            unclaimedDirectory = null
            return ResolvedBuilder(catalog, directory, false)
        } catch (network: IOException) {
            unclaimedDirectory?.deleteRecursively()
            token.check()
            if (!record.isFile || record.length() > BuilderCatalog.MAX_CATALOG_BYTES) throw network
            val catalog = BuilderCatalog.parse(record.readText(), catalogUri)
            return ResolvedBuilder(catalog, bundle(catalog, token, offline = true), true)
        } catch (error: Exception) {
            unclaimedDirectory?.deleteRecursively()
            throw error
        }
    }

    private fun bundle(catalog: BuilderCatalog, token: BuildCancellation, offline: Boolean = false): File {
        val entry = catalog.bundle; val zip = File(cache, "bundles/${entry.sha256}.zip")
        if (!zip.isFile || zip.length() != entry.bytes || BuilderFiles.sha256(zip.readBytes()) != entry.sha256) {
            if (offline) throw IOException("No verified offline builder is cached")
            val bytes = fetch(entry.uri, entry.bytes, token)
            require(bytes.size.toLong() == entry.bytes && BuilderFiles.sha256(bytes) == entry.sha256) { "Builder checksum does not match catalogue" }
            BuilderFiles.atomicWrite(zip, bytes)
        }
        val directory = File(cache, "runs/${UUID.randomUUID()}")
        try {
            val expanded = BuilderFiles.extract(zip, directory, entry.unpackedBytes, 1000, token)
            require(expanded == entry.unpackedBytes) { "Builder unpacked size does not match catalogue" }
            require(BuilderFiles.safeFile(directory, entry.entrypoint).isFile) { "Builder runner is missing" }
            return directory
        } catch (error: Exception) { directory.deleteRecursively(); throw error }
    }

    companion object {
        private fun download(uri: URI, limit: Long, token: BuildCancellation): ByteArray {
            BuilderCatalog.origin(uri); token.check()
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false; connection.connectTimeout = 15000; connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "GBA-Duo-Plus-Companion-Builder/1")
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("Builder download failed (HTTP $status)")
                require(connection.contentLengthLong <= limit) { "Download exceeds size limit" }
                return connection.inputStream.use { BuilderFiles.readBounded(it, limit, token) }
            } finally { connection.disconnect() }
        }
    }
}
