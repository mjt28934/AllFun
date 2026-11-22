package app.allfun.data.source

import app.allfun.data.db.SourceEntity
import app.allfun.data.model.Catalog
import app.allfun.data.model.MediaDetails
import app.allfun.data.model.MediaKind
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import app.allfun.data.model.StreamInfo
import app.allfun.data.model.TorrentRef
import app.allfun.data.model.UnitStub
import app.allfun.data.net.Http
import app.allfun.data.net.arr
import app.allfun.data.net.int
import app.allfun.data.net.obj
import app.allfun.data.net.str
import app.allfun.data.net.strings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * Stremio addon protocol (manifest / catalog / meta / stream / subtitles).
 *
 * The addon is described entirely by its manifest, which AllFun stores at install time.
 * Catalog `extra` declarations decide which query shapes are legal: an addon that does
 * not advertise `search` is never sent a search term, and `skip` is only appended to
 * catalogs that support it. Requesting an unsupported extra makes most addons return
 * 404, which is why guessing here is worse than asking.
 *
 * Direct HTTP(S) streams play as-is. Torrent entries (`infoHash` or `magnet`) are
 * translated into a magnet with the addon's trackers and handed to the torrent engine,
 * which streams the selected file. Browser-only (`externalUrl`) results are skipped.
 */
