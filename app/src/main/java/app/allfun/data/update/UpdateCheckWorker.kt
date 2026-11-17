package app.allfun.data.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.allfun.AllFunApp
import app.allfun.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Background update check. The job is periodic (12 hours) and network-constrained, so it
 * never runs offline and never wakes the device just to fail.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AllFunApp ?: return Result.success()
        val container = app.container
        val settings = container.settings.current()
        if (!settings.autoCheckUpdates) return Result.success()

        val release = container.updates.checkIfDue() ?: return Result.success()
        if (!AppVersion.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
            return Result.success()
        }

        if (settings.notifyUpdates && container.updates.notifiedTag() != release.tag) {
            container.updateNotifier.notifyAvailable(release)
            container.updates.setNotifiedTag(release.tag)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "allfun-update-check"
    }
}

object UpdateScheduler {

    private const val INTERVAL_HOURS = 12L

    /** Enqueues or cancels the periodic check to match the user's preference. */
    fun sync(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(UpdateCheckWorker.UNIQUE_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UpdateCheckWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
