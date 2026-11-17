package app.allfun.data.source

import app.allfun.data.db.SourceEntity
import app.allfun.data.model.MediaDetails
import app.allfun.data.model.MediaKind
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import app.allfun.data.model.StreamInfo
import app.allfun.data.model.UnitStub
import app.allfun.data.net.Http
import app.allfun.data.net.arr
import app.allfun.data.net.int
import app.allfun.data.net.long
import app.allfun.data.net.obj
import app.allfun.data.net.str
import app.allfun.data.net.strings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/** Talks to a Jellyfin server: browse, detail, episodes and direct-play streams. */
class JellyfinSource(
    private val entity: SourceEntity,
    private val http: Http
) : MediaSource() {

    override val id = entity.id
    override val name = entity.name
    override val type = SourceType.VIDEO

    private val config: JellyfinConfig? = runCatching {
        val obj = Json.parseToJsonElement(entity.configJson ?: "{}").jsonObject
        JellyfinConfig(
            base = (obj.str("base") ?: entity.baseUrl ?: "").trimEnd('/'),
            token = obj.str("token").orEmpty(),
            userId = obj.str("userId").orEmpty()
        )
    }.getOrNull()

    data class JellyfinConfig(val base: String, val token: String, val userId: String)

    private fun headers() = mapOf("X-Emby-Token" to (config?.token ?: ""))

    private fun api(path: String): String = (config?.base ?: "") + path

    private fun imageUrl(itemId: String, tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val token = config?.token ?: return null
        return api("/Items/$itemId/Images/Primary?maxHeight=720&tag=$tag&api_key=$token")
    }

    override suspend fun browse(catalog: String?, page: Int): List<MediaStub> {
        val start = page * 60
        val url = api(
            "/Items?Recursive=true&IncludeItemTypes=Movie,Series&SortBy=SortName" +
                "&Fields=ProductionYear,ImageTags,Overview&StartIndex=$start&Limit=60" +
                "&userId=${config?.userId.orEmpty()}"
        )
        val root = http.getJson(url, headers()).jsonObject
        return root.arr("Items")?.mapNotNull { element ->
            val item = element.jsonObject
            val itemId = item.str("Id") ?: return@mapNotNull null
            MediaStub(
                key = itemId,
                title = item.str("Name") ?: "Untitled",
                coverUrl = imageUrl(itemId, item.obj("ImageTags")?.str("Primary")),
                subtitle = item.int("ProductionYear")?.toString(),
                description = item.str("Overview")
            )
        } ?: emptyList()
    }

    override suspend fun search(query: String, page: Int): List<MediaStub> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = api(
            "/Items?Recursive=true&IncludeItemTypes=Movie,Series&SearchTerm=$encoded" +
                "&Fields=ProductionYear,ImageTags,Overview&Limit=60" +
                "&userId=${config?.userId.orEmpty()}"
        )
        val root = http.getJson(url, headers()).jsonObject
        return root.arr("Items")?.mapNotNull { element ->
            val item = element.jsonObject
            val itemId = item.str("Id") ?: return@mapNotNull null
            MediaStub(
                key = itemId,
                title = item.str("Name") ?: "Untitled",
                coverUrl = imageUrl(itemId, item.obj("ImageTags")?.str("Primary")),
                subtitle = item.int("ProductionYear")?.toString(),
                description = item.str("Overview")
            )
        } ?: emptyList()
    }

    override suspend fun details(key: String): MediaDetails {
        val url = api("/Items/$key?userId=${config?.userId.orEmpty()}")
        val item = http.getJson(url, headers()).jsonObject
        val isSeries = item.str("Type") == "Series"
        return MediaDetails(
            key = key,
            title = item.str("Name") ?: "Untitled",
            kind = if (isSeries) MediaKind.SERIES else MediaKind.MOVIE,
            author = item.str("Studios") ?: null,
            description = item.str("Overview"),
            coverUrl = imageUrl(key, item.obj("ImageTags")?.str("Primary")),
            status = null,
            year = item.int("ProductionYear"),
            genres = item.strings("Genres"),
            totalUnits = item.int("ChildCount"),
            unitLabel = if (isSeries) "Episode" else "Video"
        )
    }

    override suspend fun units(key: String): List<UnitStub> {
        val detailUrl = api("/Items/$key?userId=${config?.userId.orEmpty()}")
        val item = http.getJson(detailUrl, headers()).jsonObject
        if (item.str("Type") != "Series") {
            val ticks = item.long("RunTimeTicks") ?: 0L
            return listOf(
                UnitStub(
                    id = key,
                    index = 0,
                    title = item.str("Name") ?: "Feature",
                    url = key,
                    durationMs = ticks / 10_000L
                )
            )
        }
        val url = api("/Shows/$key/Episodes?userId=${config?.userId.orEmpty()}&Fields=Overview")
        val root = http.getJson(url, headers()).jsonObject
        val items = root.arr("Items")?.mapNotNull { it.jsonObject } ?: return emptyList()
        return items.sortedWith(
            compareBy({ it.int("ParentIndexNumber") ?: 0 }, { it.int("IndexNumber") ?: 0 })
        ).mapIndexed { index, episode ->
            val episodeId = episode.str("Id") ?: error("Episode without id")
            val season = episode.int("ParentIndexNumber") ?: 1
            val number = episode.int("IndexNumber") ?: (index + 1)
            UnitStub(
                id = episodeId,
                index = index,
                title = "S%02dE%02d • %s".format(season, number, episode.str("Name") ?: "Episode"),
                season = season,
                number = number.toDouble(),
                url = episodeId,
                durationMs = (episode.long("RunTimeTicks") ?: 0L) / 10_000L
            )
        }
    }

    override suspend fun stream(key: String, unitId: String, payload: String?): StreamInfo? {
        val cfg = config ?: return null
        val url = "${cfg.base}/Videos/$unitId/stream?static=true&api_key=${cfg.token}"
        return StreamInfo(
            url = url,
            headers = mapOf("X-Emby-Token" to cfg.token)
        )
    }
}
