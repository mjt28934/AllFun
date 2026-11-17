package app.allfun.data.extension

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import app.allfun.data.net.Http
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs LNReader plugins in an offscreen WebView.
 *
 * LNReader plugins are CommonJS bundles built for a browser-like runtime: they need
 * `cheerio`, a `fetch` implementation and a little key/value storage. The WebView
 * supplies the JS engine and DOM-free globals; this class supplies the network and
 * storage, and exposes a small call bridge so Kotlin can drive `popularNovels`,
 * `searchNovels`, `parseNovel` and `parseChapter`.
 */
class NovelPluginHost(context: Context, private val http: Http) {

    private val appContext = context.applicationContext
    private val storage: SharedPreferences =
        appContext.getSharedPreferences("lnreader_plugin_storage", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var webView: WebView? = null
    private var ready = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val callIds = AtomicLong(0)

    @Volatile
    var loadedPluginId: String? = null
        private set

    private val bridge = Bridge()

    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            if (!ready.isCompleted) ready.complete(Unit)
        }

        @JavascriptInterface
        fun onResult(callId: String, payload: String) {
            pending.remove(callId)?.complete(payload)
        }

        @JavascriptInterface
        fun onError(callId: String, message: String) {
            pending.remove(callId)?.completeExceptionally(IllegalStateException(message))
        }

        @JavascriptInterface
        fun fetch(requestId: String, url: String, optionsJson: String) {
            scope.launch {
                val result = runCatching {
                    val options = runCatching {
                        json.parseToJsonElement(optionsJson).jsonObject
                    }.getOrNull()
                    val method = options.strOrNull("method") ?: "GET"
                    val headers = options?.get("headers")?.let { element ->
                        runCatching {
                            element.jsonObject.mapNotNull { (key, value) ->
                                (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
                            }.toMap()
                        }.getOrDefault(emptyMap())
                    }.orEmpty()
                        // OkHttp adds its own Accept-Encoding and transparently decodes
                        // the response. A caller-set value would disable that and hand
                        // compressed bytes to the plugin.
                        .filterKeys { !it.equals("accept-encoding", ignoreCase = true) }
                    val body = options.strOrNull("body")
                    val binary = (options?.get("binary") as? JsonPrimitive)
                        ?.contentOrNull?.toBooleanStrictOrNull() ?: false
                    http.request(url, method, headers, body, binary)
                }
                val payload = result.fold(
                    onSuccess = { response ->
                        buildJsonObject {
                            put("status", response.status)
                            put("statusText", response.statusText)
                            put("url", response.url)
                            put("body", response.body)
                            response.bytes?.let { bytes ->
                                put("bodyBase64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                            }
                            put(
                                "headers",
                                JsonObject(
                                    response.headers.entries.associate { (key, value) ->
                                        key.lowercase() to JsonPrimitive(value)
                                    }
                                )
                            )
                        }.toString()
                    },
                    onFailure = { error ->
                        buildJsonObject {
                            put("error", error.message ?: error.javaClass.simpleName)
                        }.toString()
                    }
                )
                withContext(Dispatchers.Main) {
                    webView?.evaluateJavascript(
                        "__allfunFetchResolve(${JSONObject.quote(requestId)}, ${JSONObject.quote(payload)})",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun storageGet(key: String): String? = storage.getString(key, null)

        @JavascriptInterface
        fun storageSet(key: String, value: String?) {
            storage.edit().apply {
                if (value == null) remove(key) else putString(key, value)
            }.apply()
        }
    }

    private fun JsonObject?.strOrNull(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.contentOrNull

    private suspend fun ensureReady() {
        if (ready.isCompleted && webView != null) return
        withContext(Dispatchers.Main) {
            if (webView == null) {
                ready = CompletableDeferred()
                webView = createWebView()
            }
        }
        withTimeout(30_000) { ready.await() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val view = WebView(appContext)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.allowFileAccess = true
        view.settings.allowContentAccess = true
        view.settings.blockNetworkImage = false
        view.settings.loadsImagesAutomatically = false
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                // Plugin failures surface here first; without this they are invisible.
                Log.d(TAG, "console: ${message.message()} (${message.lineNumber()})")
                return true
            }
        }
        view.addJavascriptInterface(bridge, "AllFunBridge")
        view.loadUrl("file:///android_asset/lnreader/host.html")
        return view
    }

    /** Loads a plugin bundle and returns its metadata (`id`, `name`, `site`, `version`). */
    suspend fun loadPlugin(source: String): JSONObject {
        ensureReady()
        Log.d(TAG, "loadPlugin: ${source.length} chars")
        val info = evaluate(
            "__allfunLoadPlugin(${JSONObject.quote(source)})"
        )
        loadedPluginId = info.optString("id").takeIf { it.isNotBlank() }
        Log.d(TAG, "loadPlugin -> id=$loadedPluginId name=${info.optString("name")}")
        return info
    }

    suspend fun call(method: String, argsJson: String = "[]"): String {
        ensureReady()
        Log.d(TAG, "call $method $argsJson")
        val callId = "c" + callIds.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        pending[callId] = deferred
        val script = buildString {
            append("__allfunCall(")
            append(JSONObject.quote(method))
            append(", ")
            append(argsJson)
            append(").then(function(r){AllFunBridge.onResult(")
            append(JSONObject.quote(callId))
            append(", JSON.stringify(r === undefined ? null : r));})")
            append(".catch(function(e){AllFunBridge.onError(")
            append(JSONObject.quote(callId))
            append(", String((e && (e.stack || e.message)) || e));})")
        }
        withContext(Dispatchers.Main) {
            webView?.evaluateJavascript(script, null)
        }
        val payload = withTimeout(60_000) { deferred.await() }
        Log.d(TAG, "call $method -> ${payload.take(160)}")
        return payload
    }

    /** Runs a tiny synchronous expression and parses its JSON result. */
    private suspend fun evaluate(expression: String): JSONObject {
        val callId = "e" + callIds.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        pending[callId] = deferred
        val script = buildString {
            append("(function(){try{AllFunBridge.onResult(")
            append(JSONObject.quote(callId))
            append(", JSON.stringify(")
            append(expression)
            append("));}catch(e){AllFunBridge.onError(")
            append(JSONObject.quote(callId))
            append(", String((e && (e.stack || e.message)) || e));}})()")
        }
        withContext(Dispatchers.Main) {
            webView?.evaluateJavascript(script, null)
        }
        val payload = withTimeout(30_000) { deferred.await() }
        return JSONObject(payload.ifBlank { "{}" })
    }

    fun toJsonArray(raw: String): JSONArray = JSONArray(raw.ifBlank { "[]" })

    fun toJsonObject(raw: String): JSONObject = JSONObject(raw.ifBlank { "{}" })

    fun close() {
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
    }

    private companion object {
        const val TAG = "AllFunNovel"
    }
}
