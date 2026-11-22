package app.allfun.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.download.QueueOrder
import app.allfun.data.net.userMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A row the user paused. Pausing one item parks it as [DownloadStatus.WAITING], while
 * pausing the whole queue leaves every row [DownloadStatus.QUEUED] in its place with the
 * same note, so both states count as paused and both resume the same way.
 */
val DownloadEntity.isPaused: Boolean
    get() = note == "Paused" && (status == DownloadStatus.QUEUED || status == DownloadStatus.WAITING)

/** One title's downloads, the unit Komikku's queue groups by. */
data class DownloadGroup(
    val mediaId: String,
    val title: String,
    val rows: List<DownloadEntity>
) {
    val active: List<DownloadEntity> get() = rows.filter { it.status.isActive }
    val completed: List<DownloadEntity> get() = rows.filter { it.status == DownloadStatus.COMPLETED }
    val failed: List<DownloadEntity> get() = rows.filter {
        it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED
    }

    /**
     * Transfer progress for the header. Only rows that really reported progress count, so
     * a title with one finished chapter and one fresh queue entry does not read as 0%, and
     * a server that sent no length leaves the number out instead of inventing one.
     */
    val progress: Int?
        get() {
            val active = active
            if (active.isEmpty()) {
                return if (rows.isNotEmpty() && completed.size == rows.size) 100 else null
            }
            val measured = active.filter { it.progress in 1..100 }
            return measured.takeIf { it.isNotEmpty() }
                ?.map { it.progress }
                ?.average()
                ?.toInt()
        }
}

data class DownloadsUiState(
    val rows: List<DownloadEntity> = emptyList(),
    val wifiOnly: Boolean = true,
    val concurrent: Int = 2,
    /** True while the user has paused the whole queue, like Komikku's pause action. */
    val paused: Boolean = false
) {
    /** The live queue in its explicit, persisted order. */
    val active: List<DownloadEntity>
        get() = rows.filter { it.status.isActive }
            .sortedWith(compareBy({ it.queueOrder }, { it.createdAt }))

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

    /** Whether the queue can be started or paused from the floating button. */
    val canToggleQueue: Boolean get() = activeCount > 0

    /** Paused rows only; items waiting for Wi-Fi cannot be resumed by a button. */
    val pausedCount: Int get() = rows.count { it.isPaused }

    val groups: List<DownloadGroup>
        get() {
            val grouped = rows.groupBy { it.mediaId }
                .map { (mediaId, items) ->
                    DownloadGroup(
                        mediaId = mediaId,
                        title = items.first().mediaTitle,
                        rows = items.sortedWith(
                            compareBy<DownloadEntity> { statusRank(it.status) }
                                .thenBy { it.queueOrder }
                                .thenBy { it.createdAt }
                        )
                    )
                }
            // Groups follow the position of their first queued item, like Komikku's headers.
            return grouped.sortedWith(
                compareBy<DownloadGroup> {
                    it.active.minOfOrNull { row -> row.queueOrder } ?: Long.MAX_VALUE
                }.thenByDescending { group -> group.rows.maxOf { it.createdAt } }
            )
        }

    private fun statusRank(status: DownloadStatus): Int = when (status) {
        DownloadStatus.QUEUED, DownloadStatus.WAITING, DownloadStatus.RUNNING -> 0
        DownloadStatus.FAILED, DownloadStatus.CANCELED -> 1
        DownloadStatus.COMPLETED -> 2
    }
}

class DownloadsViewModel(private val container: AppContainer) : ViewModel() {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Set when the user asks to open the title a queue group belongs to. */
    val openMediaId = MutableStateFlow<String?>(null)

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
            concurrent = settings.concurrentDownloads,
            paused = settings.downloadQueuePaused
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadsUiState()
    )

    // ------------------------------------------------------------------ queue control

    /** The floating Resume/Pause button: stops or restarts the whole downloader. */
    fun toggleQueue() = launch {
        if (state.value.paused) {
            container.downloads.startAll()
            _messages.send("Downloads resumed.")
        } else {
            container.downloads.pauseAll()
            _messages.send("Queue paused. Partial files are kept.")
        }
    }

    fun sortQueue(order: QueueOrder) = launch {
        container.downloads.sortQueue(order)
        _messages.send("Queue ordered: ${order.label.lowercase()}.")
    }

    fun moveToTop(key: String) = launch { container.downloads.moveToTop(key) }

    fun moveToBottom(key: String) = launch { container.downloads.moveToBottom(key) }

    fun moveGroupToTop(mediaId: String) = launch { container.downloads.moveGroup(mediaId, toTop = true) }

    fun moveGroupToBottom(mediaId: String) = launch { container.downloads.moveGroup(mediaId, toTop = false) }

    /** Komikku's "Cancel all": empties the queue and deletes partial files. */
    fun clearQueue() = launch {
        val cancelled = container.downloads.clearQueue()
        _messages.send(
            when {
                cancelled == 0 -> "Nothing in the queue to cancel."
                cancelled == 1 -> "Cancelled 1 download. Partial files removed."
                else -> "Cancelled $cancelled downloads. Partial files removed."
            }
        )
    }

    fun openMedia(mediaId: String) {
        openMediaId.value = mediaId
    }

    fun clearOpen() {
        openMediaId.value = null
    }

    // ------------------------------------------------------------------ one item

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

    // ------------------------------------------------------------------ one group

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
        // A group resume is also a resume for the queue when the user paused everything.
        if (state.value.paused) container.downloads.startAll()
        val resumable = rows.filter {
            it.isPaused ||
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

    fun setWifiOnly(enabled: Boolean) = launch {
        container.settings.setDownloadWifiOnly(enabled)
        container.downloads.kick()
    }

    fun setConcurrent(value: Int) = launch {
        container.settings.setConcurrentDownloads(value)
        container.downloads.kick()
    }

    fun refresh() {
        viewModelScope.launch { container.downloads.kick() }
    }
}
