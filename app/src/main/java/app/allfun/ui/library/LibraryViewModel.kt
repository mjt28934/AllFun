package app.allfun.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.db.LibraryRow
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.data.settings.LibraryGrid
import app.allfun.data.settings.LibrarySort
import app.allfun.data.settings.LibraryView
import app.allfun.data.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/** The four content groups the Library offers. */
enum class LibraryFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    BOOKS("Books"),
    MANGA("Manga");

    fun matches(media: MediaEntity): Boolean = when (this) {
        ALL -> true
        MOVIES -> media.kind.isVideo
        BOOKS -> media.kind == MediaKind.NOVEL
        MANGA -> media.kind == MediaKind.MANGA
    }
}

/** Real download facts for one library title, derived from the downloads table. */
data class LibraryDownload(
    val completed: Int = 0,
    val active: Int = 0,
    val failed: Int = 0,
    /** Average progress of active rows, 0..100, or null when none is running. */
    val progress: Int? = null,
    val bytes: Long = 0,
    val completedAt: Long? = null,
    val waitingForWifi: Boolean = false
) {
    val total: Int get() = completed + active + failed
    val hasAny: Boolean get() = total > 0
}

data class LibraryUiState(
    val all: List<LibraryRow> = emptyList(),
    val items: List<LibraryRow> = emptyList(),
    val counts: Map<LibraryFilter, Int> = emptyMap(),
    val downloads: Map<String, LibraryDownload> = emptyMap(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    val view: LibraryView = LibraryView.GRID,
    val grid: LibraryGrid = LibraryGrid.COMFORTABLE
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val query = MutableStateFlow("")

    private val settingsFlow = container.settings.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings()
    )

    private val filters = combine(filter, query) { f, q ->
        f to q
    }

    val state: StateFlow<LibraryUiState> = combine(
        container.library.observeLibraryWithProgress(),
        container.downloads.downloads,
        filters,
        settingsFlow
    ) { rows, downloadRows, selection, settings ->
        val (selectedFilter, search) = selection
        val downloads = summarise(downloadRows)
        val counts = LibraryFilter.entries.associateWith { option ->
            rows.count { option.matches(it.media) }
        }
        val needle = search.trim().lowercase(Locale.ROOT)
        val filtered = rows.filter { row ->
            selectedFilter.matches(row.media) &&
                (needle.isBlank() || row.media.matches(needle))
        }.sortedWith(settings.librarySort.comparator())
        LibraryUiState(
            all = rows,
            items = filtered,
            counts = counts,
            downloads = downloads,
            filter = selectedFilter,
            query = search,
            sort = settings.librarySort,
            view = settings.libraryView,
            grid = settings.libraryGrid
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState()
    )

    fun setFilter(value: LibraryFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { container.settings.setLibrarySort(sort) }
    }

    fun setView(view: LibraryView) {
        viewModelScope.launch { container.settings.setLibraryView(view) }
    }

    fun toggleFavorite(mediaId: String) {
        viewModelScope.launch { container.library.toggleFavorite(mediaId) }
    }

    /**
     * Removes every offline copy of one title. The caller confirms first; files are
     * deleted and the rows disappear, but the title stays on the shelf.
     */
    fun removeDownloads(mediaId: String) {
        viewModelScope.launch {
            container.downloads.forMediaOnce(mediaId).forEach { row ->
                container.downloads.remove(row.key)
            }
        }
    }

    private fun summarise(rows: List<DownloadEntity>): Map<String, LibraryDownload> =
        rows.groupBy { it.mediaId }.mapValues { (_, list) ->
            val completed = list.filter { it.status == DownloadStatus.COMPLETED }
            val active = list.filter { it.status.isActive }
            val failed = list.filter { it.status == DownloadStatus.FAILED }
            val percent = active.mapNotNull { row ->
                row.progress.takeIf { it in 0..100 }
            }
            LibraryDownload(
                completed = completed.size,
                active = active.size,
                failed = failed.size,
                progress = if (active.isEmpty()) null
                else percent.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                bytes = completed.sumOf { it.totalBytes.takeIf { size -> size > 0 } ?: it.downloadedBytes },
                completedAt = completed.maxOfOrNull { it.completedAt ?: 0L }?.takeIf { it > 0L },
                waitingForWifi = active.any { it.note == "Waiting for Wi-Fi" }
            )
        }

    private fun MediaEntity.matches(needle: String): Boolean =
        title.lowercase(Locale.ROOT).contains(needle) ||
            author?.lowercase(Locale.ROOT)?.contains(needle) == true ||
            genres.lowercase(Locale.ROOT).contains(needle)

    private fun LibrarySort.comparator(): Comparator<LibraryRow> = when (this) {
        LibrarySort.RECENTLY_UPDATED -> compareByDescending { it.media.updatedAt }
        LibrarySort.RECENTLY_ADDED -> compareByDescending { it.media.addedAt }
        LibrarySort.RECENTLY_OPENED -> compareByDescending { it.media.lastOpenedAt ?: 0L }
        LibrarySort.TITLE -> compareBy { it.media.title.lowercase(Locale.ROOT) }
        LibrarySort.AUTHOR -> compareBy { it.media.author?.lowercase(Locale.ROOT) ?: "\uFFFF" }
    }
}
