package app.allfun.data.content

import app.allfun.data.db.SourceDao
import app.allfun.data.db.SourceEntity
import app.allfun.data.model.Catalog
import app.allfun.data.model.MediaDetails
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import app.allfun.data.net.userMessage
import app.allfun.data.settings.SettingsStore
import app.allfun.data.source.MediaSource
import app.allfun.data.source.SourceHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Aggregates every enabled source into the three entertainment categories.
 *
 * Design rules:
 *  - A provider is never a destination. Results are always merged into a category.
 *  - Only responses a provider actually returned become content; there is no fallback
 *    catalogue and no placeholder data.
 *  - One source failing cannot blank a category: every request has its own timeout and
 *    its failure is recorded as a [ProviderStatus] while other sources keep rendering.
 *  - Requests are cached in memory with a short TTL so switching tabs does not re-hit
 *    every provider, and pagination fetches more from the same providers.
 */
class ContentRepository(
    private val sourceHub: SourceHub,
    private val sourceDao: SourceDao,
    private val settings: SettingsStore
) {

    private data class Timed<T>(val value: T, val at: Long)

    private val feedCache = ConcurrentHashMap<ContentCategory, Timed<CategoryFeed>>()
    private val searchCache = ConcurrentHashMap<String, Timed<SearchResponse>>()
    private val detailsCache = ConcurrentHashMap<String, Timed<MediaDetails>>()

    private val partialFeeds = MutableStateFlow<Map<ContentCategory, CategoryFeed>>(emptyMap())

    /**
     * A category feed that is still being assembled. Installing one extension can expose
     * dozens of Tachiyomi sources, so a feed must render as responses arrive instead of
     * waiting for the slowest provider. Each emission contains only real results already
     * returned by a provider.
     */
    val feedProgress: StateFlow<Map<ContentCategory, CategoryFeed>> = partialFeeds.asStateFlow()

    private data class SourceResult(
        val status: ProviderStatus,
        val lanes: Map<ContentLane, List<ContentItem>>
    )

    private enum class ContentLane { POPULAR, TRENDING, LATEST, RECENT, OTHER }

    suspend fun enabledSources(category: ContentCategory): List<SourceEntity> =
        sourceDao.all().filter { it.enabled && it.type == category.sourceType }

    /** Every enabled source, for screens that need a count before a search has run. */
    suspend fun enabledSources(): List<SourceEntity> =
        sourceDao.all().filter { it.enabled }

    /**
     * Builds the category feed from every enabled source. [force] bypasses the cache and
     * is used by the explicit refresh control.
     */
    suspend fun categoryFeed(
        category: ContentCategory,
        force: Boolean = false
    ): CategoryFeed {
        val cached = feedCache[category]
        if (!force && cached != null && fresh(cached.at, FEED_TTL_MS)) return cached.value
        val feed = buildFeed(category)
        feedCache[category] = Timed(feed, System.currentTimeMillis())
        return feed
    }

    private suspend fun buildFeed(category: ContentCategory): CategoryFeed {
        val entities = enabledSources(category)
        val infos = entities.map { ContentSourceInfo(it.id, it.name, it.type) }
        if (entities.isEmpty()) {
            return CategoryFeed(category, emptyList(), emptyList(), emptyList(), infos, false)
        }

        partialFeeds.update { it - category }

        val limiter = Semaphore(MAX_PARALLEL_SOURCES)
        val results = CopyOnWriteArrayList<SourceResult>()
        // An unreachable provider must not hold the category hostage. Whatever answered
        // inside the budget is shown; the rest is reported as a timed-out provider.
        withTimeoutOrNull(FEED_BUDGET_MS) {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    balancedOrder(entities).map { entity ->
                        async {
                            val result = limiter.withPermit { fetchSource(entity, category) }
                            results += result
                            partialFeeds.update { current ->
                                current + (category to assemble(category, results.toList(), infos))
                            }
                        }
                    }.awaitAll()
                }
            }
        }

        val missing = entities
            .filter { entity -> results.none { it.status.sourceId == entity.id } }
            .map { entity ->
                SourceResult(
                    ProviderStatus(
                        sourceId = entity.id,
                        sourceName = entity.name,
                        type = entity.type,
                        available = false,
                        message = "No answer within ${FEED_BUDGET_MS / 1000} seconds."
                    ),
                    emptyMap()
                )
            }
        val feed = assemble(category, results.toList() + missing, infos)
        partialFeeds.update { it + (category to feed) }
        return feed
    }

    private fun assemble(
        category: ContentCategory,
        fetched: List<SourceResult>,
        infos: List<ContentSourceInfo>
    ): CategoryFeed {
        val ordered = fetched.sortedWith(
            compareByDescending<SourceResult> { it.status.available }
                .thenBy { it.status.sourceName.lowercase() }
        )

        val laneMap = LinkedHashMap<ContentLane, LinkedHashMap<String, ContentItem>>()
        ContentLane.entries.forEach { lane -> laneMap[lane] = LinkedHashMap() }

        // Round-robin across providers so the first shelf is not one source's catalogue.
        val maxItems = ordered.maxOfOrNull { result ->
            result.lanes.values.maxOfOrNull { it.size } ?: 0
        } ?: 0
        for (index in 0 until maxItems) {
            for (result in ordered) {
                for (lane in ContentLane.entries) {
                    val item = result.lanes[lane]?.getOrNull(index) ?: continue
                    mergeInto(laneMap.getValue(lane), item)
                }
            }
        }

        val shelves = ContentLane.entries.mapNotNull { lane ->
            val items = laneMap.getValue(lane).values.toList()
            if (items.isEmpty()) null
            else ContentShelf(
                id = "${category.name}:${lane.name}",
                title = shelfTitle(category, lane),
                items = items
            )
        }

        val all = LinkedHashMap<String, ContentItem>()
        shelves.forEach { shelf -> shelf.items.forEach { mergeInto(all, it) } }

        val canPage = ordered.any { result ->
            result.status.available && result.lanes.values.any { it.isNotEmpty() }
        }
        return CategoryFeed(
            category = category,
            shelves = shelves,
            items = all.values.toList(),
            statuses = fetched.map { it.status },
            sources = infos,
            canLoadMore = canPage
        )
    }

    private suspend fun fetchSource(
        entity: SourceEntity,
        category: ContentCategory
    ): SourceResult {
        if (!sourceHub.isAvailable(entity)) {
            return SourceResult(
                ProviderStatus(
                    sourceId = entity.id,
                    sourceName = entity.name,
                    type = entity.type,
                    available = false,
                    message = "Extension files are missing. Reinstall it in Settings → Extensions."
                ),
                emptyMap()
            )
        }
        val source = runCatching { sourceHub.instance(entity) }.getOrNull()
            ?: return SourceResult(
                ProviderStatus(
                    sourceId = entity.id,
                    sourceName = entity.name,
                    type = entity.type,
                    available = false,
                    message = "This source could not be loaded."
                ),
                emptyMap()
            )

        val catalogs = if (source.supportsCatalogs) {
            runCatching { source.catalogs() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        // Imported books/videos have no catalogue: their local rows are the catalogue.
        if (catalogs.isEmpty()) {
            val items = runCatching {
                timed { source.browse(null, 0) }
            }.getOrElse { error ->
                return SourceResult(
                    ProviderStatus(
                        sourceId = entity.id,
                        sourceName = entity.name,
                        type = entity.type,
                        available = false,
                        message = error.userMessage("this source")
                    ),
                    emptyMap()
                )
            }
            val laneItems = items.map { stub -> stubToItem(entity, category, null, stub) }
            val lanes = if (laneItems.isEmpty()) emptyMap() else mapOf(ContentLane.OTHER to laneItems)
            return SourceResult(
                ProviderStatus(
                    sourceId = entity.id,
                    sourceName = entity.name,
                    type = entity.type,
                    available = true,
                    itemCount = laneItems.size,
                    // A source with no catalogue (imported books, local video) has nothing
                    // until the user imports something. That is an empty shelf, not a fault.
                    message = null
                ),
                lanes
            )
        }

        val selected = catalogs.sortedBy(::catalogPriority).take(MAX_CATALOGS_PER_SOURCE)
        var successes = 0
        var lastError: String? = null
        val lanes = LinkedHashMap<ContentLane, MutableList<ContentItem>>()
        var count = 0
        for (catalog in selected) {
            val result = runCatching {
                timed { source.browse(catalog.id, 0) }
            }
            result.onSuccess { stubs ->
                successes++
                val lane = laneFor(category, catalog)
                val bucket = lanes.getOrPut(lane) { mutableListOf() }
                stubs.forEach { stub ->
                    bucket += stubToItem(entity, category, catalog, stub)
                }
                count += stubs.size
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                lastError = error.userMessage("this catalogue")
            }
            // A provider whose first catalogue is empty for this category rarely has
            // anything in its secondary feeds, and those feeds are the expensive ones
            // (MangaDex's latest feed pages through 100 chapters). Skip the remaining
            // catalogues instead of spending the whole feed budget on empty languages.
            if (count == 0 && selected.size > 1) break
        }

        return SourceResult(
            ProviderStatus(
                sourceId = entity.id,
                sourceName = entity.name,
                type = entity.type,
                available = successes > 0,
                itemCount = count,
                message = when {
                    successes == 0 && lastError != null -> lastError
                    count == 0 -> "Its catalogues are empty right now."
                    else -> null
                }
            ),
            lanes.mapValues { (_, value) -> value.toList() }
        )
    }

    /**
     * Fetches one more page from each provider's primary catalogue. Returns only real
     * responses; providers that fail are reported and do not stop the others.
     */
    suspend fun loadMore(category: ContentCategory, page: Int): ContentPage {
        if (page <= 0) return ContentPage(emptyList(), emptyList(), false)
        val entities = enabledSources(category)
        val statuses = java.util.Collections.synchronizedList(mutableListOf<ProviderStatus>())
        val items = LinkedHashMap<String, ContentItem>()

        withContext(Dispatchers.IO) {
            coroutineScope {
                entities.map { entity ->
                    async {
                        if (!sourceHub.isAvailable(entity)) {
                            statuses += ProviderStatus(
                                entity.id, entity.name, entity.type, false,
                                message = "Extension files are missing."
                            )
                            return@async
                        }
                        val source = runCatching { sourceHub.instance(entity) }.getOrNull()
                            ?: return@async
                        if (!source.supportsCatalogs) return@async
                        val catalog = runCatching { source.catalogs() }
                            .getOrDefault(emptyList())
                            .sortedBy(::catalogPriority)
                            .firstOrNull() ?: return@async
                        val result = runCatching {
                            timed { source.browse(catalog.id, page) }
                        }
                        result.onSuccess { stubs ->
                            stubs.forEach { stub ->
                                mergeInto(
                                    items,
                                    stubToItem(entity, category, catalog, stub)
                                )
                            }
                            statuses += ProviderStatus(
                                entity.id, entity.name, entity.type, true, stubs.size
                            )
                        }.onFailure { error ->
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            statuses += ProviderStatus(
                                entity.id, entity.name, entity.type, false,
                                message = error.userMessage("more results")
                            )
                        }
                    }
                }.awaitAll()
            }
        }

        return ContentPage(
            items = items.values.toList(),
            statuses = statuses,
            hasMore = items.isNotEmpty()
        )
    }

    /**
     * Unified search. When [category] is null every compatible installed source is
     * searched and results are grouped by category. Each group is deduplicated and
     * keeps every provider that returned the title.
     */
    suspend fun search(query: String, category: ContentCategory? = null): SearchResponse {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return SearchResponse(trimmed, emptyMap(), emptyList())

        val cacheKey = "${category?.name ?: "ALL"}|${titleKey(trimmed)}"
        val cached = searchCache[cacheKey]
        if (cached != null && fresh(cached.at, SEARCH_TTL_MS)) return cached.value

        val categories = category?.let { listOf(it) } ?: ContentCategory.entries
        val statuses = java.util.Collections.synchronizedList(mutableListOf<ProviderStatus>())
        val grouped = LinkedHashMap<ContentCategory, List<ContentItem>>()

        for (target in categories) {
            val entities = enabledSources(target)
            if (entities.isEmpty()) {
                grouped[target] = emptyList()
                continue
            }
            val limiter = Semaphore(MAX_PARALLEL_SOURCES)
            val perSource = java.util.Collections.synchronizedList(
                mutableListOf<Pair<ProviderStatus, List<ContentItem>>>()
            )
            withTimeoutOrNull(SEARCH_BUDGET_MS) {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        balancedOrder(entities).map { entity ->
                            async {
                                val result = limiter.withPermit {
                                    searchSource(entity, target, trimmed)
                                }
                                perSource += result
                            }
                        }.awaitAll()
                    }
                }
            }
            perSource += entities
                .filter { entity -> perSource.none { it.first.sourceId == entity.id } }
                .map { entity ->
                    ProviderStatus(
                        entity.id, entity.name, entity.type, false,
                        message = "No answer within ${SEARCH_BUDGET_MS / 1000} seconds."
                    ) to emptyList<ContentItem>()
                }
            statuses += perSource.map { it.first }
            val results = perSource.toList()
            val merged = LinkedHashMap<String, ContentItem>()
            val max = results.maxOfOrNull { it.second.size } ?: 0
            for (index in 0 until max) {
                for ((_, items) in results) {
                    items.getOrNull(index)?.let { mergeInto(merged, it) }
                }
            }
            grouped[target] = merged.values.toList()
        }

        val response = SearchResponse(trimmed, grouped, statuses.toList())
        searchCache[cacheKey] = Timed(response, System.currentTimeMillis())
        return response
    }

    private suspend fun searchSource(
        entity: SourceEntity,
        category: ContentCategory,
        query: String
    ): Pair<ProviderStatus, List<ContentItem>> {
        if (!sourceHub.isAvailable(entity)) {
            return ProviderStatus(
                entity.id, entity.name, entity.type, false,
                message = "Extension files are missing."
            ) to emptyList()
        }
        val source = runCatching { sourceHub.instance(entity) }.getOrNull()
            ?: return ProviderStatus(
                entity.id, entity.name, entity.type, false,
                message = "This source could not be loaded."
            ) to emptyList()
        if (!source.supportsSearch) {
            return ProviderStatus(
                entity.id, entity.name, entity.type, true,
                message = "Does not support search."
            ) to emptyList()
        }
        return runCatching {
            timed { source.search(query, 0) }
        }.fold(
            onSuccess = { stubs ->
                ProviderStatus(
                    entity.id, entity.name, entity.type, true, stubs.size
                ) to stubs.map { stub -> stubToItem(entity, category, null, stub) }
            },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                ProviderStatus(
                    entity.id, entity.name, entity.type, false,
                    message = error.userMessage("search")
                ) to emptyList()
            }
        )
    }

    /**
     * Loads details for filters. Only called when the user opens the filter sheet, so a
     * category never pays this network cost just to render.
     */
    suspend fun enrich(items: List<ContentItem>, limit: Int = ENRICH_LIMIT): Map<String, MediaDetails?> {
        val limiter = Semaphore(MAX_PARALLEL_DETAILS)
        val targets = items.take(limit)
        return withContext(Dispatchers.IO) {
            coroutineScope {
                targets.map { item ->
                    async {
                        val ref = item.primary
                        val details = limiter.withPermit {
                            detailsFor(ref).getOrNull()
                        }
                        refId(ref) to details
                    }
                }.awaitAll().toMap()
            }
        }
    }

    /** Details for one provider key, cached briefly so filters/selectors can reuse them. */
    suspend fun detailsFor(ref: ContentSourceRef): Result<MediaDetails> {
        val cacheKey = refId(ref)
        val cached = detailsCache[cacheKey]
        if (cached != null && fresh(cached.at, DETAILS_TTL_MS)) return Result.success(cached.value)
        return runCatching {
            val source = sourceHub.sourceFor(ref.sourceId)
                ?: error("That source is no longer installed")
            timed { source.details(ref.key) }
        }.onSuccess { details ->
            detailsCache[cacheKey] = Timed(details, System.currentTimeMillis())
        }
    }

    /**
     * Confirms which other installed sources can serve [title]. A source is only marked
     * available after its own search returned an exact title match, so the UI never
     * auto-selects an unconfirmed provider.
     */
    suspend fun alternateSources(
        title: String,
        category: ContentCategory,
        primary: ContentSourceRef,
        primaryStatus: String?
    ): List<SourceOption> {
        val preferred = settings.current().preferredSources[preferenceKey(category, title)]
        val entities = enabledSources(category)
        val limiter = Semaphore(MAX_PARALLEL_DETAILS)
        val options = CopyOnWriteArrayList<SourceOption>()
        options += SourceOption(
            sourceId = primary.sourceId,
            sourceName = primary.sourceName,
            key = primary.key,
            category = category,
            isPrimary = true,
            isPreferred = preferred == primary.sourceId,
            available = true,
            statusLabel = primaryStatus
        )
        // Confirmation searches every other source, so it needs the same deadline as
        // unified search: a source that never answers is reported, not awaited forever.
        withTimeoutOrNull(SEARCH_BUDGET_MS) {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    entities.filter { it.id != primary.sourceId }.map { entity ->
                        async {
                            options += limiter.withPermit {
                                confirmSource(entity, category, title, preferred)
                            }
                        }
                    }.awaitAll()
                }
            }
        }
        entities
            .filter { entity -> options.none { it.sourceId == entity.id } }
            .forEach { entity ->
                options += SourceOption(
                    sourceId = entity.id,
                    sourceName = entity.name,
                    key = null,
                    category = category,
                    isPrimary = false,
                    isPreferred = preferred == entity.id,
                    available = false,
                    statusLabel = null,
                    message = "No answer within ${SEARCH_BUDGET_MS / 1000} seconds."
                )
            }
        return options.sortedWith(
            compareByDescending<SourceOption> { it.isPrimary }
                .thenByDescending { it.isPreferred }
                .thenByDescending { it.available }
                .thenBy { it.sourceName.lowercase() }
        )
    }

    private suspend fun confirmSource(
        entity: SourceEntity,
        category: ContentCategory,
        title: String,
        preferred: String?
    ): SourceOption {
        fun unavailable(message: String) = SourceOption(
            sourceId = entity.id,
            sourceName = entity.name,
            key = null,
            category = category,
            isPrimary = false,
            isPreferred = preferred == entity.id,
            available = false,
            statusLabel = null,
            message = message
        )

        if (!sourceHub.isAvailable(entity)) {
            return unavailable("Extension files are missing. Reinstall it in Settings → Extensions.")
        }
        val source = runCatching { sourceHub.instance(entity) }.getOrNull()
            ?: return unavailable("This source could not be loaded.")
        if (!source.supportsSearch) {
            return unavailable("Search is not supported by this source.")
        }
        val stubs = runCatching {
            timed { source.search(title, 0) }
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            return unavailable(error.userMessage("this source"))
        }
        val wanted = titleKey(title)
        val match = stubs.firstOrNull { titleKey(it.title) == wanted }
            ?: return unavailable("No matching title found.")
        val ref = ContentSourceRef(
            sourceId = entity.id,
            sourceName = entity.name,
            key = match.key,
            type = entity.type
        )
        val details = detailsFor(ref).getOrNull()
        return SourceOption(
            sourceId = entity.id,
            sourceName = entity.name,
            key = match.key,
            category = category,
            isPrimary = false,
            isPreferred = preferred == entity.id,
            available = true,
            statusLabel = statusLabel(category, details) ?: "Matching title",
            message = null
        )
    }

    private fun statusLabel(category: ContentCategory, details: MediaDetails?): String? {
        if (details == null) return null
        val parts = buildList {
            if (category == ContentCategory.MOVIES) {
                details.kind?.label?.let(::add)
            }
            details.year?.let { add(it.toString()) }
            details.status?.takeIf { it.isNotBlank() }?.let(::add)
            details.author?.takeIf { it.isNotBlank() }?.let(::add)
            details.genres.take(2).joinToString(", ").takeIf { it.isNotBlank() }?.let(::add)
            details.totalUnits?.takeIf { it > 0 }?.let { add("$it ${details.unitLabel?.lowercase() ?: "items"}") }
        }
        return parts.joinToString(" · ").takeIf { it.isNotBlank() }
    }

    suspend fun rememberPreferredSource(
        title: String,
        category: ContentCategory,
        sourceId: String
    ) {
        settings.setPreferredSource(preferenceKey(category, title), sourceId)
    }

    private fun preferenceKey(category: ContentCategory, title: String): String =
        "${category.name}|${titleKey(title)}"

    private fun stubToItem(
        entity: SourceEntity,
        category: ContentCategory,
        catalog: Catalog?,
        stub: MediaStub
    ): ContentItem = ContentItem(
        title = stub.title,
        stub = stub,
        category = category,
        sources = listOf(
            ContentSourceRef(
                sourceId = entity.id,
                sourceName = entity.name,
                key = stub.key,
                type = entity.type,
                format = catalogFormat(category, catalog)
            )
        )
    )

    private fun catalogFormat(category: ContentCategory, catalog: Catalog?): String? {
        if (category != ContentCategory.MOVIES || catalog == null) return null
        val token = (catalog.id + " " + catalog.name).lowercase()
        return when {
            token.contains("series") || token.contains("show") || token.contains("tv") -> "Series"
            token.contains("movie") || token.contains("film") -> "Movie"
            else -> null
        }
    }

    private fun laneFor(category: ContentCategory, catalog: Catalog): ContentLane {
        val token = (catalog.id + " " + catalog.name).lowercase()
        return when {
            token.contains("trend") || token.contains("hot") -> ContentLane.TRENDING
            token.contains("latest") || token.contains("update") ||
                token.contains("recent") || token.contains("new") -> ContentLane.LATEST
            token.contains("popular") || token.contains("top") ||
                token.contains("best") -> ContentLane.POPULAR
            else -> ContentLane.OTHER
        }
    }

    private fun catalogPriority(catalog: Catalog): Int {
        val token = (catalog.id + " " + catalog.name).lowercase()
        return when {
            token.contains("popular") || token.contains("top") -> 0
            token.contains("trend") || token.contains("hot") -> 1
            token.contains("latest") || token.contains("update") -> 2
            token.contains("recent") || token.contains("new") -> 3
            else -> 4
        }
    }

    private fun shelfTitle(category: ContentCategory, lane: ContentLane): String =
        when (category) {
            ContentCategory.MOVIES -> when (lane) {
                ContentLane.POPULAR -> "Popular now"
                ContentLane.TRENDING -> "Trending now"
                ContentLane.LATEST -> "Latest releases"
                ContentLane.RECENT -> "Recently updated"
                ContentLane.OTHER -> "More to watch"
            }
            ContentCategory.BOOKS -> when (lane) {
                ContentLane.POPULAR -> "Popular novels"
                ContentLane.TRENDING -> "Trending now"
                ContentLane.LATEST -> "Latest updates"
                ContentLane.RECENT -> "Recently updated"
                ContentLane.OTHER -> "More to read"
            }
            ContentCategory.MANGA -> when (lane) {
                ContentLane.POPULAR -> "Popular"
                ContentLane.TRENDING -> "Trending now"
                ContentLane.LATEST -> "Latest updates"
                ContentLane.RECENT -> "Recently updated"
                ContentLane.OTHER -> "More to discover"
            }
        }

    private fun mergeInto(target: LinkedHashMap<String, ContentItem>, item: ContentItem) {
        val key = titleKey(item.title).ifBlank { item.primary.sourceId + "|" + item.primary.key }
        val existing = target[key]
        if (existing == null) {
            target[key] = item
            return
        }
        val sources = (existing.sources + item.sources)
            .distinctBy { ref -> ref.sourceId to ref.key }
        target[key] = existing.copy(sources = sources)
    }

    private fun fresh(at: Long, ttl: Long): Boolean =
        System.currentTimeMillis() - at <= ttl

    /**
     * Interleaves sources by provider name so one extension exposing dozens of variants
     * (MangaDex ships one source per language) cannot crowd every other provider out of
     * the parallel window or the feed budget.
     */
    private fun balancedOrder(entities: List<SourceEntity>): List<SourceEntity> {
        val groups = LinkedHashMap<String, java.util.ArrayDeque<SourceEntity>>()
        entities.forEach { entity ->
            groups.getOrPut(entity.name.lowercase()) { java.util.ArrayDeque() }.add(entity)
        }
        val ordered = ArrayList<SourceEntity>(entities.size)
        while (groups.values.any { it.isNotEmpty() }) {
            groups.values.forEach { queue -> queue.poll()?.let(ordered::add) }
        }
        return ordered
    }

    /**
     * Runs one provider request with a deadline. The deadline is per request and must not
     * cancel the caller: [kotlinx.coroutines.withTimeout] throws a [kotlinx.coroutines.CancellationException]
     * subclass, which callers correctly rethrow as "the whole feed was cancelled". Using
     * [withTimeoutOrNull] keeps a slow provider a normal, reportable failure instead.
     */
    private suspend fun <T> timed(block: suspend () -> T): T =
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) { block() }
            ?: throw SourceTimeoutException()

    private class SourceTimeoutException : Exception(
        "The source took longer than ${REQUEST_TIMEOUT_MS / 1000} seconds to answer."
    )

    private companion object {
        const val FEED_TTL_MS = 3 * 60_000L
        const val SEARCH_TTL_MS = 2 * 60_000L
        const val DETAILS_TTL_MS = 5 * 60_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val FEED_BUDGET_MS = 45_000L
        const val SEARCH_BUDGET_MS = 30_000L
        const val MAX_PARALLEL_SOURCES = 6
        const val MAX_PARALLEL_DETAILS = 4
        const val MAX_CATALOGS_PER_SOURCE = 4
        const val ENRICH_LIMIT = 80
    }
}