class StremioSource(
    private val entity: SourceEntity,
    private val http: Http
) : MediaSource() {

    override val id = entity.id
    override val name = entity.name
    override val type = SourceType.VIDEO

    /** Only catalogs that advertise the `search` extra can answer a search request. */
    override val supportsSearch: Boolean get() = addonCatalogs.any { it.supportsSearch }

    /** No manifest catalogs means no browse surface; never invent one. */
    override val supportsCatalogs: Boolean get() = addonCatalogs.isNotEmpty()

    private val base = (entity.baseUrl ?: "").trimEnd('/')

    /**
     * A resource the addon declares. The Stremio protocol lists `stream` and `meta` as
     * either bare strings or objects with `types`/`idPrefixes`, and an addon may expose
     * one without the other: catalogue-only addons answer `catalog` and nothing else, so
     * asking them for `meta` returns an HTML error page. [StremioManifest] reads the
     * declaration so every lookup happens only against addons that can answer.
     */
    val supportsStreams: Boolean get() = StremioManifest.supports(manifest, "stream")

    /** Only addons that really declare `meta` may be asked for a detail page. */
    val supportsMeta: Boolean get() = StremioManifest.supports(manifest, "meta")

    /** Whether this addon can answer a stream request for [type] and [id]. */
    fun acceptsStream(type: String, id: String): Boolean =
        StremioManifest.accepts(manifest, "stream", type, id)

    /** Whether this addon can answer a metadata request for [type] and [id]. */
    fun acceptsMeta(type: String, id: String): Boolean =
        StremioManifest.accepts(manifest, "meta", type, id)

    /** One catalog entry from the manifest, including its supported extras. */
    private data class AddonCatalog(
        val type: String,
        val id: String,
        val name: String,
        val extras: Set<String>,
        val genres: List<String>
    ) {
        val key: String get() = "$type/$id"

        // The Stremio protocol treats a missing `search` extra as "not supported".
        // Sending a search anyway makes real addons answer 404, so do not guess.
        val supportsSearch: Boolean get() = extras.contains("search")
        val supportsSkip: Boolean get() = extras.contains("skip")
    }

    private val manifest: JsonObject? by lazy {
        val config = runCatching {
            Json.parseToJsonElement(entity.configJson ?: "{}").jsonObject
        }.getOrNull()
        // New rows store the whole manifest; older rows stored a flattened catalog list.
        config?.obj("manifest")
    }

    private val addonCatalogs: List<AddonCatalog> by lazy {
        val fromManifest = manifest?.arr("catalogs")
        if (fromManifest != null) {
            fromManifest.mapNotNull { it.catalogOrNull() }
        } else {
            // Backward compatibility with rows written before manifests were stored.
            runCatching {
                Json.parseToJsonElement(entity.configJson ?: "{}").jsonObject
                    .arr("catalogs")
                    ?.mapNotNull { element ->
                        val cat = element.jsonObject
                        val type = cat.str("type") ?: return@mapNotNull null
                        val catId = cat.str("id") ?: return@mapNotNull null
                        AddonCatalog(
                            type = type,
                            id = catId,
                            name = cat.str("name") ?: "$type · $catId",
                            extras = emptySet(),
                            genres = emptyList()
                        )
                    }
            }.getOrNull().orEmpty()
        }
    }

    private fun kotlinx.serialization.json.JsonElement.catalogOrNull(): AddonCatalog? {
        val cat = runCatching { jsonObject }.getOrNull() ?: return null
        val type = cat.str("type") ?: return null
        val catId = cat.str("id") ?: return null
        val extras = cat.arr("extra")
            ?.mapNotNull { (it as? JsonObject)?.str("name") }
            ?.toSet()
            .orEmpty()
        val genres = cat.arr("extra")
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.str("name") == "genre" }
            ?.arr("options")
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        return AddonCatalog(
            type = type,
            id = catId,
            name = cat.str("name") ?: "$type · $catId",
            extras = extras,
            genres = genres
        )
    }

    override fun catalogs(): List<Catalog> = addonCatalogs.map { Catalog(it.key, it.name) }

    override suspend fun browse(catalog: String?, page: Int): List<MediaStub> {
        val selected = catalog ?: catalogs().firstOrNull()?.id ?: "movie/top"
        val descriptor = addonCatalogs.firstOrNull { it.key == selected }
        val type = descriptor?.type ?: selected.substringBefore('/', "movie")
        val catalogId = descriptor?.id ?: selected.substringAfter('/', "top")

        val extras = buildList {
            // Stremio's skip is an item offset, not a page number. Catalogs default to
            // 100 items per page, so page N starts at N * 100.
            if (descriptor?.supportsSkip == true && page > 0) add("skip=${page * PAGE_SIZE}")
        }
        val suffix = if (extras.isEmpty()) "" else "/" + extras.joinToString("&")
        val root = http.getJson("$base/catalog/$type/$catalogId$suffix.json").jsonObject
        return metas(root, type)
    }

    override suspend fun search(query: String, page: Int): List<MediaStub> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val capable = addonCatalogs.filter { it.supportsSearch }
        if (capable.isEmpty()) return emptyList()
        val results = mutableListOf<MediaStub>()
        for (catalog in capable) {
            val extras = buildList {
                add("search=$encoded")
                if (catalog.supportsSkip && page > 0) add("skip=${page * PAGE_SIZE}")
            }
            val root = runCatching {
                http.getJson(
                    "$base/catalog/${catalog.type}/${catalog.id}/${extras.joinToString("&")}.json"
                ).jsonObject
            }.getOrNull() ?: continue
            results += metas(root, catalog.type)
        }
        return results.distinctBy { it.key }
    }

    private fun metas(root: JsonObject, type: String): List<MediaStub> =
        root.arr("metas")?.mapNotNull { element ->
            val meta = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val metaId = meta.str("id") ?: return@mapNotNull null
            val release = meta.str("releaseInfo")
            MediaStub(
                key = "$type|$metaId",
                title = meta.str("name") ?: "Untitled",
                coverUrl = meta.str("poster"),
                subtitle = listOfNotNull(release, meta.str("imdbRating")?.let { "★ $it" })
                    .joinToString(" · ")
                    .ifBlank { null },
                description = meta.str("description")
            )
        } ?: emptyList()

    private fun splitKey(key: String): Pair<String, String> {
        val type = key.substringBefore('|', "movie")
        val metaId = key.substringAfter('|', key)
        return type to metaId
    }

    /**
     * One title's metadata and episode list, parsed from a single `meta` response. The
     * detail screen needs both, and addons with large series answer slowly, so fetching
     * once avoids a second round trip for the same JSON.
     */
    class MetaBundle(
        val details: MediaDetails,
        val units: List<UnitStub>,
        val hasVideos: Boolean
    )

    /** Returns null when the addon has no metadata for this key. */
    suspend fun metaBundle(key: String): MetaBundle? {
        val (type, metaId) = splitKey(key)
        val meta = http.getJson("$base/meta/$type/${encodePath(metaId)}.json")
            .jsonObject.obj("meta")
            ?: return null
        return MetaBundle(
            details = detailsFrom(key, type, meta),
            units = unitsFrom(type, metaId, meta),
            hasVideos = !meta.arr("videos").isNullOrEmpty()
        )
    }

    override suspend fun details(key: String): MediaDetails =
        metaBundle(key)?.details
            ?: error("This addon does not provide details for that title.")

    override suspend fun units(key: String): List<UnitStub> =
        metaBundle(key)?.units.orEmpty()

    private fun detailsFrom(key: String, type: String, meta: JsonObject): MediaDetails {
        val isSeries = type == "series"
        val videos = meta.arr("videos")?.size ?: 0
        return MediaDetails(
            key = key,
            title = meta.str("name") ?: "Untitled",
            kind = if (isSeries) MediaKind.SERIES else MediaKind.MOVIE,
            author = meta.str("director") ?: meta.str("writer"),
            description = meta.str("description"),
            coverUrl = meta.str("poster"),
            backdropUrl = meta.str("background"),
            status = null,
            year = meta.str("year")?.take(4)?.toIntOrNull()
                ?: meta.str("releaseInfo")?.take(4)?.toIntOrNull(),
            genres = meta.strings("genres"),
            cast = meta.strings("cast"),
            totalUnits = videos,
            unitLabel = if (isSeries) "Episode" else "Video"
        )
    }

    private fun unitsFrom(type: String, metaId: String, meta: JsonObject): List<UnitStub> {
        val videos = meta.arr("videos")
        val fallback = listOf(
            UnitStub(
                id = metaId,
                index = 0,
                title = meta.str("name") ?: "Feature",
                url = metaId,
                durationMs = meta.str("runtime")?.let(::runtimeToMillis)
            )
        )
        if (videos.isNullOrEmpty()) return fallback

        data class Video(val stub: UnitStub, val season: Int, val episode: Int)

        val parsed = videos.mapNotNull { element ->
            val video = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val videoId = video.str("id") ?: return@mapNotNull null
            val season = video.int("season") ?: 1
            val episode = video.int("episode") ?: 0
            val label = video.str("name")?.takeIf { it.isNotBlank() }
                ?: if (season == 0) "Special $episode" else "S%02dE%02d".format(season, episode)
            Video(
                stub = UnitStub(
                    id = videoId,
                    index = 0,
                    title = label,
                    season = season,
                    number = episode.toDouble(),
                    url = videoId,
                    durationMs = video.str("runtime")?.let(::runtimeToMillis)
                ),
                season = season,
                episode = episode
            )
        }
        // Addons are inconsistent about ordering; sort by season/episode when the data
        // supports it, otherwise keep the addon's own order.
        val sorted = if (parsed.all { it.episode > 0 }) {
            parsed.sortedWith(compareBy({ it.season }, { it.episode }))
        } else {
            parsed
        }
        return sorted.mapIndexed { index, video -> video.stub.copy(index = index) }
    }

    override suspend fun stream(key: String, unitId: String, payload: String?): StreamInfo? =
        streams(key, unitId, payload).firstOrNull()

    /**
     * Every playable stream the addon offers for one title, best candidate first.
     *
     * Torrent-only addons routinely return hundreds of releases for a popular film,
     * and the one with the most advertised seeds can still be a private tracker the
     * device cannot reach. Returning the whole list lets the player offer the next
     * release instead of dead-ending on a single bad pick.
     */
    suspend fun streams(key: String, unitId: String, payload: String?): List<StreamInfo> {
        val (type, _) = splitKey(key)
        val streamId = payload?.takeIf { it.isNotBlank() } ?: unitId
        val entries = runCatching {
            http.getJson("$base/stream/$type/${encodePath(streamId)}.json")
                .jsonObject.arr("streams")
        }.getOrNull() ?: return emptyList()

        val collected = mutableListOf<StreamInfo>()
        val seen = mutableSetOf<String>()
        entries.forEach { element ->
            val stream = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val inlineSubtitle = stream.arr("subtitles")
                ?.firstNotNullOfOrNull { it.subtitleUrl() }
            val label = stream.str("name")?.trim()?.takeIf { it.isNotEmpty() }
            val detail = (stream.str("title") ?: stream.str("description"))
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val url = stream.str("url")
            if (url != null && url.startsWith("http", ignoreCase = true)) {
                if (!seen.add("url:$url")) return@forEach
                collected += StreamInfo(
                    url = url,
                    headers = requestHeaders(stream),
                    subtitleUrl = inlineSubtitle,
                    addonName = name,
                    name = label,
                    detail = detail
                )
                return@forEach
            }

            val fileName = stream.obj("behaviorHints")?.str("filename")
            val infoHash = stream.str("infoHash")
            val suppliedMagnet = stream.str("magnet")
                ?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
            val sources = stream.arr("sources")
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .orEmpty()
            val trackers = StremioTorrents.trackers(
                sources + listOfNotNull(stream.str("announce"))
            )
            val magnet = suppliedMagnet ?: StremioTorrents.magnet(
                infoHash = infoHash,
                displayName = fileName ?: stream.str("name"),
                trackers = trackers
            ) ?: return@forEach

            val identity = StremioTorrents.identity(suppliedMagnet ?: magnet)
            if (!seen.add("btih:$identity")) return@forEach
            collected += StreamInfo(
                url = magnet,
                subtitleUrl = inlineSubtitle,
                torrent = TorrentRef(
                    magnet = magnet,
                    fileIndex = stream.int("fileIdx")?.takeIf { it >= 0 },
                    fileName = fileName,
                    seeds = StremioTorrents.seeds(
                        stream.str("name"),
                        stream.str("title"),
                        stream.str("description")
                    )
                ),
                addonName = name,
                name = label,
                detail = detail
            )
        }

        if (collected.isEmpty()) return emptyList()
        val sorted = collected.sortedWith(
            compareByDescending<StreamInfo> { it.torrent == null }
                .thenByDescending { StremioTorrents.qualityRank(it) }
                .thenByDescending { it.torrent?.seeds ?: 0 }
        )
        if (sorted.any { it.subtitleUrl == null }) {
            val fallback = fetchSubtitle(type, streamId)
            if (fallback != null) {
                return sorted.map { if (it.subtitleUrl == null) it.copy(subtitleUrl = fallback) else it }
            }
        }
        return sorted
    }

    /** Proxied request headers a Stremio stream asks the player to send. */
    private fun requestHeaders(stream: JsonObject): Map<String, String> =
        stream.obj("behaviorHints")
            ?.obj("proxyHeaders")
            ?.obj("request")
            ?.let { request ->
                request.entries.mapNotNull { (header, value) ->
                    (value as? JsonPrimitive)?.contentOrNull?.let { header to it }
                }.toMap()
            }
            .orEmpty()

    private fun kotlinx.serialization.json.JsonElement.subtitleUrl(): String? {
        val entry = runCatching { jsonObject }.getOrNull() ?: return null
        val url = entry.str("url") ?: return null
        return url.takeIf { it.startsWith("http", ignoreCase = true) }
    }

    /** Falls back to the addon's `subtitles` resource when a stream carries none. */
    private suspend fun fetchSubtitle(type: String, streamId: String): String? = runCatching {
        http.getJson("$base/subtitles/$type/${encodePath(streamId)}.json")
            .jsonObject.arr("subtitles")
            ?.firstNotNullOfOrNull { it.subtitleUrl() }
    }.getOrNull()

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** "127 min" / "1h 32min" / "5400" -> milliseconds. */
    private fun runtimeToMillis(raw: String): Long? {
        val text = raw.trim().lowercase()
        if (text.isBlank()) return null
        text.toLongOrNull()?.let { return it * 60_000L }
        var total = 0L
        Regex("(\\d+)\\s*h").find(text)?.let { total += it.groupValues[1].toLong() * 3_600_000L }
        Regex("(\\d+)\\s*m").find(text)?.let { total += it.groupValues[1].toLong() * 60_000L }
        return total.takeIf { it > 0L }
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
