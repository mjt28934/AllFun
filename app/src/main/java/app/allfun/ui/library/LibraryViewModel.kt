package app.allfun.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.LibraryRow
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.data.settings.LibrarySort
import app.allfun.data.settings.LibraryView
import app.allfun.data.settings.Settings
import app.allfun.data.source.SourceIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

enum class LibraryFilter(val label: String) {
    ALL("All"),
    MANGA("Manga"),
    NOVELS("Novels"),
    VIDEO("Movies & TV"),
    BOOKS("Books"),
    LOCAL("Local media");

    fun matches(media: MediaEntity): Boolean = when (this) {
        ALL -> true
        MANGA -> media.kind == MediaKind.MANGA
        NOVELS -> media.kind == MediaKind.NOVEL && media.sourceId != SourceIds.LOCAL_NOVEL
        VIDEO -> media.kind.isVideo && media.sourceId != SourceIds.LOCAL_VIDEO
        BOOKS -> media.kind == MediaKind.NOVEL && media.sourceId == SourceIds.LOCAL_NOVEL
        LOCAL -> media.sourceId == SourceIds.LOCAL_VIDEO
    }
}

data class LibraryUiState(
    val all: List<LibraryRow> = emptyList(),
    val items: List<LibraryRow> = emptyList(),
    val counts: Map<LibraryFilter, Int> = emptyMap(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    val view: LibraryView = LibraryView.GRID
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val query = MutableStateFlow("")
    private val favoritesOnly = MutableStateFlow(false)

    private val settingsFlow = container.settings.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings()
    )

    private val triple = combine(filter, query, favoritesOnly) { f, q, fav ->
        Triple(f, q, fav)
    }

    val state: StateFlow<LibraryUiState> = combine(
        container.library.observeLibraryWithProgress(),
        triple,
        settingsFlow
    ) { rows, filters, settings ->
        val (selectedFilter, search, onlyFavorites) = filters
        val counts = LibraryFilter.entries.associateWith { option ->
            rows.count { option.matches(it.media) }
        }
        val needle = search.trim().lowercase(Locale.ROOT)
        val filtered = rows.filter { row ->
            selectedFilter.matches(row.media) &&
                (!onlyFavorites || row.media.favorite) &&
                (needle.isBlank() || row.media.matches(needle))
        }.sortedWith(settings.librarySort.comparator())
        LibraryUiState(
            all = rows,
            items = filtered,
            counts = counts,
            filter = selectedFilter,
            query = search,
            favoritesOnly = onlyFavorites,
            sort = settings.librarySort,
            view = settings.libraryView
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

    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
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
