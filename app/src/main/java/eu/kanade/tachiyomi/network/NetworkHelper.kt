package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Network service handed to extensions. One instance is created for the process and
 * reused by every [eu.kanade.tachiyomi.source.online.HttpSource].
 *
 * The default client carries the same marker interceptors Mihon installs
 * ([UncaughtExceptionInterceptor], [UserAgentInterceptor], [CloudflareInterceptor]);
 * modern Keiyoushi sources verify their presence by class name before deriving a client
 * of their own.
 */
class NetworkHelper(context: Context) {

    private val appContext = context.applicationContext

    private val cookieJar = MemoryCookieJar()

    val client: OkHttpClient = buildClient()

    @Deprecated(
        message = "The provided regular client should have cloudflare handling capability",
        replaceWith = ReplaceWith("client"),
    )
    val cloudflareClient: OkHttpClient
        get() = client

    private fun buildClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor())
            .addInterceptor(CloudflareInterceptor(appContext, cookieJar))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
