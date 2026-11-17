package eu.kanade.tachiyomi.network

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Evaluates JavaScript for sources that need to derive a token or solve a simple
 * challenge. A short-lived offscreen WebView is used so the extension does not need a
 * browser of its own.
 */
class JavaScriptEngine(context: Context) {

    private val appContext = context.applicationContext

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val webView = try {
                WebView(appContext)
            } catch (t: Throwable) {
                continuation.resumeWithException(t)
                return@suspendCancellableCoroutine
            }
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            var settled = false
            webView.evaluateJavascript(script) { raw ->
                if (settled) return@evaluateJavascript
                settled = true
                val value = runCatching { parse(raw) }.getOrElse { t ->
                    if (continuation.isActive) continuation.resumeWithException(t)
                    webView.destroy()
                    return@evaluateJavascript
                }
                webView.destroy()
                @Suppress("UNCHECKED_CAST")
                if (continuation.isActive) continuation.resume(value as T)
            }
        }
    }

    private fun parse(raw: String?): Any? {
        if (raw == null || raw == "null" || raw == "undefined") return null
        return when {
            raw.startsWith("\"") -> JSONObject("{\"v\":$raw}").getString("v")
            raw == "true" -> true
            raw == "false" -> false
            raw.startsWith("[") -> JSONArray(raw)
            raw.startsWith("{") -> JSONObject(raw)
            raw.contains('.') -> raw.toDouble()
            else -> raw.toLongOrNull() ?: raw
        }
    }
}
