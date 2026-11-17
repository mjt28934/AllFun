package app.allfun.data.update

import app.allfun.data.net.UserError
import app.allfun.data.net.toUserError
import kotlinx.coroutines.CancellationException

/** Outcome of asking GitHub for the latest release. */
sealed interface UpdateCheck {
    data class Found(val release: ReleaseInfo) : UpdateCheck
    data object NoRelease : UpdateCheck
    data class Failed(val error: UserError) : UpdateCheck
}

class UpdateRepository(
    private val api: UpdateApi,
    private val store: UpdateStore
) {

    companion object {
        /** One automatic check per half day is enough for an app that ships weekly. */
        const val CHECK_INTERVAL_MS: Long = 12L * 60 * 60 * 1000
    }

    suspend fun cached(): ReleaseInfo? = store.cached()

    suspend fun lastCheckAt(): Long? = store.lastCheckAt()

    /** Always hits the network; use [checkIfDue] for background work. */
    suspend fun check(now: Long = System.currentTimeMillis()): UpdateCheck = try {
        val release = api.latestRelease()
        store.save(release, now)
        UpdateCheck.Found(release)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (missing: NoReleasePublishedException) {
        store.markChecked(now)
        UpdateCheck.NoRelease
    } catch (error: Exception) {
        // GitHub answers 404 both for "no release yet" and for a missing repository.
        if (error.message?.contains("HTTP 404") == true) {
            store.markChecked(now)
            UpdateCheck.NoRelease
        } else {
            UpdateCheck.Failed(error.toUserError("the update check"))
        }
    }

    /**
     * Runs a check only when the cooldown has elapsed. Launch checks and the periodic
     * worker both go through this, so opening the app repeatedly cannot hammer the API.
     */
    suspend fun checkIfDue(
        now: Long = System.currentTimeMillis(),
        cooldownMs: Long = CHECK_INTERVAL_MS
    ): ReleaseInfo? {
        val last = store.lastCheckAt()
        if (last != null && now - last in 0 until cooldownMs) return null
        return (check(now) as? UpdateCheck.Found)?.release
    }

    /** A release that is newer than [installedVersion], or null. */
    suspend fun newerThan(installedVersion: String): ReleaseInfo? =
        cached()?.takeIf { AppVersion.isNewer(it.versionName, installedVersion) }

    suspend fun notifiedTag(): String? = store.notifiedTag()

    suspend fun setNotifiedTag(tag: String) {
        store.setNotifiedTag(tag)
    }
}
