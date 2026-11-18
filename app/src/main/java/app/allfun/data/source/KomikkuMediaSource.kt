package app.allfun.data.source

import app.allfun.data.Ids
import app.allfun.data.db.EpisodeDao
import app.allfun.data.db.MediaDao
import app.allfun.data.db.SourceEntity
import app.allfun.data.model.Catalog
import app.allfun.data.model.MediaDetails
import app.allfun.data.model.MediaKind
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import app.allfun.data.model.UnitStub
import app.allfun.data.net.ImageHeaders
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Adapts a loaded Tachiyomi/Komikku extension source to AllFun's [MediaSource] contract.
 *
 * Extensions are stateful (cookies, memo fields), so the instance is kept alive by
 * [app.allfun.data.extension.ExtensionManager] for the life of the process.
 */
class KomikkuMediaSource(
    private val entity: SourceEntity,
    private val source: CatalogueSource,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao
) : MediaSource() {

    override val id: String = entity.id
    override val name: String = entity.name
    override val type: SourceType = SourceType.MANGA

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun catalogs(): List<Catalog> {
        val catalogs = mutableListOf(Catalog("popular", "Popular"))
        if (source.supportsLatest) catalogs += Catalog("latest", "Latest")
        return catalogs
    }

    override suspend fun browse(catalog: String?, page: Int): List<MediaStub> {
        val result = if (catalog == "latest") {
            source.getLatestUpdates(tachiyomiPage(page))
        } else {
            source.getPopularManga(tachiyomiPage(page))
        }
        return result.mangas.map { it.toStub() }
    }

    override suspend fun search(query: String, page: Int): List<MediaStub> {
        val result = source.getSearchManga(tachiyomiPage(page), query, source.getFilterList())
        return result.mangas.map { it.toStub() }
    }

    override suspend fun details(key: String): MediaDetails {
        val row = mediaDao.byId(Ids.media(id, key))
        val manga = SManga.create().apply {
            url = key
            memo = parseMemo(row?.memo)
        }
        val update = source.getMangaUpdate(
            manga = manga,
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false
        )
        val detail = update.manga
        val cover = absolute(detail.thumbnail_url)
        registerHeaders(cover)

        return MediaDetails(
            key = key,
            title = detail.title.ifBlank { key },
            kind = MediaKind.MANGA,
            author = detail.author?.takeIf { it.isNotBlank() },
            artist = detail.artist?.takeIf { it.isNotBlank() },
            description = detail.description?.takeIf { it.isNotBlank() },
            coverUrl = cover,
            status = statusLabel(detail.status),
            genres = detail.genre
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty(),
            totalUnits = null,
            unitLabel = "Chapter",
            sourceMemo = detail.memo.takeIf { it.isNotEmpty() }?.toString()
        )
    }

    override suspend fun units(key: String): List<UnitStub> {
        val row = mediaDao.byId(Ids.media(id, key))
        val manga = SManga.create().apply {
            url = key
            memo = parseMemo(row?.memo)
        }
        val update = source.getMangaUpdate(
            manga = manga,
            chapters = emptyList(),
            fetchDetails = false,
            fetchChapters = true
        )
        val ordered = update.chapters.sortedWith(
            compareBy({ it.chapter_number < 0f }, { it.chapter_number }, { it.name })
        )
        return ordered.mapIndexed { index, chapter ->
            UnitStub(
                id = chapter.url,
                index = index,
                title = chapter.name.ifBlank { "Chapter ${chapter.chapter_number}" },
                number = chapter.chapter_number.toDouble(),
                url = chapter.url,
                memo = chapter.memo.takeIf { it.isNotEmpty() }?.toString()
            )
        }
    }

    override suspend fun pages(key: String, unitId: String): List<String> {
        val mediaId = Ids.media(id, key)
        val episode = episodeDao.bySourceUnit(mediaId, unitId)
        val chapter = SChapter.create().apply {
            url = unitId
            name = episode?.title.orEmpty()
            memo = parseMemo(episode?.memo)
        }
        val pages = source.getPageList(chapter)
        val http = source as? HttpSource
        return pages.mapNotNull { page ->
            val raw = page.imageUrl?.takeIf { it.isNotBlank() }
                ?: http?.getImageUrl(page)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val resolved = absolute(raw) ?: return@mapNotNull null
            registerHeaders(resolved)
            resolved
        }
    }

    private fun SManga.toStub(): MediaStub {
        val cover = absolute(thumbnail_url)
        registerHeaders(cover)
        return MediaStub(
            key = url,
            title = title.ifBlank { url },
            coverUrl = cover,
            subtitle = author?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseMemo(raw: String?): JsonObject {
        if (raw.isNullOrBlank()) return buildJsonObject { }
        return runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: buildJsonObject { }
    }

    private fun registerHeaders(url: String?) {
        val http = source as? HttpSource ?: return
        ImageHeaders.register(url, http.headers)
    }

    private fun absolute(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.startsWith("http://") || raw.startsWith("https://") ||
            raw.startsWith("file:") || raw.startsWith("data:")
        ) {
            return raw
        }
        if (raw.startsWith("//")) return "https:$raw"
        val base = (source as? HttpSource)?.baseUrl ?: return raw
        return base.toHttpUrlOrNull()?.resolve(raw)?.toString() ?: raw
    }

    private fun statusLabel(status: Int): String? = when (status) {
        SManga.ONGOING -> "Ongoing"
        SManga.COMPLETED -> "Completed"
        SManga.LICENSED -> "Licensed"
        SManga.PUBLISHING_FINISHED -> "Publishing finished"
        SManga.CANCELLED -> "Cancelled"
        SManga.ON_HIATUS -> "On hiatus"
        else -> null
    }
}

/**
 * AllFun pages start at 0; Tachiyomi's source API starts at 1. Feeding 0 straight through
 * made extensions compute offsets like `(0 - 1) * limit` and get an HTTP 400 from the site,
 * so the conversion belongs here rather than in the aggregator.
 */
internal fun tachiyomiPage(page: Int): Int = page.coerceAtLeast(0) + 1
