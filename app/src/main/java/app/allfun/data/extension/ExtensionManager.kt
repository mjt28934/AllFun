package app.allfun.data.extension

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.system.Os
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.io.File

/**
 * Installs and loads Tachiyomi/Mihon/Komikku extension APKs.
 *
 * The APK is copied into private storage and made read-only because Android 14+
 * refuses to load writable dex files. Extension classes are then resolved through a
 * [PathClassLoader] whose parent is the app class loader, so the real
 * `eu.kanade.tachiyomi.*` API implemented by AllFun is what the extension links
 * against (the published `extensions-lib` artifact only contains stubs).
 */
class ExtensionManager(private val context: Context) {

    data class ExtensionInfo(
        val apk: File,
        val packageName: String,
        val versionName: String?,
        val className: String,
        val displayName: String,
        val nsfw: Boolean,
        val libVersion: String?
    )

    class LoadedExtension(
        val info: ExtensionInfo,
        val classLoader: ClassLoader,
        val sources: List<Source>
    )

    private val directory = File(context.filesDir, "extensions")
    private val loaded = HashMap<String, LoadedExtension>()
    @Volatile
    private var cachedInfos: List<ExtensionInfo>? = null

    init {
        directory.mkdirs()
    }

    /** Copies an APK from a content URI, validates it, and replaces any older copy. */
    fun install(uri: Uri, expectedSigningKey: String? = null): Result<ExtensionInfo> = runCatching {
        val temp = File(directory, "incoming-${System.currentTimeMillis()}.apk")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not read that file")
            finishInstall(temp, expectedSigningKey)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /** Installs an APK already present on disk (repository download). */
    fun installFile(file: File, expectedSigningKey: String? = null): Result<ExtensionInfo> = runCatching {
        if (!file.isFile || file.length() == 0L) error("The downloaded extension is empty")
        val temp = File(directory, "incoming-${System.currentTimeMillis()}.apk")
        try {
            file.copyTo(temp, overwrite = true)
            finishInstall(temp, expectedSigningKey)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun finishInstall(temp: File, expectedSigningKey: String?): ExtensionInfo {
        val info = readInfo(temp) ?: error("That file is not a Tachiyomi/Komikku extension")
        if (!expectedSigningKey.isNullOrBlank()) {
            val hashes = signingKeyHashes(temp)
            if (hashes.isEmpty()) {
                error("Could not verify the extension signature")
            }
            if (hashes.none { it.equals(expectedSigningKey.trim(), ignoreCase = true) }) {
                // The repository advertises which certificate it signs with. A mismatch
                // means the APK was replaced or came from a different publisher.
                error("The extension signature does not match the repository")
            }
        }
        val target = File(directory, info.packageName + ".apk")
        if (target.exists() && target.absolutePath != temp.absolutePath) {
            target.setWritable(true)
            target.delete()
        }
        if (target.absolutePath != temp.absolutePath) {
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
        makeReadOnly(target)
        loaded.remove(target.absolutePath)
        cachedInfos = null

        val stored = info.copy(apk = target)
        loaded.remove(stored.apk.absolutePath)
        return stored
    }

    fun installed(): List<ExtensionInfo> {
        cachedInfos?.let { return it }
        val scanned = directory.listFiles { file -> file.isFile && file.name.endsWith(".apk") }
            .orEmpty()
            .mapNotNull { readInfo(it) }
            .sortedBy { it.displayName.lowercase() }
        cachedInfos = scanned
        return scanned
    }

    /**
     * Permissions the extension package declares in its manifest.
     *
     * These are informational only: the APK is not installed with Android's package
     * installer, so the declarations are never granted to AllFun. Extension code runs with
     * the host app's own permissions, and the UI says so next to this list. It is still
     * useful to see what a publisher asked for, and to spot extensions that request things
     * a catalogue reader has no reason to need.
     */
    fun declaredPermissions(apkName: String): List<String> {
        val file = File(directory, apkName)
        if (!file.isFile) return emptyList()
        val archive = context.packageManager
            .getPackageArchiveInfo(file.absolutePath, PackageManager.GET_PERMISSIONS)
            ?: return emptyList()
        return archive.requestedPermissions.orEmpty()
            .map(::permissionLabel)
            .distinct()
    }

    private fun permissionLabel(raw: String): String = when (raw) {
        "android.permission.INTERNET" -> "Internet access"
        "android.permission.ACCESS_NETWORK_STATE" -> "Check network status"
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.MANAGE_EXTERNAL_STORAGE" -> "Device storage"
        "android.permission.VIBRATE" -> "Vibration"
        "android.permission.WAKE_LOCK" -> "Keep the device awake"
        "android.permission.FOREGROUND_SERVICE" -> "Run a foreground service"
        "android.permission.POST_NOTIFICATIONS" -> "Show notifications"
        else -> raw.substringAfterLast('.')
    }

    fun load(info: ExtensionInfo): LoadedExtension {
        loaded[info.apk.absolutePath]?.let { return it }
        val loader = PathClassLoader(info.apk.absolutePath, javaClass.classLoader)
        val clazz = Class.forName(info.className, true, loader)
        val instance = clazz.getDeclaredConstructor().newInstance()
        val sources = when (instance) {
            is SourceFactory -> instance.createSources()
            is Source -> listOf(instance)
            else -> error("${info.className} is not a Tachiyomi source")
        }
        return LoadedExtension(info, loader, sources).also { loaded[info.apk.absolutePath] = it }
    }

    /** Deletes an installed APK and forgets every class loaded from it. */
    fun remove(apkName: String) {
        val file = File(directory, apkName)
        loaded.remove(file.absolutePath)
        file.setWritable(true)
        file.delete()
        cachedInfos = null
    }

    /**
     * Finds one source inside an installed extension. Falls back to the only source in
     * the package when an extension update changed its generated id.
     */
    fun catalogue(apkName: String, sourceId: Long?): CatalogueSource? {
        val info = installed().firstOrNull { it.apk.name == apkName } ?: return null
        val sources = load(info).sources
        val selected = sources.firstOrNull { it.id == sourceId } ?: sources.singleOrNull()
        return selected as? CatalogueSource
    }

    private fun readInfo(file: File): ExtensionInfo? {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA) ?: return null
        val appInfo = archive.applicationInfo ?: return null
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath
        val meta = appInfo.metaData ?: return null
        val className = meta.string("tachiyomi.extension.class") ?: return null
        val displayName = meta.string("tachiyomix.name")
            ?: runCatching { appInfo.loadLabel(pm).toString() }.getOrNull()
                ?.removePrefix("Tachiyomi: ")
                ?.takeIf { it.isNotBlank() }
            ?: archive.packageName

        return ExtensionInfo(
            apk = file,
            packageName = archive.packageName,
            versionName = archive.versionName,
            className = className,
            displayName = displayName,
            nsfw = meta.bool("tachiyomi.extension.nsfw") || meta.bool("tachiyomix.contentWarning"),
            libVersion = meta.string("tachiyomix.extensionLib")
                ?: meta.string("tachiyomi.extension.lib")
        )
    }

    private fun makeReadOnly(file: File) {
        file.setReadOnly()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 0444: read for owner/group/others, no write. Android 14 requires this
            // before a dynamically loaded dex file can be mapped.
            runCatching { Os.chmod(file.absolutePath, 0b100100100) }
        }
    }

    /** SHA-256 of every signing certificate in the APK, lowercase hex. */
    private fun signingKeyHashes(file: File): List<String> {
        val pm = context.packageManager
        val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: return emptyList()
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            archive.signatures?.toList().orEmpty()
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return signatures.map { signature ->
            digest.digest(signature.toByteArray()).joinToString("") { byte ->
                "%02x".format(byte)
            }
        }
    }

    private fun Bundle.string(key: String): String? =
        runCatching { getString(key) }.getOrNull() ?: get(key)?.toString()

    private fun Bundle.bool(key: String): Boolean {
        val value = get(key) ?: return false
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> value.toString() == "1" || value.toString().equals("true", ignoreCase = true)
        }
    }
}
