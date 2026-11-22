package app.allfun.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.ContentItem
import app.allfun.data.content.ProviderStatus
import app.allfun.data.content.previewDetails
import app.allfun.data.net.UserError
import app.allfun.data.net.toUserError
import app.allfun.data.net.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Global search across every compatible installed source. Results are grouped by the
 * three categories and deduplicated per category; each card keeps the provider name.
 */
class GlobalSearchViewModel(
    private val container: AppContainer,
    initialCategory: ContentCategory? = null
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val categoryFilter: ContentCategory? = null,
        val searching: Boolean = false,
        val results: Map<ContentCategory, List<ContentItem>> = emptyMap(),
        val statuses: List<ProviderStatus> = emptyList(),
        val sourceFilter: String? = null,
        val error: UserError? = null,
        val searched: Boolean = false,
        val history: List<String> = emptyList(),
        /** Enabled sources known before any search has run, so the idle state is honest. */
        val knownSources: Int = 0
    ) {
        val total: Int get() = results.values.sumOf { it.size }

        fun visibleResults(): Map<ContentCategory, List<ContentItem>> {
            val source = sourceFilter
            return if (source == null) results else results.mapValues { (_, items) ->
                items.filter { item -> item.sources.any { it.sourceId == source } }
                    .map { item ->
                        item.copy(
                            sources = item.sources.sortedByDescending { it.sourceId == source }
                        )
                    }
            }
        }

        val availableSources: List<Pair<String, String>>
            get() = statuses
                .filter { it.available }
                .distinctBy { it.sourceId }
                .map { it.sourceId to it.sourceName }
                .sortedBy { it.second.lowercase() }
    }

    private val _state = MutableStateFlow(UiState(categoryFilter = initialCategory))
    val state: StateFlow<UiState> = _state.asStateFlow()

    val openMediaId = MutableStateFlow<String?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var searchJob: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                if (settings.searchHistory != _state.value.history) {
                    _state.update { it.copy(history = settings.searchHistory) }
                }
            }
        }
        viewModelScope.launch {
            val count = runCatching { container.content.enabledSources().size }
                .getOrDefault(0)
            _state.update { it.copy(knownSources = count) }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        scheduleSearch()
    }

    /**
     * Commits the current query: it is stored in recent searches and searched without the
     * typing debounce. Debounced searches are not recorded, so history never fills with
     * every prefix the user typed.
     */
    fun submit() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        viewModelScope.launch { container.settings.addSearchTerm(query) }
        scheduleSearch(immediate = true)
    }

    fun useHistory(term: String) {
        _state.update { it.copy(query = term) }
        viewModelScope.launch { container.settings.addSearchTerm(term) }
        scheduleSearch(immediate = true)
    }

    fun removeHistory(term: String) {
        viewModelScope.launch { container.settings.removeSearchTerm(term) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.settings.clearSearchHistory() }
    }

    fun setCategory(category: ContentCategory?) {
        _state.update { it.copy(categoryFilter = category, sourceFilter = null) }
        if (_state.value.query.isNotBlank()) scheduleSearch(immediate = true)
    }

    fun setSource(sourceId: String?) {
        _state.update { it.copy(sourceFilter = sourceId) }
    }

    fun retry() {
        scheduleSearch(immediate = true)
    }

    private fun scheduleSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        val query = _state.value.query
        if (query.isBlank()) {
            _state.update {
                it.copy(
                    searching = false,
                    results = emptyMap(),
                    statuses = emptyList(),
                    error = null,
                    searched = false
                )
            }
            return
        }
        val current = ++generation
        searchJob = viewModelScope.launch {
            if (!immediate) delay(450)
            if (current != generation) return@launch
            _state.update { it.copy(searching = true, error = null) }
            try {
                val response = container.content.search(query, _state.value.categoryFilter)
                if (current != generation) return@launch
                _state.update {
                    it.copy(
                        searching = false,
                        results = response.results.filterValues { list -> list.isNotEmpty() },
                        statuses = response.statuses,
                        searched = true,
                        error = null
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (current != generation) return@launch
                _state.update {
                    it.copy(
                        searching = false,
                        searched = true,
                        error = t.toUserError("search")
                    )
                }
            }
        }
    }

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
}
