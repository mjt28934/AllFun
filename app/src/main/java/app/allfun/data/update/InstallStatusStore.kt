package app.allfun.data.update

import android.content.Context
import android.content.pm.PackageInstaller

/** Result of the last PackageInstaller session, written by [InstallResultReceiver]. */
data class InstallReport(
    val success: Boolean,
    val message: String,
    val at: Long
)

object InstallStatusStore {

    fun read(context: Context): InstallReport? {
        val prefs = context.getSharedPreferences(InstallResultReceiver.PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong(InstallResultReceiver.KEY_AT, 0L)
        if (at == 0L) return null
        val status = prefs.getInt(InstallResultReceiver.KEY_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = prefs.getString(InstallResultReceiver.KEY_MESSAGE, null).orEmpty()
        return InstallReport(
            success = status == PackageInstaller.STATUS_SUCCESS,
            message = message,
            at = at
        )
    }

    /** Called once the report has been shown, so it is not repeated forever. */
    fun clear(context: Context) {
        context.getSharedPreferences(InstallResultReceiver.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
