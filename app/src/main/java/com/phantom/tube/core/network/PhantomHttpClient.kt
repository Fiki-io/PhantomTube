package com.phantom.tube.core.network

import android.net.http.HttpResponseCache
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

/**
 * Lightweight internal HTTP client built directly on Android SDK's [HttpURLConnection]
 * and [HttpResponseCache], with zero third-party networking dependencies.
 */
class HttpMediaType internal constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        fun String.toMediaType(): HttpMediaType = HttpMediaType(this)
    }
}

open class HttpRequestBody(
    val bytes: ByteArray,
    val contentType: HttpMediaType? = null
) {
    companion object {
        fun String.toRequestBody(contentType: HttpMediaType? = null): HttpRequestBody =
            HttpRequestBody(this.toByteArray(Charsets.UTF_8), contentType)
    }
}

class HttpFormBody private constructor(encodedBytes: ByteArray) :
    HttpRequestBody(encodedBytes, HttpMediaType("application/x-www-form-urlencoded; charset=UTF-8")) {

    class Builder {
        private val pairs = ArrayList<Pair<String, String>>()

        fun add(name: String, value: String): Builder = apply {
            pairs.add(name to value)
        }

        fun build(): HttpFormBody {
            val encoded = pairs.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            return HttpFormBody(encoded.toByteArray(Charsets.UTF_8))
        }
    }
}

class HttpCacheControl private constructor(val noCache: Boolean) {
    companion object {
        val FORCE_NETWORK = HttpCacheControl(noCache = true)
    }
}

class HttpCache(val directory: File, val maxSize: Long) {
    init {
        try {
            if (HttpResponseCache.getInstalled() == null) {
                if (!directory.exists()) directory.mkdirs()
                HttpResponseCache.install(directory, maxSize)
            }
        } catch (_: Exception) {
        }
    }
}

class HttpDispatcher {
    var maxRequests: Int = 64
    var maxRequestsPerHost: Int = 5

    private val globalSemaphore by lazy { Semaphore(maxRequests.coerceAtLeast(1)) }
    private val hostSemaphores = ConcurrentHashMap<String, Semaphore>()

    internal fun acquireGlobal() {
        globalSemaphore.acquire()
    }

    internal fun releaseGlobal() {
        globalSemaphore.release()
    }

    internal fun acquireHost(host: String) {
        if (host.isBlank()) return
        val sem = hostSemaphores.getOrPut(host.lowercase()) {
            Semaphore(maxRequestsPerHost.coerceAtLeast(1))
        }
        sem.acquire()
    }

    internal fun releaseHost(host: String) {
        if (host.isBlank()) return
        hostSemaphores[host.lowercase()]?.release()
    }
}

class HttpRequest internal constructor(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: HttpRequestBody?,
    val forceNetwork: Boolean
) {
    class Builder {
        private var url: String = ""
        private var method: String = "GET"
        private val headers = LinkedHashMap<String, String>()
        private var body: HttpRequestBody? = null
        private var forceNetwork: Boolean = false

        fun url(url: String): Builder = apply { this.url = url }

        fun header(name: String, value: String): Builder = apply {
            headers[name] = value
        }

        fun addHeader(name: String, value: String): Builder = apply {
            headers[name] = value
        }

        fun cacheControl(cacheControl: HttpCacheControl): Builder = apply {
            this.forceNetwork = cacheControl.noCache
        }

        fun get(): Builder = apply {
            this.method = "GET"
            this.body = null
        }

        fun post(body: HttpRequestBody): Builder = apply {
            this.method = "POST"
            this.body = body
        }

        fun build(): HttpRequest = HttpRequest(
            url = url,
            method = method,
            headers = headers.toMap(),
            body = body,
            forceNetwork = forceNetwork
        )
    }
}

