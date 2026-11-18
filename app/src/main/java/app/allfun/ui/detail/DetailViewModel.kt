package app.allfun.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentSourceRef
import app.allfun.data.content.SourceOption
import app.allfun.data.content.category
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaEntity
import app.allfun.data.db.ProgressEntity
import app.allfun.data.net.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
 * Loads one library title, refreshes it from its provider, and discovers which other
 * installed providers can serve the same title. A provider is only offered once its own
 * search returned an exact title match, and switching is always an explicit action.
 */
class DetailViewModel(
    private val mediaId: String,
    private val container: AppContainer
) : ViewModel() {

    data class SourceState(
        val loading: Boolean = false,
        val switching: Boolean = false,
        val options: List<SourceOption> = emptyList(),
        val error: String? = null
    )

    val media = container.library.observeMedia(mediaId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val episodes = container.library.observeEpisodes(mediaId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList<EpisodeEntity>()
    )

    val progress = container.progress.observe(mediaId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null as ProgressEntity?
    )

    private val _sources = MutableStateFlow(SourceState())
    val sources: StateFlow<SourceState> = _sources.asStateFlow()

    /** Non-null after the user explicitly picks another confirmed provider. */
    val switchMediaId = MutableStateFlow<String?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var sourcesJob: Job? = null

    init {
        viewModelScope.launch {
            container.library.touchOpened(mediaId)
            refresh()
            findSources()
        }
    }

    /** Pulls fresh metadata and units from the source, silently failing when offline. */
    suspend fun refresh() {
        try {
            val media = container.library.media(mediaId) ?: return
            val source = container.sourceHub.sourceFor(media.sourceId) ?: return
            val details = source.details(media.sourceKey)
            val units = runCatching { source.units(media.sourceKey) }.getOrDefault(emptyList())
            container.library.savePreview(media.sourceId, source.type, details, units)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Keep whatever is cached in the database.
        }
    }

    /**
     * Builds the "Available sources" list. This does not auto-select anything: every
     * option shows its real confirmation state and the user decides.
     */
    fun findSources() {
        if (sourcesJob?.isActive == true) return
        sourcesJob = viewModelScope.launch {
            val item = container.library.media(mediaId) ?: return@launch
            _sources.update { it.copy(loading = true, error = null) }
            try {
                val category = item.kind.category()
                val entity = container.sources.byId(item.sourceId)
                val primary = ContentSourceRef(
                    sourceId = item.sourceId,
                    sourceName = entity?.name ?: "Current source",
                    key = item.sourceKey,
                    type = entity?.type ?: category.sourceType
                )
                val options = container.content.alternateSources(
                    title = item.title,
                    category = category,
                    primary = primary,
                    primaryStatus = primaryStatus(item)
                ).toMutableList()
                if (options.none { it.isPrimary }) {
                    options.add(
                        0,
                        SourceOption(
                            sourceId = item.sourceId,
                            sourceName = primary.sourceName,
                            key = item.sourceKey,
                            category = category,
                            isPrimary = true,
                            isPreferred = false,
                            available = true,
                            statusLabel = primaryStatus(item)
                        )
                    )
                }
                _sources.update { it.copy(loading = false, options = options) }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _sources.update {
                    it.copy(loading = false, error = t.userMessage("available sources"))
                }
            }
        }
    }

    /**
     * Switches the detail page to another provider that already confirmed it has this
     * title. The preview is saved first so the reader/player always has a database id.
     */
    fun useSource(option: SourceOption) {
        val item = media.value ?: return
        if (!option.available) return
        if (option.key == null) return
        viewModelScope.launch {
            if (option.isPrimary) {
                rememberPreferred(item, option.sourceId)
                return@launch
            }
            _sources.update { it.copy(switching = true) }
            try {
                val source = container.sourceHub.sourceFor(option.sourceId)
                    ?: error("That source is no longer installed")
                val ref = ContentSourceRef(
                    sourceId = option.sourceId,
                    sourceName = option.sourceName,
                    key = option.key,
                    type = source.type
                )
                val details = container.content.detailsFor(ref).getOrThrow()
                val units = runCatching {
                    withTimeout(20_000) { source.units(option.key) }
                }.getOrDefault(emptyList())
                val newId = container.library.savePreview(
                    sourceId = option.sourceId,
                    sourceType = source.type,
                    details = details,
                    units = units
                )
                rememberPreferred(item, option.sourceId)
                switchMediaId.value = newId
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _messages.send(t.userMessage("that source"))
            } finally {
                _sources.update { it.copy(switching = false) }
            }
        }
    }

    private suspend fun rememberPreferred(item: MediaEntity, sourceId: String) {
        container.content.rememberPreferredSource(item.title, item.kind.category(), sourceId)
        _sources.update { state ->
            state.copy(
                options = state.options.map { option ->
                    option.copy(isPreferred = option.sourceId == sourceId)
                }
            )
        }
    }

    fun toggleLibrary() {
        viewModelScope.launch {
            val item: MediaEntity = container.library.media(mediaId) ?: return@launch
            container.library.setInLibrary(mediaId, !item.inLibrary)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch { container.library.toggleFavorite(mediaId) }
    }

    fun clearSwitch() {
        switchMediaId.value = null
    }

    private fun primaryStatus(media: MediaEntity): String = buildList {
        media.kind.label.takeIf { media.kind.isVideo }?.let(::add)
        media.year?.let { add(it.toString()) }
        media.status?.takeIf { it.isNotBlank() }?.let(::add)
        media.author?.takeIf { it.isNotBlank() }?.let(::add)
        media.genreList.take(2).joinToString(", ").takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ").ifBlank { "Available" }
}
