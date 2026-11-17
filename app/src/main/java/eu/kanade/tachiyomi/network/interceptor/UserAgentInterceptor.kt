package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.HostServices
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Fills in a browser-like User-Agent when a source did not set one itself.
 *
 * Extensions that need a specific agent set it in their `headersBuilder`, so an existing
 * value is always preserved.
 */
class UserAgentInterceptor(
    private val userAgentProvider: () -> String = { HostServices.userAgent },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("User-Agent") != null) return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header("User-Agent", userAgentProvider())
                .build()
        )
    }
}
