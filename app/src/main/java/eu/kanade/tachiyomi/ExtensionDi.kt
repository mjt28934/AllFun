package eu.kanade.tachiyomi

import android.app.Application
import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.FullTypeReference

/**
 * Mihon extensions resolve a handful of host singletons through Injekt, for example
 * `Injekt.get<Application>()` for shared preferences or `Injekt.get<Json>()` for
 * filter/memo (de)serialization. The container has to be populated before any
 * extension class runs, otherwise those calls throw NoClassDefFoundError /
 * InjektionException inside the extension's static initializer.
 *
 * The keys here are ordinary `java.lang.reflect.Type` values: a `FullTypeReference`
 * subclass resolves its own generic superclass, which is exactly what the extension's
 * compiler-generated reference classes do.
 */
internal object ExtensionDi {

    private object ApplicationRef : FullTypeReference<Application>()
    private object ContextRef : FullTypeReference<Context>()
    private object JsonRef : FullTypeReference<Json>()
    private object OkHttpClientRef : FullTypeReference<OkHttpClient>()

    /** Matches Mihon's application-wide Json: tolerant of unknown keys and nulls. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun install(application: Application, client: OkHttpClient) {
        Injekt.addSingleton(ApplicationRef, application)
        Injekt.addSingleton(ContextRef, application)
        Injekt.addSingleton(JsonRef, json)
        Injekt.addSingleton(OkHttpClientRef, client)
    }
}
