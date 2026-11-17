package app.allfun.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/**
 * Hands an APK to Android's own installer through a `PackageInstaller` session. The
 * session commit opens the system confirmation dialog; AllFun never installs anything
 * silently and never needs "install unknown apps" to be pre-granted.
 */
class ApkInstaller(private val context: Context) {

    /** @return the installer session id, useful only for logging. */
    fun install(apk: File): Int {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(context.packageName)
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                session.fsync(output)
            }

            val statusIntent = Intent(context, InstallResultReceiver::class.java)
                .setAction(InstallResultReceiver.ACTION_INSTALL_STATUS)

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)

            val callback = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
            session.commit(callback.intentSender)
        }
        return sessionId
    }
}

/** Records how the system installer finished so the app can report it honestly. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_STATUS, status)
            .putString(KEY_MESSAGE, message)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "app.allfun.install.STATUS"
        const val PREFS = "update_install"
        const val KEY_STATUS = "status"
        const val KEY_MESSAGE = "message"
        const val KEY_AT = "at"
    }
}
