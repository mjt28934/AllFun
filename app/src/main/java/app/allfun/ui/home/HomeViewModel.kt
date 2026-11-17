package app.allfun.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.ContinueRow
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import app.allfun.data.net.UserError
import app.allfun.data.net.toUserError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** One source's real catalogue result, used by the Home shelves. */
data class HomeShelf(
    val kind: SourceType,
    val sourceId: String,
    val sourceName: String,
    val items: List<MediaStub>
)

data class HomeRecommendation(
    val sourceId: String,
    val sourceName: String,
    val kind: SourceType,
    val stub: MediaStub
)

data class HomeState(
    val continueReading: List<ContinueRow> = emptyList(),
    val continueWatching: List<ContinueRow> = emptyList(),
    val recentlyAdded: List<MediaEntity> = emptyList(),
    val recentlyOpened: List<MediaEntity> = emptyList(),
    val recentlyUpdated: List<MediaEntity> = emptyList(),
    val recommendations: Map<SourceType, List<HomeRecommendation>> = emptyMap(),
    val libraryCount: Int = 0,
    val shelvesLoading: Boolean = false,
    val shelvesError: UserError? = null
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val shelves = MutableStateFlow<Map<SourceType, List<HomeRecommendation>>>(emptyMap())
    private val shelvesLoading = MutableStateFlow(false)
    private val shelvesError = MutableStateFlow<UserError?>(null)

    /** Non-null while a discovery card is being resolved into a detail row. */
    private val _opening = MutableStateFlow<String?>(null)
    val opening: StateFlow<String?> = _opening.asStateFlow()

    val openMediaId = MutableStateFlow<String?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var shelfJob: Job? = null

    private val base = combine(
        container.library.observeLibrary(),
        container.library.observeContinue(),
        shelves,
        shelvesLoading
    ) { library, continued, recommendationMap, loading ->
        val sortedContinue = continued.sortedByDescending { it.progressUpdatedAt }
        val recentlyUpdated = library
            .filter { it.updatedAt - it.addedAt > 60_000L }
            .sortedByDescending { it.updatedAt }
            .take(12)
        HomeState(
            continueReading = sortedContinue.filter { it.media.kind.isReading },
            continueWatching = sortedContinue.filter { it.media.kind.isVideo },
            recentlyAdded = library.sortedByDescending { it.addedAt }.take(12),
            recentlyOpened = library
                .filter { it.lastOpenedAt != null }
                .sortedByDescending { it.lastOpenedAt }
                .take(12),
            recentlyUpdated = recentlyUpdated,
            recommendations = recommendationMap,
            libraryCount = library.size,
            shelvesLoading = loading
        )
    }

    val state: StateFlow<HomeState> = base
        .combine(shelvesError) { current, error -> current.copy(shelvesError = error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeState(shelvesLoading = true)
        )

    init {
        refresh()
    }

    /**
     * Fetches the first catalogue of installed, enabled sources once per Home session.
     * Each request has its own timeout and failure is per-source: one broken addon must
     * not blank the whole dashboard. Results are not written to the library until the
     * user opens them.
     */
    fun refresh() {
        shelfJob?.cancel()
        shelfJob = viewModelScope.launch {
            shelvesLoading.value = true
            shelvesError.value = null
            try {
                val sources = container.sources.all().filter { it.enabled && !it.builtIn }
                if (sources.isEmpty()) {
                    shelves.value = emptyMap()
                    return@launch
                }
                val fetched = withContext(Dispatchers.IO) {
                    coroutineScope {
                        sources.groupBy { it.type }.mapValues { (_, group) ->
                            group.take(4).map { entity ->
                                async {
                                    runCatching {
                                        withTimeout(15_000) {
                                            val source = container.sourceHub.instance(entity)
                                                ?: return@withTimeout null
                                            if (!source.supportsCatalogs) return@withTimeout null
                                            val catalog = source.catalogs().firstOrNull()?.id
                                                ?: return@withTimeout null
                                            val items = source.browse(catalog, 0)
                                            if (items.isEmpty()) null
                                            else HomeShelf(
                                                kind = entity.type,
                                                sourceId = entity.id,
                                                sourceName = entity.name,
                                                items = items.take(12)
                                            )
                                        }
                                    }.getOrNull()
                                }
                            }.awaitAll().filterNotNull()
                        }
                    }
                }
                shelves.value = interleave(fetched)
                val attempted = fetched.values.flatten().size
                if (attempted > 0 && fetched.values.all { it.isEmpty() }) {
                    shelvesError.value = UserError(
                        "Your sources could not be reached. Check your connection, then try again.",
                        "All ${sources.size} enabled source(s) returned no catalogue."
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                shelvesError.value = t.toUserError("your sources")
            } finally {
                shelvesLoading.value = false
            }
        }
    }

    private fun interleave(
        shelvesByType: Map<SourceType, List<HomeShelf>>
    ): Map<SourceType, List<HomeRecommendation>> = shelvesByType.mapValues { (_, shelves) ->
        val rows = shelves.filter { it.items.isNotEmpty() }
        if (rows.isEmpty()) return@mapValues emptyList()
        val taken = LinkedHashMap<String, HomeRecommendation>()
        val max = rows.maxOf { it.items.size }
        for (index in 0 until max) {
            for (shelf in rows) {
                val item = shelf.items.getOrNull(index) ?: continue
                val key = shelf.sourceId + "|" + item.key
                if (taken.containsKey(key)) continue
                taken[key] = HomeRecommendation(
                    sourceId = shelf.sourceId,
                    sourceName = shelf.sourceName,
                    kind = shelf.kind,
                    stub = item
                )
                if (taken.size >= 30) return@mapValues taken.values.toList()
            }
        }
        taken.values.toList()
    }

    fun open(recommendation: HomeRecommendation) {
        viewModelScope.launch {
            _opening.value = recommendation.sourceId + "|" + recommendation.stub.key
            try {
                val source = container.sourceHub.sourceFor(recommendation.sourceId)
                    ?: error("That source is no longer installed")
                val details = source.details(recommendation.stub.key)
                val units = runCatching { source.units(recommendation.stub.key) }
                    .getOrDefault(emptyList())
                val mediaId = container.library.savePreview(
                    sourceId = recommendation.sourceId,
                    sourceType = source.type,
                    details = details,
                    units = units
                )
                openMediaId.value = mediaId
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                android.util.Log.e("AllFunHome", "Could not open ${recommendation.stub.title}", t)
                _messages.send(t.toUserError("that title").message)
            } finally {
                _opening.value = null
            }
        }
    }

    fun clearOpen() {
        openMediaId.value = null
    }
}
