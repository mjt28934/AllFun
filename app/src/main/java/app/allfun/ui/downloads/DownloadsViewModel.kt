package app.allfun.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.settings.Settings
import app.allfun.data.net.userMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val rows: List<DownloadEntity> = emptyList(),
    val wifiOnly: Boolean = true,
    val concurrent: Int = 2
) {
    val active: List<DownloadEntity>
        get() = rows.filter { it.status.isActive }
            .sortedWith(compareBy({ it.status.ordinal }, { it.createdAt }))
    val completed: List<DownloadEntity>
        get() = rows.filter { it.status == DownloadStatus.COMPLETED }
            .sortedByDescending { it.completedAt ?: it.createdAt }
    val failed: List<DownloadEntity>
        get() = rows.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED }
            .sortedByDescending { it.createdAt }

    /** Bytes recorded for completed downloads; these are the files really on disk. */
    val completedBytes: Long
        get() = completed.sumOf { it.totalBytes.takeIf { size -> size > 0 } ?: it.downloadedBytes }
}

class DownloadsViewModel(private val container: AppContainer) : ViewModel() {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (t: Throwable) {
                _messages.send(t.userMessage("this download"))
            }
        }
    }

    val state: StateFlow<DownloadsUiState> = combine(
        container.downloads.downloads,
        container.settings.settings
    ) { rows, settings ->
        DownloadsUiState(
            rows = rows,
            wifiOnly = settings.downloadWifiOnly,
            concurrent = settings.concurrentDownloads
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadsUiState()
    )

    fun pause(key: String) = launch {
        container.downloads.pause(key)
        _messages.send("Paused. The partial file is kept.")
    }

    fun resume(key: String) = launch {
        container.downloads.resume(key)
        _messages.send("Queued to continue.")
    }

    fun retry(key: String) = launch {
        container.downloads.retry(key)
        _messages.send("Retrying this download.")
    }

    fun cancel(key: String) = launch {
        container.downloads.cancel(key)
        _messages.send("Download cancelled and partial files removed.")
    }

    fun remove(key: String) = launch {
        container.downloads.remove(key)
        _messages.send("Downloaded files removed.")
    }

    fun clearFinished() = launch {
        container.downloads.clearFinished()
        _messages.send("Finished downloads cleared.")
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { container.settings.setDownloadWifiOnly(enabled) }
    }

    fun setConcurrent(value: Int) {
        viewModelScope.launch {
            container.settings.setConcurrentDownloads(value)
            container.downloads.kick()
        }
    }

    fun refresh() {
        viewModelScope.launch { container.downloads.kick() }
    }
}
