package com.khumam.gbaduo.companion.builder

import org.json.JSONObject
import java.net.URI

data class BuilderProfile(val id: String, val name: String, val version: String, val gameCode: String, val crc32: Long)
data class BuilderBundle(val uri: URI, val sha256: String, val bytes: Long, val unpackedBytes: Long, val entrypoint: String)
data class BuilderCatalog(val version: String, val bundle: BuilderBundle, val profiles: List<BuilderProfile>) {
    fun profile(gameCode: String, crc32: Long): BuilderProfile? = profiles.singleOrNull { it.gameCode == gameCode && it.crc32 == crc32 }

    companion object {
        const val MAX_CATALOG_BYTES = 256 * 1024L
        const val MAX_BUNDLE_BYTES = 32 * 1024 * 1024L
        const val MAX_BUNDLE_UNPACKED = 128 * 1024 * 1024L
        private val idPattern = Regex("^[a-z0-9-]{1,64}$")
        private val versionPattern = Regex("^[A-Za-z0-9][A-Za-z0-9.+_-]{0,39}$")
        fun origin(uri: URI): String {
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) { "Builder address must use HTTPS" }
            require(uri.port == -1 || uri.port == 443) { "Builder address uses an unsupported port" }
            return "https://${uri.host.lowercase()}"
        }
        fun parse(json: String, catalogUri: URI): BuilderCatalog {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES) { "Catalogue exceeds size limit" }
            origin(catalogUri)
            val root = try { JSONObject(json) } catch (e: Exception) { throw IllegalArgumentException("Invalid builder catalogue", e) }
            require(root.getInt("schemaVersion") == 1 && root.getInt("runnerProtocol") == 1) { "Unsupported builder protocol" }
            val version = root.getString("builderVersion"); require(versionPattern.matches(version)) { "Invalid builder version" }
            val raw = root.getJSONObject("builder")
            val relative = raw.getString("url")
            require(relative.isNotBlank() && !relative.startsWith("/") && !relative.contains('\\') && relative.split('/').none { it == ".." }) { "Unsafe builder bundle address" }
            val link = URI(relative); require(!link.isAbsolute && link.query == null && link.fragment == null) { "Bundle must use a relative address" }
            val uri = catalogUri.resolve(link); require(origin(uri) == origin(catalogUri)) { "Bundle must have the catalogue origin" }
            val hash = raw.getString("sha256").lowercase(); require(Regex("^[0-9a-f]{64}$").matches(hash)) { "Invalid bundle checksum" }
            val size = raw.getLong("bytes"); val unpacked = raw.getLong("unpackedBytes")
            require(size in 1..MAX_BUNDLE_BYTES && unpacked in 1..MAX_BUNDLE_UNPACKED) { "Builder bundle exceeds size limits" }
            val entry = raw.getString("entrypoint"); require(entry == "plus-runner.html") { "Unsupported builder entrypoint" }
            val array = root.getJSONArray("profiles"); require(array.length() in 1..256) { "Invalid profile count" }
            val profiles = (0 until array.length()).map { n ->
                val p = array.getJSONObject(n); val id = p.getString("id"); val name = p.getString("name")
                val code = p.getString("gameCode"); val crc = p.getString("crc32"); val pv = p.getString("version")
                require(idPattern.matches(id) && name.isNotBlank() && name.length <= 160 && versionPattern.matches(pv)) { "Invalid profile identity" }
                require(Regex("^[A-Z0-9]{4}$").matches(code) && Regex("^[0-9A-Fa-f]{8}$").matches(crc)) { "Invalid ROM identity" }
                BuilderProfile(id, name, pv, code, crc.toLong(16))
            }
            require(profiles.distinctBy { it.id }.size == profiles.size && profiles.distinctBy { it.gameCode to it.crc32 }.size == profiles.size) { "Duplicate builder profiles" }
            return BuilderCatalog(version, BuilderBundle(uri, hash, size, unpacked, entry), profiles)
        }
    }
}
