package app.allfun.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.net.userMessage
import app.allfun.data.settings.Settings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Sort order for the grouped download queue. */
enum class DownloadSort(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    TITLE("Title A–Z"),
    PROGRESS("Progress")
}

/** One title's downloads, the unit Komikku's queue groups by. */
data class DownloadGroup(
    val title: String,
    val rows: List<DownloadEntity>
) {
    val active: List<DownloadEntity> get() = rows.filter { it.status.isActive }
    val completed: List<DownloadEntity> get() = rows.filter { it.status == DownloadStatus.COMPLETED }
    val failed: List<DownloadEntity> get() = rows.filter {
        it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED
    }

    /** Average progress of the unfinished rows, or 100 when the whole group is saved. */
    val progress: Int?
        get() {
            val running = active
            if (running.isEmpty()) {
                return if (rows.isNotEmpty() && completed.size == rows.size) 100 else null
            }
            return running.map { if (it.progress in 0..100) it.progress else 0 }
                .average()
                .toInt()
        }
}

data class DownloadsUiState(
    val rows: List<DownloadEntity> = emptyList(),
    val wifiOnly: Boolean = true,
    val concurrent: Int = 2,
    val sort: DownloadSort = DownloadSort.NEWEST
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

    /** The number shown in the queue title. */
    val activeCount: Int get() = rows.count { it.status.isActive }

    /** Paused rows only; items waiting for Wi-Fi cannot be resumed by a button. */
    val pausedCount: Int
        get() = rows.count { it.status == DownloadStatus.WAITING && it.note == "Paused" }

    val groups: List<DownloadGroup>
        get() {
            val grouped = rows.groupBy { it.mediaTitle }
                .map { (title, items) ->
                    DownloadGroup(title, items.sortedBy { it.createdAt })
                }
            return when (sort) {
                DownloadSort.NEWEST ->
                    grouped.sortedByDescending { group -> group.rows.maxOf { it.createdAt } }
                DownloadSort.OLDEST ->
                    grouped.sortedBy { group -> group.rows.minOf { it.createdAt } }
                DownloadSort.TITLE ->
                    grouped.sortedBy { it.title.lowercase() }
                DownloadSort.PROGRESS ->
                    grouped.sortedWith(
                        compareByDescending<DownloadGroup> { it.progress ?: -1 }
                            .thenBy { it.title.lowercase() }
                    )
            }
        }
}

class DownloadsViewModel(private val container: AppContainer) : ViewModel() {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val sort = MutableStateFlow(DownloadSort.NEWEST)

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
        container.settings.settings,
        sort
    ) { rows, settings, order ->
        DownloadsUiState(
            rows = rows,
            wifiOnly = settings.downloadWifiOnly,
            concurrent = settings.concurrentDownloads,
            sort = order
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadsUiState()
    )

    fun setSort(value: DownloadSort) {
        sort.value = value
    }

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

    fun pauseAll(rows: List<DownloadEntity>) = launch {
        val active = rows.filter { it.status.isActive }
        if (active.isEmpty()) {
            _messages.send("Nothing in that group is running.")
            return@launch
        }
        active.forEach { container.downloads.pause(it.key) }
        _messages.send("Paused ${active.size} ${if (active.size == 1) "download" else "downloads"}.")
    }

    fun resumeAll(rows: List<DownloadEntity>) = launch {
        val resumable = rows.filter {
            it.status == DownloadStatus.WAITING ||
                it.status == DownloadStatus.FAILED ||
                it.status == DownloadStatus.CANCELED
        }
        if (resumable.isEmpty()) {
            _messages.send("Nothing in that group is paused.")
            return@launch
        }
        resumable.forEach { container.downloads.resume(it.key) }
        _messages.send("Queued ${resumable.size} ${if (resumable.size == 1) "download" else "downloads"}.")
    }

    fun cancelAll(rows: List<DownloadEntity>) = launch {
        val active = rows.filter { it.status.isActive }
        if (active.isEmpty()) {
            _messages.send("Nothing in that group is active.")
            return@launch
        }
        active.forEach { container.downloads.cancel(it.key) }
        _messages.send("Cancelled ${active.size} ${if (active.size == 1) "download" else "downloads"}.")
    }

    /** The floating Resume button: only real pauses, never Wi-Fi-blocked rows. */
    fun resumePaused() = launch {
        val paused = state.value.rows.filter {
            it.status == DownloadStatus.WAITING && it.note == "Paused"
        }
        if (paused.isEmpty()) {
            _messages.send("Nothing is paused.")
            return@launch
        }
        paused.forEach { container.downloads.resume(it.key) }
        _messages.send("Resumed ${paused.size} ${if (paused.size == 1) "download" else "downloads"}.")
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
