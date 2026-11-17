package app.allfun.data.net

import eu.kanade.tachiyomi.HostServices
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Extension sources frequently require a Referer or a site-specific User-Agent on image
 * requests. Coil does not know which source an image came from, so the source registers
 * its headers by host while it is producing results, and the Coil interceptor replays
 * them for matching URLs.
 */
object ImageHeaders {

    private val byHost = ConcurrentHashMap<String, Map<String, String>>()

    fun register(url: String?, headers: Headers) {
        if (headers.size == 0) return
        val host = url?.toHttpUrlOrNull()?.host ?: return
        val values = headers.toMultimap()
            .mapValues { (_, list) -> list.firstOrNull().orEmpty() }
            .filterValues { it.isNotEmpty() }
        if (values.isNotEmpty()) byHost[host] = values
    }

    fun forUrl(url: String): Map<String, String> =
        url.toHttpUrlOrNull()?.host?.let { byHost[it] } ?: emptyMap()
}

class ImageHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val extra = ImageHeaders.forUrl(request.url.toString())
        val builder = request.newBuilder()
        extra.forEach { (name, value) -> builder.header(name, value) }

        val hasUserAgent = extra.keys.any { it.equals("User-Agent", ignoreCase = true) } ||
            request.header("User-Agent") != null
        if (!hasUserAgent) builder.header("User-Agent", HostServices.userAgent)

        val hasReferer = extra.keys.any { it.equals("Referer", ignoreCase = true) } ||
            request.header("Referer") != null
        if (!hasReferer) {
            builder.header("Referer", "${request.url.scheme}://${request.url.host}/")
        }

        return chain.proceed(builder.build())
    }
}
