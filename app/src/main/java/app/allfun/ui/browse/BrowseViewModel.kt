package app.allfun.ui.browse

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.Ids
import app.allfun.data.db.SourceEntity
import app.allfun.data.model.Catalog
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import app.allfun.data.net.UserError
import app.allfun.data.net.toUserError
import app.allfun.data.net.userMessage
import app.allfun.data.source.SourceIds
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

class BrowseViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val sources: List<SourceEntity> = emptyList(),
        val selectedSourceId: String? = null,
        val selectedSource: SourceEntity? = null,
        val catalogs: List<Catalog> = emptyList(),
        val selectedCatalog: String? = null,
        val results: List<MediaStub> = emptyList(),
        val loading: Boolean = false,
        val pageLoading: Boolean = false,
        val opening: Boolean = false,
        val error: UserError? = null,
        val query: String = "",
        val supportsSearch: Boolean = false,
        val supportsCatalogs: Boolean = false,
        val capabilities: List<String> = emptyList(),
        val page: Int = 0,
        val endReached: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val openMediaId = MutableStateFlow<String?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0

    init {
        viewModelScope.launch {
            container.sources.observeAll().collect { list ->
                val enabled = list.filter { it.enabled }
                val previous = _state.value.selectedSourceId
                val valid = enabled.firstOrNull { it.id == previous }?.id
                    ?: enabled.firstOrNull()?.id
                _state.update { it.copy(sources = enabled) }
                if (valid != previous) selectSource(valid)
                else {
                    _state.update { current ->
                        current.copy(selectedSource = enabled.firstOrNull { it.id == valid })
                    }
                }
            }
        }
    }

    fun selectSource(sourceId: String?) {
        if (sourceId == null) {
            _state.update {
                it.copy(
                    selectedSourceId = null,
                    selectedSource = null,
                    loading = false,
                    results = emptyList(),
                    catalogs = emptyList(),
                    supportsSearch = false,
                    supportsCatalogs = false
                )
            }
            return
        }
        loadJob?.cancel()
        searchJob?.cancel()
        _state.update {
            it.copy(
                selectedSourceId = sourceId,
                loading = true,
                error = null,
                results = emptyList(),
                catalogs = emptyList(),
                selectedCatalog = null,
                capabilities = emptyList(),
                supportsSearch = false,
                supportsCatalogs = false,
                query = "",
                page = 0,
                endReached = false
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val entity = container.sources.byId(sourceId)
                    ?: error("That source is no longer installed")
                val source = container.sourceHub.sourceFor(sourceId)
                    ?: error("That source could not be loaded. Reinstall its extension.")
                val catalogs = if (source.supportsCatalogs) {
                    runCatching { source.catalogs() }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val first = catalogs.firstOrNull()?.id
                _state.update {
                    it.copy(
                        selectedSource = entity,
                        catalogs = catalogs,
                        selectedCatalog = first,
                        supportsSearch = source.supportsSearch,
                        supportsCatalogs = source.supportsCatalogs && catalogs.isNotEmpty(),
                        capabilities = source.capabilityLabels()
                    )
                }
                if (first == null || !source.supportsCatalogs) {
                    _state.update { it.copy(loading = false) }
                    return@launch
                }
                val results = source.browse(first, 0)
                _state.update {
                    it.copy(results = results, loading = false, page = 0, endReached = results.isEmpty())
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                android.util.Log.e("AllFunBrowse", "Could not load $sourceId", t)
                _state.update { it.copy(loading = false, error = t.toUserError("this source")) }
            }
        }
    }

    fun selectCatalog(catalogId: String) {
        loadJob?.cancel()
        searchJob?.cancel()
        _state.update {
            it.copy(
                selectedCatalog = catalogId,
                loading = true,
                error = null,
                query = "",
                results = emptyList(),
                page = 0,
                endReached = false
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val sourceId = _state.value.selectedSourceId
                    ?: error("No source selected")
                val source = container.sourceHub.sourceFor(sourceId)
                    ?: error("That source is no longer available")
                val results = source.browse(catalogId, 0)
                _state.update {
                    it.copy(results = results, loading = false, page = 0, endReached = results.isEmpty())
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update { it.copy(loading = false, error = t.toUserError("this catalog")) }
            }
        }
    }

    /** Appends the next page; stops as soon as a source repeats itself or returns nothing. */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.pageLoading || current.endReached) return
        if (current.query.isNotBlank()) return
        val sourceId = current.selectedSourceId ?: return
        val catalog = current.selectedCatalog ?: return
        if (!current.supportsCatalogs) return
        val nextPage = current.page + 1
        if (nextPage > MAX_PAGES) {
            _state.update { it.copy(endReached = true) }
            return
        }
        _state.update { it.copy(pageLoading = true) }
        viewModelScope.launch {
            try {
                val source = container.sourceHub.sourceFor(sourceId)
                    ?: error("That source is no longer available")
                val results = source.browse(catalog, nextPage)
                val existing = current.results.map { it.key }.toSet()
                val added = results.filterNot { it.key in existing }
                _state.update {
                    it.copy(
                        results = it.results + added,
                        page = nextPage,
                        pageLoading = false,
                        endReached = added.isEmpty()
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(
                        pageLoading = false,
                        endReached = true,
                        error = t.toUserError("more results")
                    )
                }
            }
        }
    }

    fun setQuery(query: String) {
        if (!_state.value.supportsSearch) return
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        val generation = ++searchGeneration
        searchJob = viewModelScope.launch {
            delay(450)
            if (generation == searchGeneration) runSearch(query)
        }
    }

    fun submitSearch() {
        if (!_state.value.supportsSearch) return
        searchJob?.cancel()
        val generation = ++searchGeneration
        viewModelScope.launch {
            if (generation == searchGeneration) runSearch(_state.value.query)
        }
    }

    private suspend fun runSearch(query: String) {
        val sourceId = _state.value.selectedSourceId ?: return
        val source = container.sourceHub.sourceFor(sourceId) ?: return
        if (query.isBlank()) {
            selectCatalog(_state.value.selectedCatalog ?: return)
            return
        }
        _state.update { it.copy(loading = true, error = null, endReached = true) }
        try {
            val results = source.search(query, 0)
            _state.update {
                it.copy(results = results, loading = false, page = 0)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            _state.update { it.copy(loading = false, error = t.toUserError("search")) }
        }
    }

    fun open(stub: MediaStub) {
        val sourceId = _state.value.selectedSourceId ?: return
        viewModelScope.launch {
            _state.update { it.copy(opening = true) }
            try {
                val existingId = Ids.media(sourceId, stub.key)
                val existing = container.library.media(existingId)
                if (existing != null) {
                    openMediaId.value = existing.id
                    return@launch
                }
                val source = container.sourceHub.sourceFor(sourceId)
                    ?: error("That source is no longer available")
                val details = source.details(stub.key)
                val units = runCatching { source.units(stub.key) }.getOrDefault(emptyList())
                val mediaId = container.library.savePreview(sourceId, source.type, details, units)
                openMediaId.value = mediaId
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                android.util.Log.e("AllFunBrowse", "Could not open ${stub.title}", t)
                _state.update { it.copy(error = t.toUserError("that title")) }
            } finally {
                _state.update { it.copy(opening = false) }
            }
        }
    }

    fun clearOpen() {
        openMediaId.value = null
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun retry() {
        val current = _state.value
        if (current.query.isNotBlank() && current.supportsSearch) {
            viewModelScope.launch { runSearch(current.query) }
        } else if (current.selectedCatalog != null) {
            selectCatalog(current.selectedCatalog)
        } else {
            selectSource(current.selectedSourceId)
        }
    }

    fun importEpub(uri: Uri) {
        viewModelScope.launch {
            try {
                val id = container.bookImporter.importUri(uri)
                _messages.send("Book imported")
                selectSource(SourceIds.LOCAL_NOVEL)
                openMediaId.value = id
            } catch (t: Throwable) {
                _messages.send("Import failed: ${t.userMessage("that EPUB")}")
            }
        }
    }

    fun importVideos(uri: Uri) {
        viewModelScope.launch {
            try {
                val summary = container.videoImporter.importTree(uri)
                _messages.send(summary.message)
                selectSource(SourceIds.LOCAL_VIDEO)
            } catch (t: Throwable) {
                _messages.send("Import failed: ${t.userMessage("that folder")}")
            }
        }
    }

    /**
     * Installs a Tachiyomi/Komikku extension APK. The APK itself stays in private
     * storage; the sources it exposes become ordinary rows the Browse screen can use.
     */
    fun importExtension(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(opening = true) }
            try {
                val added = container.sourceHub.addExtension(uri).getOrThrow()
                _messages.send(
                    if (added.size == 1) "Installed ${added.first().name}"
                    else "Installed ${added.size} manga sources"
                )
                selectSource(added.first().id)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _messages.send("Could not install that extension: ${t.userMessage("the extension")}")
            } finally {
                _state.update { it.copy(opening = false) }
            }
        }
    }

    fun importNovelPlugin(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(opening = true) }
            try {
                val added = container.sourceHub.addLnReader(uri).getOrThrow()
                _messages.send("Installed ${added.name}")
                selectSource(added.id)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _messages.send("Could not install that plugin: ${t.userMessage("the plugin")}")
            } finally {
                _state.update { it.copy(opening = false) }
            }
        }
    }

    fun addStremio(url: String) {
        viewModelScope.launch {
            container.sourceHub.addStremio(url)
                .onSuccess {
                    _messages.send("Added ${it.name}")
                    selectSource(it.id)
                }
                .onFailure { _messages.send("Could not add addon: ${it.userMessage("the addon")}") }
        }
    }

    fun addJellyfin(url: String, username: String, password: String) {
        viewModelScope.launch {
            container.sourceHub.addJellyfin(url, username, password)
                .onSuccess {
                    _messages.send("Connected to ${it.name}")
                    selectSource(it.id)
                }
                .onFailure { _messages.send("Jellyfin: ${it.userMessage("the server")}") }
        }
    }

    /** Source type label for the summary line. */
    fun typeLabel(type: SourceType): String = when (type) {
        SourceType.MANGA -> "Manga source"
        SourceType.NOVEL -> "Novel source"
        SourceType.VIDEO -> "Video addon / server"
    }

    private companion object {
        const val MAX_PAGES = 20
    }
}
