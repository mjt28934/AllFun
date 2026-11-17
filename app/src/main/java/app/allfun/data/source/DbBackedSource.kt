package app.allfun.data.source

import app.allfun.data.db.EpisodeDao
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaDao
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaDetails
import app.allfun.data.model.MediaStub
import app.allfun.data.model.UnitStub
import app.allfun.data.Ids

/**
 * A source whose catalogue already lives in the database: imported EPUBs, imported
 * video files. Browsing it simply lists what was imported.
 */
abstract class DbBackedSource(
    protected val mediaDao: MediaDao,
    protected val episodeDao: EpisodeDao
) : MediaSource() {

    override fun catalogs() = emptyList<app.allfun.data.model.Catalog>()

    override suspend fun browse(catalog: String?, page: Int): List<MediaStub> {
        if (page > 0) return emptyList()
        return mediaDao.bySource(id).map {
            MediaStub(
                key = it.sourceKey,
                title = it.title,
                coverUrl = it.coverUrl,
                subtitle = it.author,
                description = it.description
            )
        }
    }

    override suspend fun search(query: String, page: Int): List<MediaStub> =
        browse(null, page).filter { it.title.contains(query, ignoreCase = true) }

    protected suspend fun row(key: String): MediaEntity? = mediaDao.byId(Ids.media(id, key))

    override suspend fun details(key: String): MediaDetails {
        val row = row(key) ?: error("Item not found in $name")
        return MediaDetails(
            key = key,
            title = row.title,
            kind = row.kind,
            author = row.author,
            description = row.description,
            coverUrl = row.coverUrl,
            status = row.status,
            year = row.year,
            genres = row.genreList,
            totalUnits = row.totalUnits,
            unitLabel = row.unitLabel
        )
    }

    override suspend fun units(key: String): List<UnitStub> {
        val row = row(key) ?: return emptyList()
        return episodeDao.forMedia(row.id).map { it.toUnitStub() }
    }
}

fun EpisodeEntity.toUnitStub() = UnitStub(
    id = sourceUnitId ?: id,
    index = orderIndex,
    title = title,
    season = season,
    number = number,
    url = url,
    payload = payload,
    durationMs = durationMs,
    pageCount = pageCount,
    memo = memo
)
