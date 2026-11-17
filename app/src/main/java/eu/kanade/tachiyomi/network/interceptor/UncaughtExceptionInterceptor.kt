package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Marker interceptor installed in the default client.
 *
 * Mihon ships this as a guard around the interceptor chain so exceptions raised by a
 * source can be distinguished from network failures. AllFun does not need a separate
 * behaviour here, but extensions check for this class by simple name before deriving
 * their own client, so it must be present in [eu.kanade.tachiyomi.network.NetworkHelper].
 */
class UncaughtExceptionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
