package com.phantom.tube.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class PhantomBackgroundWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        // Render directly to the hardware-accelerated window surface without allocating a duplicate offscreen FBO texture
        setLayerType(View.LAYER_TYPE_NONE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setRendererPriorityPolicy(RENDERER_PRIORITY_BOUND, true)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Intercept GONE/INVISIBLE to keep Chromium audio and timers running in background / screen off
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onPause() {
        // Do NOT pause Chromium media or timers when activity is in background
        resumeTimers()
    }
}

class PhantomPlayerController(context: Context) {
    private val appContext: Context = context.applicationContext
    private var webView: WebView? = null
    private var isReady = false
    private var pendingVideoId: String? = null
    private var pendingStartSeconds: Float = 0f
    private var isSurfaceCollapsed = false

    fun attachWebView(view: WebView) {
        this.webView = view
    }

    fun onReady() {
        isReady = true
        val targetId = pendingVideoId
        if (targetId != null) {
            loadVideo(targetId, pendingStartSeconds)
            pendingVideoId = null
            pendingStartSeconds = 0f
        }
    }

    fun setPhysicalViewportCollapsed(collapsed: Boolean) {
        if (isSurfaceCollapsed == collapsed) return
        isSurfaceCollapsed = collapsed
        val target = webView ?: return
        target.post {
            try {
                val lp = target.layoutParams
                if (lp != null) {
                    val targetSize = if (collapsed) 1 else ViewGroup.LayoutParams.MATCH_PARENT
                    if (lp.width != targetSize || lp.height != targetSize) {
                        lp.width = targetSize
                        lp.height = targetSize
                        target.layoutParams = lp
                        target.requestLayout()
                    }
                }
                if (collapsed) {
                    com.phantom.tube.core.util.PhantomMemoryOptimizer.trimNow(
                        context = appContext,
                        webView = target,
                        aggressiveGraphicsPurge = true
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    fun loadVideo(videoId: String, startSeconds: Float = 0f) {
        if (isReady && webView != null) {
            com.phantom.tube.core.util.PhantomMemoryOptimizer.trimNow(
                context = appContext,
                webView = webView,
                aggressiveGraphicsPurge = false
            )
            evaluateJs("window.loadVideo('$videoId', $startSeconds);")
        } else {
            pendingVideoId = videoId
            pendingStartSeconds = startSeconds
        }
    }

    fun play() {
        evaluateJs("window.playVideo();")
    }

    fun pause() {
        evaluateJs("window.pauseVideo();")
    }

    fun seekTo(seconds: Float) {
        evaluateJs("window.seekTo($seconds);")
    }

    fun setPlaybackRate(rate: Float) {
        evaluateJs("window.setPlaybackRate($rate);")
    }

    fun setLoop(loop: Boolean) {
        evaluateJs("window.setLoop($loop);")
    }

    fun setPlaybackQuality(quality: String) {
        evaluateJs("window.setPlaybackQuality('$quality');")
    }

    fun setCaptionsEnabled(enabled: Boolean, languageCode: String = "id") {
        evaluateJs("if (window.setCaptionsEnabled) window.setCaptionsEnabled($enabled, '$languageCode');")
    }

    fun setAudioOnly(enabled: Boolean) {
        setPhysicalViewportCollapsed(enabled)
        evaluateJs("if (window.setAudioOnly) window.setAudioOnly($enabled);")
    }

    fun setVideoSurfaceActive(active: Boolean, restoreQuality: String = "auto") {
        setPhysicalViewportCollapsed(!active)
        evaluateJs("if (window.setVideoSurfaceActive) window.setVideoSurfaceActive($active, '$restoreQuality');")
    }

    fun requestStoryboard() {
        evaluateJs("if (window.requestStoryboard) window.requestStoryboard();")
    }

    private fun evaluateJs(script: String) {
        val target = webView ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            target.evaluateJavascript(script, null)
        } else {
            target.post { target.evaluateJavascript(script, null) }
        }
    }

    fun release() {
        webView?.apply {
            try {
                (parent as? ViewGroup)?.removeView(this)
                stopLoading()
                clearHistory()
                clearCache(false)
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            } catch (_: Exception) {
            }
        }
        webView = null
        isReady = false
        pendingVideoId = null
        isSurfaceCollapsed = false
        com.phantom.tube.core.util.PhantomMemoryOptimizer.trimNow(
            context = appContext,
            aggressiveGraphicsPurge = true
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PhantomGhostSurface(
    videoId: String,
    startSeconds: Float = 0f,
    modifier: Modifier = Modifier,
    controller: PhantomPlayerController,
    bridge: PhantomPlayerBridge
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PhantomBackgroundWebView(ctx).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = false
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    allowFileAccess = false
                    allowContentAccess = false
                    offscreenPreRaster = false
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    setGeolocationEnabled(false)
                    setNeedInitialFocus(false)
                    textZoom = 100
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    setSupportMultipleWindows(false)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = false
                    }

                    // Clean Chrome User-Agent
                    userAgentString = com.phantom.tube.core.security.PhantomNative.getUserAgent()
                }

                clearCache(false)
                webChromeClient = WebChromeClient()

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val cleanResp = PhantomRenderEngine.intercept(request)
                        if (cleanResp != null) return cleanResp

                        return super.shouldInterceptRequest(view, request)
                    }
                }

                addJavascriptInterface(bridge, "PhantomBridge")
                controller.attachWebView(this)

                // Load with official HTTPS domain via in-memory native engine
                val startInt = startSeconds.toInt()
                val origin = com.phantom.tube.core.security.PhantomNative.getOrigin()
                loadUrl("$origin/engine/render?v=$videoId&start=$startInt")
            }
        },
        update = { /* controller maintains internal state */ }
    )
}