class HttpResponseBody(
    private val streamProvider: () -> InputStream,
    private val length: Long,
    private val onClose: () -> Unit
) : Closeable {

    fun contentLength(): Long = length

    fun byteStream(): InputStream = streamProvider()

    fun bytes(): ByteArray = byteStream().use { it.readBytes() }

    fun string(): String = byteStream().use { it.readBytes().toString(Charsets.UTF_8) }

    override fun close() {
        onClose()
    }
}

class HttpResponse(
    val code: Int,
    val headers: List<Pair<String, String>>,
    val body: HttpResponseBody?,
    private val connection: HttpURLConnection?
) : Closeable {

    val isSuccessful: Boolean
        get() = code in 200..299

    override fun close() {
        try {
            body?.close()
        } catch (_: Exception) {
        }
        try {
            connection?.disconnect()
        } catch (_: Exception) {
        }
    }
}

class HttpCall internal constructor(
    private val client: PhantomHttpClient,
    private val request: HttpRequest
) {
    fun execute(): HttpResponse {
        var currentUrl = request.url
        var currentMethod = request.method
        var currentBody = request.body
        val initialHost = runCatching { URL(request.url).host }.getOrDefault("")
        var currentHost = initialHost
        var redirectCount = 0

        client.dispatcher.acquireGlobal()
        client.dispatcher.acquireHost(currentHost)
        val released = AtomicBoolean(false)
        val releasePermits = {
            if (released.compareAndSet(false, true)) {
                client.dispatcher.releaseHost(currentHost)
                client.dispatcher.releaseGlobal()
            }
        }

        try {
            while (redirectCount < 8) {
                val targetUrl = URL(currentUrl)
                val targetHost = targetUrl.host.orEmpty()
                if (!targetHost.equals(currentHost, ignoreCase = true)) {
                    client.dispatcher.releaseHost(currentHost)
                    currentHost = targetHost
                    client.dispatcher.acquireHost(currentHost)
                }

                val conn = (targetUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = client.connectTimeoutMs
                    readTimeout = client.readTimeoutMs
                    instanceFollowRedirects = false
                    useCaches = !request.forceNetwork
                    requestMethod = currentMethod
                }

                val isCrossHost = !targetHost.equals(initialHost, ignoreCase = true)
                var hasAcceptEncoding = false
                var hasContentType = false
                for ((k, v) in request.headers) {
                    if (isCrossHost && (k.equals("Authorization", ignoreCase = true) || k.equals("Cookie", ignoreCase = true))) {
                        continue
                    }
                    conn.setRequestProperty(k, v)
                    if (k.equals("Accept-Encoding", ignoreCase = true)) hasAcceptEncoding = true
                    if (k.equals("Content-Type", ignoreCase = true)) hasContentType = true
                }
                if (!hasAcceptEncoding) {
                    conn.setRequestProperty("Accept-Encoding", "gzip")
                }

                val reqBody = currentBody
                if (reqBody != null && (currentMethod == "POST" || currentMethod == "PUT" || currentMethod == "PATCH")) {
                    if (!hasContentType && reqBody.contentType != null) {
                        conn.setRequestProperty("Content-Type", reqBody.contentType.value)
                    }
                    conn.doOutput = true
                    conn.setFixedLengthStreamingMode(reqBody.bytes.size)
                    conn.outputStream.use { out ->
                        out.write(reqBody.bytes)
                        out.flush()
                    }
                }

                val status = conn.responseCode
                if (client.followRedirects && status in listOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        val nextUrl = URL(targetUrl, location)
                        val schemeChanged = !nextUrl.protocol.equals(targetUrl.protocol, ignoreCase = true)
                        if (!schemeChanged || client.followSslRedirects) {
                            conn.disconnect()
                            if (status in 301..303 && currentMethod == "POST") {
                                currentMethod = "GET"
                                currentBody = null
                            }
                            currentUrl = nextUrl.toString()
                            redirectCount++
                            continue
                        }
                    }
                }

                val headerPairs = ArrayList<Pair<String, String>>()
                val headerMap = conn.headerFields
                if (headerMap != null) {
                    for ((k, values) in headerMap) {
                        if (k != null && values != null) {
                            for (v in values) {
                                if (v != null) {
                                    headerPairs.add(k to v)
                                }
                            }
                        }
                    }
                }

                val encoding = conn.contentEncoding ?: ""
                val isGzip = encoding.contains("gzip", ignoreCase = true)
                val contentLength = if (isGzip) -1L else conn.contentLengthLong

                val rawStream: InputStream = try {
                    if (status >= 400) {
                        conn.errorStream ?: ByteArrayInputStream(ByteArray(0))
                    } else {
                        conn.inputStream
                    }
                } catch (_: Exception) {
                    conn.errorStream ?: ByteArrayInputStream(ByteArray(0))
                }

                val decodedStream = if (isGzip) {
                    try {
                        GZIPInputStream(rawStream)
                    } catch (_: Exception) {
                        rawStream
                    }
                } else {
                    rawStream
                }

                val responseBody = HttpResponseBody(
                    streamProvider = { decodedStream },
                    length = contentLength,
                    onClose = {
                        try {
                            decodedStream.close()
                        } catch (_: Exception) {
                        }
                        try {
                            conn.disconnect()
                        } catch (_: Exception) {
                        }
                        releasePermits()
                    }
                )

                return HttpResponse(
                    code = status,
                    headers = headerPairs,
                    body = responseBody,
                    connection = conn
                )
            }

            throw java.io.IOException("Too many redirects for ${request.url}")
        } catch (t: Throwable) {
            releasePermits()
            throw t
        }
    }
}

