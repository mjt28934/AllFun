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
import app.allfun.data.content.previewDetails
import app.allfun.data.content.refId
import app.allfun.data.content.titleKey
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

/**
 * Sort orders offered on every category screen. [POPULAR] and [LATEST] use the shelves
 * the providers themselves returned (popular/top catalogues, latest/updated catalogues);
 * a title that appeared in both keeps its strongest shelf. Nothing is re-ranked by a
 * hidden heuristic, and titles missing from those shelves simply follow in source order.
 */
enum class CategorySort(val label: String, val shortLabel: String) {
    POPULAR("Popular", "Popular"),
    LATEST("Latest", "Latest"),
    TITLE_ASC("Title A–Z", "A–Z"),
    SOURCE("By source", "Source");

    val quick: Boolean get() = this == POPULAR || this == LATEST
}

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
        val sort: CategorySort = CategorySort.POPULAR,
        val enrichment: Map<String, MediaDetails?> = emptyMap(),
        val enriching: Boolean = false,
        val filtersOpen: Boolean = false,
        val extraItems: List<ContentItem> = emptyList(),
        val loadingMore: Boolean = false,
        val canLoadMore: Boolean = false,
        val page: Int = 0,
        val loadMoreError: String? = null
    ) {
        val allItems: List<ContentItem>
            get() = feed?.items.orEmpty() + extraItems

        fun detailsFor(item: ContentItem): MediaDetails? = enrichment[refId(item.primary)]

        /** Applies filters, then the user's sort order, keeping provider order otherwise. */
        fun sorted(items: List<ContentItem>): List<ContentItem> = when (sort) {
            CategorySort.POPULAR -> orderedByLane(
                items,
                listOf("POPULAR", "TRENDING", "RECENT", "LATEST", "OTHER")
            )
            CategorySort.LATEST -> orderedByLane(
                items,
                listOf("LATEST", "RECENT", "TRENDING", "POPULAR", "OTHER")
            )
            CategorySort.TITLE_ASC -> items.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            CategorySort.SOURCE -> items.sortedWith(
                compareBy<ContentItem, String>(String.CASE_INSENSITIVE_ORDER) { it.sourceLabel }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
        }

        /**
         * Orders real provider results by the lane they came from. The rank is built once
         * per call from the feed's shelves, so ten titles that were on the "Popular" shelf
         * stay ahead of the ones only found on "Latest".
         */
        private fun orderedByLane(
            items: List<ContentItem>,
            priority: List<String>
        ): List<ContentItem> {
            val shelves = feed?.shelves.orEmpty()
            val rank = HashMap<String, Int>(shelves.size * 8)
            var index = 0
            priority.forEach { lane ->
                shelves.firstOrNull { it.id.endsWith(":$lane") }?.items?.forEach { item ->
                    rank.putIfAbsent(titleKey(item.title), index++)
                }
            }
            if (rank.isEmpty()) return items
            return items.sortedBy { rank[titleKey(it.title)] ?: Int.MAX_VALUE }
        }

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

    val libraryRows: StateFlow<List<LibraryRow>> = container.library.observeLibraryWithProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openMediaId = MutableStateFlow<String?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var feedJob: Job? = null
    private var searchJob: Job? = null
    private var enrichJob: Job? = null
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

    /**
     * Opens a category result the same way Library opens a saved title: the local row is
     * written first, so navigation starts immediately, and the provider's metadata and
     * episode list fill in behind the detail screen. Provider failures are therefore
     * invisible to navigation: even a catalogue addon with no `meta` resource opens.
     */
    fun open(item: ContentItem) {
        viewModelScope.launch {
            val ref = item.primary
            val source = container.sourceHub.sourceFor(ref.sourceId)
            if (source == null) {
                _messages.send("That source is no longer installed.")
                return@launch
            }
            val mediaId = try {
                container.library.savePreview(
                    sourceId = ref.sourceId,
                    sourceType = source.type,
                    details = item.previewDetails(),
                    units = emptyList()
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _messages.send(t.userMessage("that title"))
                return@launch
            }
            openMediaId.value = mediaId

            // The screen is already animating in; enrich it from whatever addon can answer.
            val bundle = runCatching { container.content.detailsWithUnits(ref).getOrThrow() }
                .getOrNull() ?: return@launch
            runCatching {
                container.library.savePreview(
                    sourceId = ref.sourceId,
                    sourceType = source.type,
                    details = bundle.details,
                    units = bundle.units
                )
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

    fun selectSort(sort: CategorySort) {
        _state.update { it.copy(sort = sort) }
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
     * browsing a category never turns into one metadata request per card. Results are
     * published batch by batch: the sheet fills in as real metadata arrives instead of
     * showing one spinner until the slowest provider answers.
     */
    private fun prepareFilters() {
        val current = _state.value
        if (current.feed == null) return
        val missing = current.allItems.filter { refId(it.primary) !in current.enrichment }
        if (missing.isEmpty()) return
        if (enrichJob?.isActive == true) return
        _state.update { it.copy(enriching = true) }
        enrichJob = viewModelScope.launch {
            try {
                missing.chunked(ENRICH_BATCH).forEach { chunk ->
                    val values = container.content.enrich(chunk, limit = chunk.size)
                    _state.update { state ->
                        state.copy(enrichment = state.enrichment + values)
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _messages.send(t.userMessage("filter metadata"))
            } finally {
                _state.update { it.copy(enriching = false) }
            }
        }
    }

    private companion object {
        /** Small enough that the first facets appear in about a second. */
        const val ENRICH_BATCH = 8
    }
}
