package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.net.http.SslError
import android.webkit.*
import com.lagradost.api.Log
import com.lagradost.api.getContext
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.utils.Coroutines.atomicListOf
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.Coroutines.mainWork
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.nicehttp.requestCreator
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * When used as Interceptor additionalUrls cannot be returned, use WebViewResolver(...).resolveUsingWebView(...)
 * @param interceptUrl will stop the WebView when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * @param userAgent if null then will use the default user agent
 * @param useOkhttp will try to use the okhttp client as much as possible, but this might cause some requests to fail. Disable for cloudflare.
 * @param script pass custom js to execute
 * @param scriptCallback will be called with the result from custom js
 * @param timeout close webview after timeout
 * */
actual class WebViewResolver actual constructor(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex>,
    val userAgent: String?,
    val useOkhttp: Boolean,
    val script: String?,
    val scriptCallback: ((String) -> Unit)?,
    val timeout: Long
) :
    Interceptor {

    actual companion object {
        actual var webViewUserAgent: String? = null
        actual val DEFAULT_TIMEOUT = 60_000L
        private const val TAG = "WebViewResolver"
        private val tvResolverSlots = Semaphore(2)

        @JvmName("getWebViewUserAgent1")
        fun getWebViewUserAgent(): String? {
            return webViewUserAgent ?: (getContext() as? Context)?.let { ctx ->
                runBlocking {
                    mainWork {
                        val view = WebView(ctx)
                        try {
                            view.settings.userAgentString.also { webViewUserAgent = it }
                        } finally {
                            view.destroy()
                        }
                    }
                }
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            val fixedRequest = resolveUsingWebView(request).first
            return@runBlocking chain.proceed(fixedRequest ?: request)
        }
    }

    actual suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        method: String,
        requestCallBack: (Request) -> Boolean,
    ): Pair<Request?, List<Request>> =
        resolveUsingWebView(url, referer, emptyMap(), method, requestCallBack)

    actual suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        headers: Map<String, String>,
        method: String,
        requestCallBack: (Request) -> Boolean,
    ): Pair<Request?, List<Request>> {
        return try {
            resolveUsingWebView(
                requestCreator(method, url, referer = referer, headers = headers), requestCallBack
            )
        } catch (e: java.lang.IllegalArgumentException) {
            logError(e)
            debugException { "ILLEGAL URL IN resolveUsingWebView!" }
            return null to emptyList()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    actual suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        val context = getContext() as? Context
        val isTv = context?.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
        val lowRam = (context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        if (isTv || lowRam) tvResolverSlots.acquire()
        try {
            return resolveRequest(request, requestCallBack)
        } finally {
            if (isTv || lowRam) tvResolverSlots.release()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveRequest(
        request: Request,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        val url = request.url.toString()
        val headers = request.headers
        Log.i(TAG, "Initial web-view request: $url")
        var webView: WebView? = null
        // Extra assurance it exits as it should.
        val shouldExit = AtomicBoolean(false)

        fun destroyOnMain() {
            val view = webView
            webView = null
            if (view != null) {
                runCatching { view.stopLoading() }
                runCatching { view.destroy() }
                Log.i(TAG, "Destroyed webview")
            }
        }

        fun destroyWebView() {
            shouldExit.set(true)
            main { destroyOnMain() }
        }

        val fixedRequest = AtomicReference<Request?>()
        val extraRequestList = atomicListOf<Request>()

        main {
            if (shouldExit.get()) return@main
            try {
                webView = WebView(
                    (getContext() as? Context)
                        ?: throw RuntimeException("No base context in WebViewResolver")
                ).apply {
                    // Bare minimum to bypass captcha
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    webViewUserAgent = settings.userAgentString
                    // Don't set user agent, setting user agent will make cloudflare break.
                    if (userAgent != null) {
                        settings.userAgentString = userAgent
                    }
                    // Blocks unnecessary images, remove if captcha fucks.
//                    settings.blockNetworkImage = true
                }

                webView?.webViewClient = object : WebViewClient() {
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        Log.i(TAG, "WebView renderer exited; ending resolver safely")
                        destroyWebView()
                        return true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = runBlocking {
                        val webViewUrl = request.url.toString()
                        Log.i(TAG, "Loading WebView URL: $webViewUrl")

                        if (script != null) {
                            runOnMainThread {
                                if (!shouldExit.get() && webView === view) {
                                    view.evaluateJavascript(script) { result ->
                                        if (!shouldExit.get()) safe { scriptCallback?.invoke(result) }
                                    }
                                }
                            }
                        }

                        if (interceptUrl.containsMatchIn(webViewUrl)) {
                            fixedRequest.set(request.toRequest()?.also {
                                safe { requestCallBack(it) }
                            })
                            Log.i(TAG, "Web-view request finished: $webViewUrl")
                            destroyWebView()
                            return@runBlocking null
                        }

                        if (additionalUrls.any { it.containsMatchIn(webViewUrl) }) {
                            request.toRequest()?.also {
                                if (safe { requestCallBack(it) } == true) destroyWebView()
                            }?.let(extraRequestList::add)
                        }

                        // Suppress image requests as we don't display them anywhere
                        // Less data, low chance of causing issues.
                        // blockNetworkImage also does this job but i will keep it for the future.
                        val blacklistedFiles = listOf(
                            ".jpg",
                            ".png",
                            ".webp",
                            ".mpg",
                            ".mpeg",
                            ".jpeg",
                            ".webm",
                            ".mp4",
                            ".mp3",
                            ".gifv",
                            ".flv",
                            ".asf",
                            ".mov",
                            ".mng",
                            ".mkv",
                            ".ogg",
                            ".avi",
                            ".wav",
                            ".woff2",
                            ".woff",
                            ".ttf",
                            ".css",
                            ".vtt",
                            ".srt",
                            ".ts",
                            ".gif",
                            // Warning, this might fuck some future sites, but it's used to make Sflix work.
                            "wss://"
                        )

                        /** NOTE!  request.requestHeaders is not perfect!
                         *  They don't contain all the headers the browser actually gives.
                         *  Overriding with okhttp might fuck up otherwise working requests,
                         *  e.g the recaptcha request.
                         * */
                        return@runBlocking try {
                            when {
                                blacklistedFiles.any { Url(webViewUrl).encodedPath.decodeURLPart().contains(it) } || webViewUrl.endsWith(
                                    "/favicon.ico"
                                ) -> WebResourceResponse(
                                    "image/png",
                                    null,
                                    null
                                )

                                webViewUrl.contains("recaptcha") || webViewUrl.contains("/cdn-cgi/") -> super.shouldInterceptRequest(
                                    view,
                                    request
                                )

                                useOkhttp && request.method == "GET" -> app.get(
                                    webViewUrl,
                                    headers = request.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                useOkhttp && request.method == "POST" -> app.post(
                                    webViewUrl,
                                    headers = request.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                else -> super.shouldInterceptRequest(
                                    view,
                                    request
                                )
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }

                    @SuppressLint("WebViewClientOnReceivedSslError")
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed() // Ignore ssl issues
                    }
                }
                webView?.loadUrl(url, headers.toMap())
            } catch (e: Exception) {
                logError(e)
                destroyWebView()
            }
        }

        try {
            var loop = 0
            val delayTime = 100L
            while (loop < timeout / delayTime && !shouldExit.get()) {
                fixedRequest.get()?.let { return it to extraRequestList }
                delay(delayTime)
                loop += 1
            }

            if (!shouldExit.get()) Log.i(TAG, "Web-view timeout after ${timeout / 1000}s")
            return fixedRequest.get() to extraRequestList
        } finally {
            // Cleanup must finish before releasing the TV concurrency slot, even
            // if a provider/episode job is canceled during the polling delay.
            shouldExit.set(true)
            withContext(NonCancellable) {
                mainWork { destroyOnMain() }
            }
        }
    }
}

fun WebResourceRequest.toRequest(): Request? {
    val webViewUrl = this.url.toString()

    // If invalid url then it can crash with
    // java.lang.IllegalArgumentException: Expected URL scheme 'http' or 'https' but was 'data'
    // At Request.Builder().url(addParamsToUrl(url, params))
    return safe {
        requestCreator(
            this.method,
            webViewUrl,
            this.requestHeaders,
        )
    }
}

fun Response.toWebResourceResponse(): WebResourceResponse {
    val contentTypeValue = this.header("Content-Type")
    // 1. contentType. 2. charset
    val typeRegex = Regex("""(.*);(?:.*charset=(.*)(?:|;)|)""")
    return if (contentTypeValue != null) {
        val found = typeRegex.find(contentTypeValue)
        val contentType = found?.groupValues?.getOrNull(1)?.ifBlank { null } ?: contentTypeValue
        val charset = found?.groupValues?.getOrNull(2)?.ifBlank { null }
        WebResourceResponse(contentType, charset, this.body.byteStream())
    } else {
        WebResourceResponse("application/octet-stream", null, this.body.byteStream())
    }
}
