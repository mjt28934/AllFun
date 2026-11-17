package eu.kanade.tachiyomi

import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper

/**
 * The small set of host services that extension classes reach for through static or
 * constructor-time access. AllFun installs these once at process start.
 *
 * This object is not part of the published extension ABI; it only exists so the real
 * implementations in this package can find the app's network stack and version data.
 */
object HostServices {

    lateinit var appContext: Context
        internal set

    lateinit var networkHelper: NetworkHelper
        internal set

    var versionCode: Int = 1
    var versionName: String = "0.1"

    val userAgent: String
        get() = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36 AllFun/$versionName"

    fun install(context: Context, versionCode: Int, versionName: String) {
        val app = context.applicationContext
        appContext = app
        this.versionCode = versionCode
        this.versionName = versionName
        networkHelper = NetworkHelper(app)
        // Populate Injekt before any extension class can touch it.
        (app as? android.app.Application)?.let { ExtensionDi.install(it, networkHelper.client) }
    }
}
