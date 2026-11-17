package eu.kanade.tachiyomi.network.interceptor

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Kept for binary compatibility. Mihon no longer ships a default rate limiter, and the
 * deprecation notice asks source developers to implement their own; returning the same
 * builder preserves that behaviour.
 */
@Suppress("UnusedReceiverParameter")
@Deprecated(
    message = "Default rate limiting implementation is no longer provided. Source developers are now " +
        "responsible for implementing their own rate limiting logic if desired",
    replaceWith = ReplaceWith("this"),
)
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
): OkHttpClient.Builder = this
