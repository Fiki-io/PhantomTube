package okhttp3

import android.net.http.HttpResponseCache
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class MediaType internal constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        fun String.toMediaType(): MediaType = MediaType(this)
    }
}

open class RequestBody(
    val bytes: ByteArray,
    val contentType: MediaType? = null
) {
    companion object {
        fun String.toRequestBody(contentType: MediaType? = null): RequestBody =
            RequestBody(this.toByteArray(Charsets.UTF_8), contentType)
    }
}

class FormBody private constructor(encodedBytes: ByteArray) :
    RequestBody(encodedBytes, MediaType("application/x-www-form-urlencoded; charset=UTF-8")) {

    class Builder {
        private val pairs = ArrayList<Pair<String, String>>()

        fun add(name: String, value: String): Builder = apply {
            pairs.add(name to value)
        }

        fun build(): FormBody {
            val encoded = pairs.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            return FormBody(encoded.toByteArray(Charsets.UTF_8))
        }
    }
}

class CacheControl private constructor(val noCache: Boolean) {
    companion object {
        val FORCE_NETWORK = CacheControl(noCache = true)
    }
}

class Cache(val directory: File, val maxSize: Long) {
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

class ConnectionPool(
    val maxIdleConnections: Int = 5,
    val keepAliveDuration: Long = 5,
    val timeUnit: TimeUnit = TimeUnit.MINUTES
)

class Dispatcher {
    var maxRequests: Int = 64
    var maxRequestsPerHost: Int = 5
}

class Request internal constructor(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: RequestBody?,
    val forceNetwork: Boolean
) {
    class Builder {
        private var url: String = ""
        private var method: String = "GET"
        private val headers = LinkedHashMap<String, String>()
        private var body: RequestBody? = null
        private var forceNetwork: Boolean = false

        fun url(url: String): Builder = apply { this.url = url }

        fun header(name: String, value: String): Builder = apply {
            headers[name] = value
        }

        fun addHeader(name: String, value: String): Builder = apply {
            headers[name] = value
        }

        fun cacheControl(cacheControl: CacheControl): Builder = apply {
            this.forceNetwork = cacheControl.noCache
        }

        fun get(): Builder = apply {
            this.method = "GET"
            this.body = null
        }

        fun post(body: RequestBody): Builder = apply {
            this.method = "POST"
            this.body = body
        }

        fun build(): Request = Request(
            url = url,
            method = method,
            headers = headers.toMap(),
            body = body,
            forceNetwork = forceNetwork
        )
    }
}

class ResponseBody(
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

class Response(
    val code: Int,
    val headers: List<Pair<String, String>>,
    val body: ResponseBody?,
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

class Call internal constructor(
    private val client: OkHttpClient,
    private val request: Request
) {
    fun execute(): Response {
        var currentUrl = request.url
        var redirectCount = 0
        var conn: HttpURLConnection? = null

        while (redirectCount < 8) {
            val targetUrl = URL(currentUrl)
            conn = (targetUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = client.connectTimeoutMs
                readTimeout = client.readTimeoutMs
                instanceFollowRedirects = client.followRedirects
                useCaches = !request.forceNetwork
                requestMethod = if (redirectCount > 0 && request.method == "POST") "GET" else request.method
            }

            var hasAcceptEncoding = false
            var hasContentType = false
            for ((k, v) in request.headers) {
                conn.setRequestProperty(k, v)
                if (k.equals("Accept-Encoding", ignoreCase = true)) hasAcceptEncoding = true
                if (k.equals("Content-Type", ignoreCase = true)) hasContentType = true
            }
            if (!hasAcceptEncoding) {
                conn.setRequestProperty("Accept-Encoding", "gzip")
            }

            val reqBody = if (redirectCount == 0) request.body else null
            if (reqBody != null && conn.requestMethod == "POST") {
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
                    conn.disconnect()
                    currentUrl = URL(targetUrl, location).toString()
                    redirectCount++
                    continue
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

            val finalConn = conn
            val responseBody = ResponseBody(
                streamProvider = { decodedStream },
                length = contentLength,
                onClose = {
                    try {
                        decodedStream.close()
                    } catch (_: Exception) {
                    }
                    try {
                        finalConn.disconnect()
                    } catch (_: Exception) {
                    }
                }
            )

            return Response(
                code = status,
                headers = headerPairs,
                body = responseBody,
                connection = finalConn
            )
        }

        throw java.io.IOException("Too many redirects for ${request.url}")
    }
}

class OkHttpClient internal constructor(
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val followRedirects: Boolean,
    val followSslRedirects: Boolean,
    val dispatcher: Dispatcher,
    val connectionPool: ConnectionPool?,
    val cache: Cache?
) {
    constructor() : this(
        connectTimeoutMs = 10_000,
        readTimeoutMs = 10_000,
        followRedirects = true,
        followSslRedirects = true,
        dispatcher = Dispatcher(),
        connectionPool = null,
        cache = null
    )

    fun newCall(request: Request): Call = Call(this, request)

    fun newBuilder(): Builder = Builder(this)

    class Builder() {
        private var connectTimeoutMs: Int = 10_000
        private var readTimeoutMs: Int = 10_000
        private var followRedirects: Boolean = true
        private var followSslRedirects: Boolean = true
        private var dispatcher: Dispatcher = Dispatcher()
        private var connectionPool: ConnectionPool? = null
        private var cache: Cache? = null

        internal constructor(client: OkHttpClient) : this() {
            this.connectTimeoutMs = client.connectTimeoutMs
            this.readTimeoutMs = client.readTimeoutMs
            this.followRedirects = client.followRedirects
            this.followSslRedirects = client.followSslRedirects
            this.dispatcher = client.dispatcher
            this.connectionPool = client.connectionPool
            this.cache = client.cache
        }

        fun dispatcher(dispatcher: Dispatcher): Builder = apply {
            this.dispatcher = dispatcher
        }

        fun connectTimeout(timeout: Long, unit: TimeUnit): Builder = apply {
            this.connectTimeoutMs = unit.toMillis(timeout).coerceIn(1_000L, 120_000L).toInt()
        }

        fun readTimeout(timeout: Long, unit: TimeUnit): Builder = apply {
            this.readTimeoutMs = unit.toMillis(timeout).coerceIn(1_000L, 120_000L).toInt()
        }

        fun connectionPool(connectionPool: ConnectionPool): Builder = apply {
            this.connectionPool = connectionPool
        }

        fun cache(cache: Cache?): Builder = apply {
            this.cache = cache
        }

        fun followRedirects(followRedirects: Boolean): Builder = apply {
            this.followRedirects = followRedirects
        }

        fun followSslRedirects(followProtocolRedirects: Boolean): Builder = apply {
            this.followSslRedirects = followProtocolRedirects
        }

        fun retryOnConnectionFailure(retryOnConnectionFailure: Boolean): Builder = apply {
        }

        fun writeTimeout(timeout: Long, unit: TimeUnit): Builder = apply {
        }

        fun build(): OkHttpClient = OkHttpClient(
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            followRedirects = followRedirects,
            followSslRedirects = followSslRedirects,
            dispatcher = dispatcher,
            connectionPool = connectionPool,
            cache = cache
        )
    }
}
