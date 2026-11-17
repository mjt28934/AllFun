package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.HostServices
import eu.kanade.tachiyomi.network.MemoryCookieJar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Clears Cloudflare's browser challenge when a site serves one.
 *
 * Some extension sources sit behind Cloudflare and answer plain HTTP requests with a
 * "Just a moment..." interstitial. When that happens the request is replayed inside a
 * short-lived WebView so the challenge script can run; the resulting cookies (including
 * `cf_clearance`) are copied back into the shared jar and the request is retried once.
 *
 * The class name is part of the extension contract: sources check the default client for
 * an interceptor with this simple name before deriving their own client.
 */
class CloudflareInterceptor(
    private val context: Context = HostServices.appContext,
    private val cookieJar: MemoryCookieJar? = null,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!isChallenge(response)) return response

        runCatching {
            runBlocking { solve(request.url.toString()) }
        }
        response.close()

        // Retry once; if the challenge was not solved this simply returns the same page
        // and the source reports an honest HTTP error.
        return chain.proceed(request)
    }

    private fun isChallenge(response: Response): Boolean {
        if (response.code != 403 && response.code != 503) return false
        val server = response.header("Server").orEmpty()
        val mitigated = response.header("cf-mitigated")
        if (!server.contains("cloudflare", ignoreCase = true) && mitigated == null) return false

        val preview = runCatching { response.peekBody(32L * 1024).string() }.getOrDefault("")
        return mitigated.equals("challenge", ignoreCase = true) ||
            preview.contains("Just a moment", ignoreCase = true) ||
            preview.contains("challenge-platform", ignoreCase = true) ||
            preview.contains("cf-challenge", ignoreCase = true) ||
            preview.contains("Attention Required", ignoreCase = true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun solve(url: String): Boolean = withContext(Dispatchers.Main) {
        val webView = WebView(context)
        try {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.userAgentString = HostServices.userAgent

            val finished = CompletableDeferred<Unit>()
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    if (!finished.isCompleted) finished.complete(Unit)
                }
            }

            webView.loadUrl(url)
            val loaded = withTimeoutOrNull(20_000) { finished.await() } != null
            if (loaded) {
                // The challenge script writes its clearance cookie shortly after the page
                // reports completion.
                delay(2_500)
            }

            val header = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                .orEmpty()
            url.toHttpUrlOrNull()?.let { cookieJar?.saveFromHeader(it, header) }
            header.contains("cf_clearance")
        } finally {
            webView.destroy()
        }
    }
}
