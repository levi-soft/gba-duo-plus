package com.khumam.gbaduo.artwork

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.net.URI
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

data class ArtworkRequest(val uri: URI, val headers: Map<String, String> = emptyMap(), val maxBytes: Int = 2 * 1024 * 1024) {
    override fun toString(): String = "ArtworkRequest(host=${uri.host}, maxBytes=$maxBytes)"
}
data class ArtworkResponse(val status: Int, val body: ByteArray, val headers: Map<String, String>) {
    override fun toString(): String = "ArtworkResponse(status=$status, bytes=${body.size})"
}
fun interface ArtworkTransport { fun request(request: ArtworkRequest): ArtworkResponse }

/** One bounded HTTPS transport. API secrets are never passed to image/CDN requests. */
object ArtworkHttp {
    const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
    private const val MAX_JSON_BYTES = 2 * 1024 * 1024
    val transport = ArtworkTransport { request ->
        val connection = request.uri.toURL().openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "GBA-Duo-Plus-Artwork/1")
        request.headers.forEach(connection::setRequestProperty)
        try {
            checkInterrupted()
            val status = connection.responseCode
            val responseHeaders = connection.headerFields.entries.mapNotNull { (key, values) ->
                key?.let { it to values.firstOrNull().orEmpty() }
            }.toMap()
            if (status !in 200..299) ArtworkResponse(status, ByteArray(0), responseHeaders)
            else {
                if (connection.contentLengthLong > request.maxBytes) throw ArtworkProviderException("Artwork response is too large.")
                val data = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16384)
                    while (true) {
                        checkInterrupted()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > request.maxBytes) throw ArtworkProviderException("Artwork response is too large.")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                ArtworkResponse(status, data, responseHeaders)
            }
        } finally { connection.disconnect() }
    }

    fun json(uri: URI, headers: Map<String, String> = emptyMap(), transport: ArtworkTransport = this.transport): JSONObject {
        requireHttps(uri)
        val response = perform(ArtworkRequest(uri, headers, MAX_JSON_BYTES), transport)
        try { return JSONObject(response.body.toString(Charsets.UTF_8)) }
        catch (_: Exception) { throw ArtworkProviderException("The artwork service returned an invalid response.") }
    }

    fun download(candidate: ArtworkCandidate, transport: ArtworkTransport = this.transport): ByteArray {
        val uri = imageUri(candidate.provider, candidate.url)
        return perform(ArtworkRequest(uri, emptyMap(), MAX_IMAGE_BYTES), transport).body
    }

    /** Bounded plain-text index; the caller supplies a fixed trusted endpoint. */
    fun text(uri: URI, maxBytes: Int = 4 * 1024 * 1024, transport: ArtworkTransport = this.transport): String {
        requireHttps(uri)
        require(maxBytes in 1..4 * 1024 * 1024)
        return perform(ArtworkRequest(uri, emptyMap(), maxBytes), transport).body.toString(Charsets.UTF_8)
    }

    /** Also used by parsers before presenting an untrusted response URL in the UI. */
    fun imageUri(provider: String, address: String): URI {
        val uri = try { URI(address) } catch (_: Exception) { throw ArtworkProviderException("Invalid artwork address.") }
        requireHttps(uri)
        val host = uri.host.lowercase()
        val allowed = when (provider) {
            "steamgriddb" -> host in setOf("cdn.steamgriddb.com", "cdn2.steamgriddb.com")
            "libretro" -> host == "thumbnails.libretro.com"
            else -> false
        }
        if (!allowed) throw ArtworkProviderException("The artwork address is not from the selected source.")
        return uri
    }

    fun url(base: String, query: Map<String, String>): URI = URI(base + "?" + query.entries.joinToString("&") { encode(it.key) + "=" + encode(it.value) })
    fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    fun checkInterrupted() { if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Artwork operation cancelled.") }

    private fun requireHttps(uri: URI) {
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.port !in listOf(-1, 443) || uri.fragment != null) {
            throw ArtworkProviderException("Only secure artwork addresses are supported.")
        }
    }

    private fun perform(request: ArtworkRequest, transport: ArtworkTransport): ArtworkResponse {
        try {
            checkInterrupted()
            val response = transport.request(request)
            if (response.status !in 200..299) {
                val delay = response.headers.entries.firstOrNull { it.key.equals("Retry-After", true) }?.value?.toLongOrNull()?.coerceIn(1, 3600)
                val message = when (response.status) {
                    401, 403 -> "The artwork source rejected the configured credentials."
                    404 -> "No matching artwork was found."
                    429 -> "Artwork rate limit reached. Try again later."
                    in 300..399 -> "The artwork source returned an unsupported redirect."
                    else -> "The artwork service is unavailable (HTTP ${response.status})."
                }
                throw ArtworkProviderException(message, delay, response.status)
            }
            if (response.body.size > request.maxBytes) throw ArtworkProviderException("Artwork response is too large.")
            checkInterrupted()
            return response
        } catch (safe: ArtworkProviderException) { throw safe }
        catch (_: InterruptedIOException) { throw ArtworkProviderException("Artwork operation cancelled.") }
        catch (_: Exception) { throw ArtworkProviderException("Could not reach the artwork service.") }
    }
}
