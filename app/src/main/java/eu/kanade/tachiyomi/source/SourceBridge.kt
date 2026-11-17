package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runtime glue between the two API shapes an extension may use.
 *
 * Mihon 1.6 extensions can override either the suspend API (`getPopularManga`, ...) or
 * the legacy Rx API (`fetchPopularManga`, ...). The host always calls the suspend API,
 * so we detect which one the concrete extension class actually implements.
 */
internal object SourceBridge {

    fun overrides(target: Any, name: String, vararg parameterTypes: Class<*>): Boolean {
        val method = try {
            target.javaClass.getMethod(name, *parameterTypes)
        } catch (e: NoSuchMethodException) {
            return false
        }
        return when (method.declaringClass) {
            Source::class.java,
            CatalogueSource::class.java,
            HttpSource::class.java,
            ParsedHttpSource::class.java -> false
            else -> true
        }
    }
}

internal suspend fun <T> Observable<T>.awaitSingleValue(): T = suspendCancellableCoroutine { cont ->
    val subscription = subscribe(
        { value -> if (cont.isActive) cont.resume(value) },
        { error -> if (cont.isActive) cont.resumeWithException(error) },
        {
            if (cont.isActive) {
                cont.resumeWithException(NoSuchElementException("Observable completed without a value"))
            }
        },
    )
    cont.invokeOnCancellation { subscription.unsubscribe() }
}
