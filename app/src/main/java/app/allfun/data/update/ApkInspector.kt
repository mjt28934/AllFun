package app.allfun.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** The installed build, as Android sees it. */
data class InstalledApp(
    val versionCode: Long,
    val versionName: String?,
    val signerSha256: String?
)

class ApkRejectedException(message: String) : Exception(message)

/**
 * Checks a downloaded APK before it is handed to the installer: right package, newer
 * version, and the same signing certificate as the running app. Android would reject a
 * mismatch anyway; failing first produces a sentence instead of an opaque installer error.
 */
class ApkInspector(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    fun installedApp(): InstalledApp {
        val info = packageManager.getPackageInfo(context.packageName, signatureFlags())
        return InstalledApp(
            versionCode = info.versionCodeCompat(),
            versionName = info.versionName,
            signerSha256 = info.signerSha256()
        )
    }

    fun verify(downloaded: File, expectedVersionName: String? = null): InstalledApp {
        if (!downloaded.exists() || downloaded.length() == 0L) {
            throw ApkRejectedException("The downloaded file is missing or empty.")
        }
        val archive = packageManager.getPackageArchiveInfo(downloaded.absolutePath, signatureFlags())
            ?: throw ApkRejectedException("The downloaded file is not a readable APK.")

        if (archive.packageName != context.packageName) {
            throw ApkRejectedException(
                "That APK is for ${archive.packageName}, not ${context.packageName}."
            )
        }

        val installed = installedApp()
        val archiveCode = archive.versionCodeCompat()
        if (archiveCode <= installed.versionCode) {
            throw ApkRejectedException(
                "That APK is version ${archive.versionName} (code $archiveCode), which is not " +
                    "newer than the installed code ${installed.versionCode}."
            )
        }

        val archiveSigner = archive.signerSha256()
            ?: throw ApkRejectedException("The APK carries no signing certificate.")
        val installedSigner = installed.signerSha256
            ?: throw ApkRejectedException("The installed app's signing certificate could not be read.")

        if (!archiveSigner.equals(installedSigner, ignoreCase = true)) {
            throw ApkRejectedException(
                "That APK is signed by a different key than the installed app, so Android " +
                    "would refuse to install it. Download it from the official release page."
            )
        }

        val archiveVersion = archive.versionName
        if (expectedVersionName != null && archiveVersion != null &&
            AppVersion.compare(archiveVersion, expectedVersionName) != 0
        ) {
            throw ApkRejectedException(
                "The APK reports version $archiveVersion but the release says " +
                    "$expectedVersionName."
            )
        }

        return InstalledApp(archiveCode, archiveVersion, archiveSigner)
    }

    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private fun PackageInfo.signerSha256(): String? {
        val raw: Signature? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = signingInfo ?: return null
            val current = info.apkContentsSigners
            (current.ifEmpty { info.signingCertificateHistory }).firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            signatures?.firstOrNull()
        }
        val signature = raw ?: return null

        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
