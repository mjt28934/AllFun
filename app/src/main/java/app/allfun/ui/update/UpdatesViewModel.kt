package app.allfun.ui.update

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.BuildConfig
import app.allfun.data.AppContainer
import app.allfun.data.net.toUserError
import app.allfun.data.update.AppVersion
import app.allfun.data.update.DownloadEvent
import app.allfun.data.update.InstallReport
import app.allfun.data.update.InstallStatusStore
import app.allfun.data.update.ReleaseInfo
import app.allfun.data.update.UpdateCheck
import app.allfun.data.update.UpdateRepository
import app.allfun.data.update.UpdateScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Download lifecycle for the release APK, kept separate from the network check state. */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Running(val bytes: Long, val total: Long) : UpdateDownloadState
    data object Verifying : UpdateDownloadState
    data class Ready(val file: File, val versionName: String) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

data class UpdateUiState(
    val installedVersion: String = BuildConfig.VERSION_NAME,
    val installedCode: Long = BuildConfig.VERSION_CODE.toLong(),
    val latest: ReleaseInfo? = null,
    val lastCheckAt: Long? = null,
    val checking: Boolean = false,
    val status: String? = null,
    val checkFailed: Boolean = false,
    val download: UpdateDownloadState = UpdateDownloadState.Idle,
    val installReport: InstallReport? = null,
    val autoCheck: Boolean = true,
    val notify: Boolean = true,
    val canInstallPackages: Boolean = true
) {
    /** The release only counts as an update when its tag is newer than the running build. */
    val available: ReleaseInfo? = latest?.takeIf {
        AppVersion.isNewer(it.versionName, installedVersion)
    }

    val installedIsAhead: Boolean = latest != null &&
        available == null &&
        AppVersion.isNewer(installedVersion, latest.versionName)
}

/**
 * Drives Settings → About → App Updates. Reads the same [UpdateRepository] the background
 * worker uses, so the cache, cooldown and notification bookkeeping are shared rather than
 * duplicated per screen.
 */
class UpdatesViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                _state.update { it.copy(autoCheck = settings.autoCheckUpdates, notify = settings.notifyUpdates) }
            }
        }
        viewModelScope.launch {
            val cached = container.updates.cached()
            val last = container.updates.lastCheckAt()
            _state.update {
                it.copy(
                    latest = cached,
                    lastCheckAt = last,
                    installReport = InstallStatusStore.read(container.appContext),
                    canInstallPackages = canInstallPackages()
                )
            }
            // Opening the screen counts as a check request, but only when the cooldown has
            // elapsed. Otherwise the cached release is shown as-is.
            if (last == null || System.currentTimeMillis() - last >= UpdateRepository.CHECK_INTERVAL_MS) {
                checkForUpdates()
            }
        }
    }

    fun checkForUpdates() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, status = null, checkFailed = false) }
        viewModelScope.launch {
            when (val result = container.updates.check()) {
                is UpdateCheck.Found -> {
                    val release = result.release
                    val newer = AppVersion.isNewer(release.versionName, BuildConfig.VERSION_NAME)
                    _state.update {
                        it.copy(
                            checking = false,
                            latest = release,
                            lastCheckAt = container.updates.lastCheckAt(),
                            status = if (newer) {
                                "Version ${release.versionName} is available."
                            } else if (AppVersion.isNewer(BuildConfig.VERSION_NAME, release.versionName)) {
                                "You are running ${BuildConfig.VERSION_NAME}, which is newer than " +
                                    "the latest published release (${release.versionName})."
                            } else {
                                "You are on the latest release (${release.versionName})."
                            }
                        )
                    }
                }
                is UpdateCheck.NoRelease -> _state.update {
                    it.copy(
                        checking = false,
                        latest = null,
                        lastCheckAt = container.updates.lastCheckAt(),
                        status = "No stable release has been published yet. Releases appear " +
                            "here once the first version tag is published."
                    )
                }
                is UpdateCheck.Failed -> _state.update {
                    it.copy(
                        checking = false,
                        checkFailed = true,
                        status = "Could not reach GitHub. ${result.error.message}"
                    )
                }
            }
        }
    }

    fun downloadUpdate() {
        val release = _state.value.available ?: return
        if (downloadJob?.isActive == true) return

        downloadJob = viewModelScope.launch {
            _state.update { it.copy(download = UpdateDownloadState.Running(0L, release.apkSize)) }
            var finished: File? = null
            try {
                container.updateDownloader.download(release).collect { event ->
                    when (event) {
                        is DownloadEvent.Running -> _state.update {
                            it.copy(download = UpdateDownloadState.Running(event.bytes, event.total))
                        }
                        is DownloadEvent.Completed -> finished = event.file
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(download = UpdateDownloadState.Idle) }
                return@launch
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        download = UpdateDownloadState.Failed(
                            error.message ?: "The download did not finish."
                        )
                    )
                }
                return@launch
            }

            val file = finished
            if (file == null) {
                _state.update {
                    it.copy(download = UpdateDownloadState.Failed("The download produced no file."))
                }
                return@launch
            }

            // The checksum is already verified by the downloader; this pass checks the APK
            // itself (package name, version and signing certificate) before install.
            _state.update { it.copy(download = UpdateDownloadState.Verifying) }
            val verified = withContext(Dispatchers.IO) {
                runCatching { container.apkInspector.verify(file, release.versionName) }
            }
            verified.fold(
                onSuccess = {
                    _state.update {
                        it.copy(download = UpdateDownloadState.Ready(file, release.versionName))
                    }
                    _messages.tryEmit(
                        "Update downloaded and verified. SHA-256 matches the published checksum."
                    )
                },
                onFailure = { error ->
                    file.delete()
                    _state.update {
                        it.copy(
                            download = UpdateDownloadState.Failed(
                                error.message ?: "The downloaded APK could not be verified."
                            )
                        )
                    }
                }
            )
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun dismissDownloadError() {
        _state.update { it.copy(download = UpdateDownloadState.Idle) }
    }

    fun installUpdate() {
        val ready = _state.value.download as? UpdateDownloadState.Ready ?: return
        _state.update { it.copy(canInstallPackages = canInstallPackages()) }

        if (!_state.value.canInstallPackages) {
            _messages.tryEmit(
                "Android requires you to allow installs from AllFun before the update can start."
            )
            return
        }

        viewModelScope.launch {
            val started = withContext(Dispatchers.IO) {
                runCatching { container.apkInstaller.install(ready.file) }
            }
            started.fold(
                onSuccess = {
                    _messages.tryEmit(
                        "Android is asking you to confirm the install. AllFun closes its " +
                            "own screen when the system installer finishes."
                    )
                },
                onFailure = { error ->
                    _messages.tryEmit(
                        "The installer refused to start. " +
                            (error.message ?: "Unknown installer error.")
                    )
                }
            )
        }
    }

    fun dismissInstallReport() {
        InstallStatusStore.clear(container.appContext)
        _state.update { it.copy(installReport = null) }
    }

    fun setAutoCheck(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setAutoCheckUpdates(enabled)
            UpdateScheduler.sync(container.appContext, enabled)
        }
    }

    fun setNotify(enabled: Boolean) = viewModelScope.launch {
        container.settings.setNotifyUpdates(enabled)
    }

    /** Re-read after the user returns from Android's "install unknown apps" screen. */
    fun refreshInstallPermission() {
        _state.update { it.copy(canInstallPackages = canInstallPackages()) }
    }

    fun notificationsAllowed(): Boolean = container.updateNotifier.notificationsAllowed()

    private fun canInstallPackages(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return container.appContext.packageManager.canRequestPackageInstalls()
    }

    companion object {
        /** Deep-link target for the system's per-app "install unknown apps" screen. */
        fun unknownSourcesSettingsIntent(packageName: String) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:$packageName")
                )
            } else {
                null
            }
    }
}