class PhantomHttpClient internal constructor(
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val followRedirects: Boolean,
    val followSslRedirects: Boolean,
    val dispatcher: HttpDispatcher,
    val cache: HttpCache?
) {
    constructor() : this(
        connectTimeoutMs = 10_000,
        readTimeoutMs = 10_000,
        followRedirects = true,
        followSslRedirects = true,
        dispatcher = HttpDispatcher(),
        cache = null
    )

    fun newCall(request: HttpRequest): HttpCall = HttpCall(this, request)

    fun newBuilder(): Builder = Builder(this)

    class Builder() {
        private var connectTimeoutMs: Int = 10_000
        private var readTimeoutMs: Int = 10_000
        private var followRedirects: Boolean = true
        private var followSslRedirects: Boolean = true
        private var dispatcher: HttpDispatcher = HttpDispatcher()
        private var cache: HttpCache? = null

        internal constructor(client: PhantomHttpClient) : this() {
            this.connectTimeoutMs = client.connectTimeoutMs
            this.readTimeoutMs = client.readTimeoutMs
            this.followRedirects = client.followRedirects
            this.followSslRedirects = client.followSslRedirects
            this.dispatcher = client.dispatcher
            this.cache = client.cache
        }

        fun dispatcher(dispatcher: HttpDispatcher): Builder = apply {
            this.dispatcher = dispatcher
        }

        fun connectTimeout(timeout: Long, unit: TimeUnit): Builder = apply {
            this.connectTimeoutMs = unit.toMillis(timeout).coerceIn(1_000L, 120_000L).toInt()
        }

        fun readTimeout(timeout: Long, unit: TimeUnit): Builder = apply {
            this.readTimeoutMs = unit.toMillis(timeout).coerceIn(1_000L, 120_000L).toInt()
        }

        fun cache(cache: HttpCache?): Builder = apply {
            this.cache = cache
        }

        fun followRedirects(followRedirects: Boolean): Builder = apply {
            this.followRedirects = followRedirects
        }

        fun followSslRedirects(followProtocolRedirects: Boolean): Builder = apply {
            this.followSslRedirects = followProtocolRedirects
        }

        fun build(): PhantomHttpClient = PhantomHttpClient(
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            followRedirects = followRedirects,
            followSslRedirects = followSslRedirects,
            dispatcher = dispatcher,
            cache = cache
        )
    }
}