object PhantomRenderEngine {
    private var customHttpClient: okhttp3.OkHttpClient? = null
    private val EMPTY_BYTES = ByteArray(0)
    private val NO_CONTENT_HEADERS = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Cache-Control" to "no-store"
    )

    fun init(client: okhttp3.OkHttpClient) {
        customHttpClient = client
    }

    private val defaultHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val httpClient: okhttp3.OkHttpClient
        get() = customHttpClient ?: defaultHttpClient

    private const val MAX_CSS_CACHE_ENTRIES = 2
    private val cssCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    fun clearCache() {
        cssCache.clear()
    }

    val CSS_RULES: String get() = com.phantom.tube.core.security.PhantomNative.getCssRules()


    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val reqType = com.phantom.tube.core.security.PhantomNative.classifyRequestUrl(url)
        if (reqType == 0) return null

        if (reqType == 4) {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                204,
                "No Content",
                NO_CONTENT_HEADERS,
                java.io.ByteArrayInputStream(EMPTY_BYTES)
            )
        }

        if (request.method != "GET") return null

        if (reqType == 1) {
            val html = com.phantom.tube.core.security.PhantomNative.getHtml()
            val bytes = html.toByteArray(Charsets.UTF_8)
            val headers = mapOf(
                "Content-Type" to "text/html; charset=utf-8",
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-store, no-cache, must-revalidate"
            )
            return WebResourceResponse("text/html", "utf-8", 200, "OK", headers, java.io.ByteArrayInputStream(bytes))
        }

        val isEmbedHtml = (reqType == 2)
        val isPlayerCss = (reqType == 3)

        try {
            if (isPlayerCss) {
                val cached = cssCache[url]
                if (cached != null) {
                    val headers = mapOf(
                        "Content-Type" to "text/css; charset=utf-8",
                        "Access-Control-Allow-Origin" to "*"
                    )
                    return WebResourceResponse("text/css", "utf-8", 200, "OK", headers, java.io.ByteArrayInputStream(cached))
                }
            }

            val reqBuilder = okhttp3.Request.Builder()
                .url(url)
                .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
            for ((k, v) in request.requestHeaders) {
                // Strip Accept-Encoding and conditional cache headers so YouTube always returns 200 OK body
                val lower = k.lowercase()
                if (lower != "accept-encoding" &&
                    lower != "if-none-match" &&
                    lower != "if-modified-since" &&
                    lower != "cache-control" &&
                    lower != "pragma"
                ) {
                    reqBuilder.header(k, v)
                }
            }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null

                if (isPlayerCss) {
                    val rawCss = resp.body?.string() ?: ""
                    val modifiedCss = rawCss + "\n\n/* Phantom Clean Engine */\n" + CSS_RULES
                    val bytes = modifiedCss.toByteArray(Charsets.UTF_8)
                    if (cssCache.size >= MAX_CSS_CACHE_ENTRIES) {
                        cssCache.clear()
                    }
                    cssCache[url] = bytes
                    val headers = mapOf(
                        "Content-Type" to "text/css; charset=utf-8",
                        "Access-Control-Allow-Origin" to "*"
                    )
                    return WebResourceResponse("text/css", "utf-8", 200, "OK", headers, java.io.ByteArrayInputStream(bytes))
                }

                if (isEmbedHtml) {
                    val rawHtml = resp.body?.string() ?: ""
                    val styleNonceMatch = Regex("""<style[^>]*nonce=["']([^"']+)["']""").find(rawHtml)
                        ?: Regex("""nonce=["']([^"']+)["']""").find(rawHtml)
                    val styleNonce = styleNonceMatch?.groupValues?.get(1) ?: ""
                    val styleNonceAttr = if (styleNonce.isNotEmpty()) " nonce=\"$styleNonce\"" else ""

                    val scriptNonceMatch = Regex("""<script[^>]*nonce=["']([^"']+)["']""").find(rawHtml)
                        ?: Regex("""nonce=["']([^"']+)["']""").find(rawHtml)
                    val scriptNonce = scriptNonceMatch?.groupValues?.get(1) ?: ""
                    val scriptNonceAttr = if (scriptNonce.isNotEmpty()) " nonce=\"$scriptNonce\"" else ""

                    val styleToInject = "<style id=\"phantom-clean-engine\"$styleNonceAttr>$CSS_RULES</style>"
                    val scriptBody = com.phantom.tube.core.security.PhantomNative.getCleanEngineScript()
                    val scriptToInject = "<script id=\"phantom-clean-script\"$scriptNonceAttr>$scriptBody</script>"

                    val payload = styleToInject + scriptToInject
                    val modifiedHtml = when {
                        rawHtml.contains("</head>") -> rawHtml.replace("</head>", "$payload</head>")
                        rawHtml.contains("<body") -> rawHtml.replace("<body", "$payload<body")
                        else -> payload + rawHtml
                    }

                    val bytes = modifiedHtml.toByteArray(Charsets.UTF_8)
                    val headers = mutableMapOf<String, String>()
                    for ((k, v) in resp.headers) {
                        val lower = k.lowercase()
                        // Strip CSP and encoding headers so injected styles and scripts run unhindered
                        if (lower != "content-encoding" &&
                            lower != "content-length" &&
                            !lower.contains("content-security-policy") &&
                            !lower.contains("x-content-security-policy") &&
                            !lower.contains("x-webkit-csp")
                        ) {
                            headers[k] = v
                        }
                    }
                    headers["Content-Type"] = "text/html; charset=utf-8"
                    headers["Access-Control-Allow-Origin"] = "*"
                    return WebResourceResponse("text/html", "utf-8", 200, "OK", headers, java.io.ByteArrayInputStream(bytes))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PhantomCleanEngine", "Intercept error for $url", e)
        }

        return null
    }
}
