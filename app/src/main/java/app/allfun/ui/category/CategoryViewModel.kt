package app.allfun.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.content.CategoryFeed
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.ContentItem
import app.allfun.data.content.ProviderStatus
import app.allfun.data.content.format
import app.allfun.data.content.matchesFormat
import app.allfun.data.content.refId
import app.allfun.data.content.titleKey
import app.allfun.data.db.ContinueRow
import app.allfun.data.db.LibraryRow
import app.allfun.data.model.MediaDetails
import app.allfun.data.net.UserError
import app.allfun.data.net.toUserError
import app.allfun.data.net.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * One ViewModel drives all three category screens. The only differences between them
 * are the category passed in and the filters the UI offers; aggregation, deduping,
 * pagination and error isolation are shared.
 */
class CategoryViewModel(
    val category: ContentCategory,
    private val container: AppContainer
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val feed: CategoryFeed? = null,
        val error: UserError? = null,
        val query: String = "",
        val searching: Boolean = false,
        val searchResults: List<ContentItem> = emptyList(),
        val searchStatuses: List<ProviderStatus> = emptyList(),
        val searchError: UserError? = null,
        val sourceFilter: String? = null,
        val formatFilter: String? = null,
        val genreFilter: String? = null,
        val yearFilter: Int? = null,
        val authorFilter: String? = null,
        val enrichment: Map<String, MediaDetails?> = emptyMap(),
        val enriching: Boolean = false,
        val filtersOpen: Boolean = false,
        val browseAll: Boolean = false,
        val extraItems: List<ContentItem> = emptyList(),
        val loadingMore: Boolean = false,
        val canLoadMore: Boolean = false,
        val page: Int = 0,
        val opening: Boolean = false,
        val loadMoreError: String? = null
    ) {
        val allItems: List<ContentItem>
            get() = feed?.items.orEmpty() + extraItems

        fun detailsFor(item: ContentItem): MediaDetails? = enrichment[refId(item.primary)]

        fun filtered(items: List<ContentItem>): List<ContentItem> = items.mapNotNull { item ->
            val details = detailsFor(item)
            if (sourceFilter != null && item.sources.none { it.sourceId == sourceFilter }) {
                return@mapNotNull null
            }
            if (!item.matchesFormat(formatFilter, details)) return@mapNotNull null
            if (genreFilter != null && details?.genres?.none {
                    it.equals(genreFilter, ignoreCase = true)
                } != false
            ) {
                return@mapNotNull null
            }
            if (yearFilter != null && details?.year != yearFilter) return@mapNotNull null
            if (authorFilter != null) {
                val author = details?.author.orEmpty()
                val artist = details?.artist.orEmpty()
                if (!author.contains(authorFilter, true) && !artist.contains(authorFilter, true)) {
                    return@mapNotNull null
                }
            }
            val preferred = sourceFilter ?: return@mapNotNull item
            item.copy(
                sources = item.sources.sortedByDescending { it.sourceId == preferred }
            )
        }

        val formatOptions: List<String>
            get() = allItems.mapNotNull { item ->
                item.format(detailsFor(item))
            }.distinct().sorted()

        val genreOptions: List<String>
            get() = allItems.flatMap { detailsFor(it)?.genres.orEmpty() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

        val yearOptions: List<Int>
            get() = allItems.mapNotNull { detailsFor(it)?.year }
                .distinct()
                .sortedDescending()

        val authorOptions: List<String>
            get() = allItems.flatMap { item ->
                listOfNotNull(detailsFor(item)?.author, detailsFor(item)?.artist)
            }.filter { it.isNotBlank() }
                .distinct()
                .sorted()

        val hasActiveFilters: Boolean
            get() = sourceFilter != null || formatFilter != null || genreFilter != null ||
                yearFilter != null || authorFilter != null
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val continueRows: StateFlow<List<ContinueRow>> = container.library.observeContinue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryRows: StateFlow<List<LibraryRow>> = container.library.observeLibraryWithProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openMediaId = MutableStateFlow<String?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var feedJob: Job? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0

    private var adoptPartials = true

    init {
        viewModelScope.launch {
            container.content.feedProgress.collect { feeds ->
                if (!adoptPartials) return@collect
                val partial = feeds[category] ?: return@collect
                if (partial.items.isEmpty() && partial.statuses.isEmpty()) return@collect
                _state.update { state ->
                    state.copy(
                        feed = partial,
                        loading = false,
                        canLoadMore = partial.canLoadMore
                    )
                }
            }
        }
        refresh()
    }

    /**
     * Called when the screen comes back to the foreground. Returning from Settings after
     * installing a source must not leave the category on its old, empty provider list, so
     * the enabled source ids are compared and a real refresh only happens when they differ.
     */
    fun onResume() {
        viewModelScope.launch {
            val ids = runCatching { container.content.enabledSources(category) }
                .getOrDefault(emptyList())
                .map { it.id }
                .sorted()
            // The initial load is already running; only a changed provider set triggers work.
            val known = _state.value.feed?.sources?.map { it.id }?.sorted() ?: return@launch
            if (known != ids) refresh()
        }
    }

    fun refresh() {
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            val current = _state.value
            _state.update {
                it.copy(
                    loading = current.feed == null,
                    refreshing = current.feed != null,
                    error = null,
                    loadMoreError = null
                )
            }
            try {
                val feed = container.content.categoryFeed(category, force = true)
                adoptPartials = false
                _state.update {
                    it.copy(
                        feed = feed,
                        loading = false,
                        refreshing = false,
                        page = 0,
                        extraItems = emptyList(),
                        canLoadMore = feed.canLoadMore,
                        error = when {
                            feed.sources.isEmpty() -> null
                            feed.items.isNotEmpty() -> null
                            feed.statuses.isNotEmpty() &&
                                feed.statuses.all { !it.available } -> UserError(
                                "None of your ${category.shortLabel.lowercase()} sources could be reached.",
                                feed.statuses.joinToString("\n") { status ->
                                    "${status.sourceName}: ${status.message.orEmpty()}"
                                }
                            )
                            else -> null
                        }
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    // A cancelled load must never leave the screen on its spinner.
                    _state.update { it.copy(loading = false, refreshing = false) }
                    throw t
                }
                adoptPartials = false
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = t.toUserError(category.label.lowercase())
                    )
                }
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update {
                it.copy(
                    searching = false,
                    searchResults = emptyList(),
                    searchStatuses = emptyList(),
                    searchError = null
                )
            }
            return
        }
        val generation = ++searchGeneration
        searchJob = viewModelScope.launch {
            delay(450)
            if (generation != searchGeneration) return@launch
            _state.update { it.copy(searching = true, searchError = null) }
            try {
                val response = container.content.search(query, category)
                if (generation != searchGeneration) return@launch
                _state.update {
                    it.copy(
                        searching = false,
                        searchResults = response.results[category].orEmpty(),
                        searchStatuses = response.statuses,
                        searchError = null
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (generation != searchGeneration) return@launch
                _state.update {
                    it.copy(
                        searching = false,
                        searchError = t.toUserError("search")
                    )
                }
            }
        }
    }

    fun clearSearch() = setQuery("")

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || !current.canLoadMore) return
        val nextPage = current.page + 1
        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            try {
                val page = container.content.loadMore(category, nextPage)
                _state.update { state ->
                    val merged = LinkedHashMap<String, ContentItem>()
                    (state.extraItems + page.items).forEach { item ->
                        val key = titleKey(item.title).ifBlank { item.primary.key }
                        val existing = merged[key]
                        merged[key] = if (existing == null) {
                            item
                        } else {
                            existing.copy(
                                sources = (existing.sources + item.sources)
                                    .distinctBy { it.sourceId to it.key }
                            )
                        }
                    }
                    state.copy(
                        loadingMore = false,
                        page = nextPage,
                        extraItems = merged.values.toList(),
                        canLoadMore = page.hasMore,
                        loadMoreError = page.statuses
                            .firstOrNull { !it.available }
                            ?.let { status -> "${status.sourceName}: ${status.message.orEmpty()}" }
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(loadingMore = false, loadMoreError = t.userMessage("more results"))
                }
            }
        }
    }

    fun open(item: ContentItem) {
        viewModelScope.launch {
            _state.update { it.copy(opening = true) }
            try {
                val ref = item.primary
                val source = container.sourceHub.sourceFor(ref.sourceId)
                    ?: error("That source is no longer installed")
                val details = container.content.detailsFor(ref).getOrThrow()
                val units = runCatching {
                    withTimeout(20_000) { source.units(ref.key) }
                }.getOrDefault(emptyList())
                val mediaId = container.library.savePreview(
                    sourceId = ref.sourceId,
                    sourceType = source.type,
                    details = details,
                    units = units
                )
                openMediaId.value = mediaId
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _messages.send(t.userMessage("that title"))
            } finally {
                _state.update { it.copy(opening = false) }
            }
        }
    }

    fun clearOpen() {
        openMediaId.value = null
    }

    fun selectSource(sourceId: String?) {
        _state.update { it.copy(sourceFilter = sourceId) }
    }

    fun selectFormat(format: String?) {
        _state.update { it.copy(formatFilter = format) }
    }

    fun selectGenre(genre: String?) {
        _state.update { it.copy(genreFilter = genre) }
    }

    fun selectYear(year: Int?) {
        _state.update { it.copy(yearFilter = year) }
    }

    fun selectAuthor(author: String?) {
        _state.update { it.copy(authorFilter = author) }
    }

    fun clearFilters() {
        _state.update {
            it.copy(
                sourceFilter = null,
                formatFilter = null,
                genreFilter = null,
                yearFilter = null,
                authorFilter = null
            )
        }
    }

    fun openFilters() {
        _state.update { it.copy(filtersOpen = true) }
        prepareFilters()
    }

    fun closeFilters() {
        _state.update { it.copy(filtersOpen = false) }
    }

    /**
     * Details are only fetched when the user actually opens the filters, so simply
     * browsing a category never turns into one metadata request per card.
     */
    private fun prepareFilters() {
        val current = _state.value
        if (current.enriching || current.feed == null) return
        val items = current.allItems
        val missing = items.filter { refId(it.primary) !in current.enrichment }
        if (missing.isEmpty()) return
        _state.update { it.copy(enriching = true) }
        viewModelScope.launch {
            try {
                val values = container.content.enrich(items)
                _state.update { state ->
                    state.copy(
                        enriching = false,
                        enrichment = state.enrichment + values
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(
                        enriching = false,
                        loadMoreError = t.userMessage("filter metadata")
                    )
                }
            }
        }
    }

    fun setBrowseAll(enabled: Boolean) {
        _state.update { it.copy(browseAll = enabled) }
        if (enabled) prepareFilters()
    }
}
