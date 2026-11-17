package eu.kanade.tachiyomi

/**
 * Host version information exposed to extensions.
 *
 * @since extension-lib 1.3
 */
object AppInfo {
    fun getVersionCode(): Int = HostServices.versionCode

    fun getVersionName(): String = HostServices.versionName
}
