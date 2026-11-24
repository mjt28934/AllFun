package app.allfun.data.repo

import app.allfun.data.Ids
import app.allfun.data.db.EpisodeDao
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaDao
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaDetails
import app.allfun.data.model.MediaKind
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import app.allfun.data.model.UnitStub
import kotlinx.coroutines.flow.Flow

/**
 * Single write path for everything that ends up on the shelf, whichever source it came from.
 */
class LibraryRepository(
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao
) {
    fun observeLibrary(): Flow<List<MediaEntity>> = mediaDao.observeLibrary()
    fun observeLibraryWithProgress() = mediaDao.observeLibraryWithProgress()
    fun observeContinue() = mediaDao.observeContinue()
    fun observeMedia(id: String): Flow<MediaEntity?> = mediaDao.observeById(id)
    fun observeEpisodes(mediaId: String): Flow<List<EpisodeEntity>> =
        episodeDao.observeForMedia(mediaId)

    suspend fun media(id: String): MediaEntity? = mediaDao.byId(id)
    suspend fun episodes(mediaId: String): List<EpisodeEntity> = episodeDao.forMedia(mediaId)
    suspend fun episode(mediaId: String, episodeId: String): EpisodeEntity? =
        episodeDao.one(mediaId, episodeId)

    suspend fun searchInLibrary(query: String): List<MediaEntity> =
        mediaDao.searchLibrary(query)

    suspend fun libraryCount(): Int = mediaDao.libraryCount()

    suspend fun hasItemsFrom(sourceId: String): Boolean = mediaDao.bySource(sourceId).isNotEmpty()

    suspend fun setInLibrary(id: String, value: Boolean) =
        mediaDao.setInLibrary(id, value, System.currentTimeMillis())

    suspend fun setFavorite(id: String, value: Boolean) =
        mediaDao.setFavorite(id, value, System.currentTimeMillis())

    suspend fun toggleFavorite(id: String) {
        val current = mediaDao.byId(id) ?: return
        mediaDao.setFavorite(id, !current.favorite, System.currentTimeMillis())
    }

    suspend fun toggleLibrary(id: String) {
        val current = mediaDao.byId(id) ?: return
        mediaDao.setInLibrary(id, !current.inLibrary, System.currentTimeMillis())
    }

    suspend fun touchOpened(id: String) = mediaDao.touchOpened(id, System.currentTimeMillis())

    suspend fun delete(id: String) = mediaDao.delete(id)

    suspend fun setCover(id: String, cover: String?) = mediaDao.setCover(id, cover)

    /**
     * Stores a detail page (and its chapter/episode list) before navigation, so every
     * screen downstream can work from the database instead of holding source objects.
     * Returns the media id to navigate to.
     */
    suspend fun savePreview(
        sourceId: String,
        sourceType: SourceType,
        details: MediaDetails,
        units: List<UnitStub>
    ): String {
        val mediaId = Ids.media(sourceId, details.key)
        val existing = mediaDao.byId(mediaId)
        val kind = details.kind ?: when (sourceType) {
            SourceType.MANGA -> MediaKind.MANGA
            SourceType.NOVEL -> MediaKind.NOVEL
            SourceType.VIDEO -> MediaKind.MOVIE
        }

        // Catalogue-only addons declare `catalog` but not `meta`, so a film opened from
        // their card arrives with no unit. A movie is playable from its own id, so keep
        // one unit instead of leaving the detail page with a disabled Play button.
        val playableUnits = if (units.isEmpty() && kind == MediaKind.MOVIE) {
            listOfNotNull(fallbackMovieUnit(details))
        } else {
            units
        }

        val media = MediaEntity(
            id = mediaId,
            kind = kind,
            sourceId = sourceId,
            sourceKey = details.key,
            title = details.title,
            author = details.author ?: existing?.author,
            artist = details.artist ?: existing?.artist,
            description = details.description ?: existing?.description,
            coverUrl = details.coverUrl ?: existing?.coverUrl,
            backdropUrl = details.backdropUrl ?: existing?.backdropUrl,
            status = details.status ?: existing?.status,
            year = details.year ?: existing?.year,
            genres = details.genres.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: existing?.genres.orEmpty(),
            cast = details.cast.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: existing?.cast.orEmpty(),
            totalUnits = playableUnits.size
                .coerceAtLeast(details.totalUnits ?: existing?.totalUnits ?: 0),
            unitLabel = details.unitLabel ?: kind.unit,
            inLibrary = existing?.inLibrary ?: false,
            favorite = existing?.favorite ?: false,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastOpenedAt = existing?.lastOpenedAt,
            memo = details.sourceMemo ?: existing?.memo
        )
        mediaDao.upsert(media)
        saveEpisodes(mediaId, playableUnits)
        return mediaId
    }

    /**
     * Guarantees a playable unit for a film whose catalogue addon cannot answer `meta`.
     * Returns false for anything else, so series still need a real episode list.
     */
    suspend fun ensurePlayableUnit(mediaId: String): Boolean {
        val media = mediaDao.byId(mediaId) ?: return false
        if (media.kind != MediaKind.MOVIE) return false
        if (episodeDao.forMedia(mediaId).isNotEmpty()) return false
        val unit = fallbackMovieUnit(
            MediaDetails(key = media.sourceKey, title = media.title)
        ) ?: return false
        saveEpisodes(mediaId, listOf(unit))
        mediaDao.upsert(
            media.copy(totalUnits = 1, unitLabel = media.kind.unit)
        )
        return true
    }

    /** The catalogue id is also the stream id for a film (`movie|tt…` → `tt…`). */
    private fun fallbackMovieUnit(details: MediaDetails): UnitStub? {
        val streamId = details.key.substringAfter('|', details.key).trim()
        if (streamId.isBlank()) return null
        return UnitStub(
            id = streamId,
            index = 0,
            title = details.title.ifBlank { "Feature" },
            url = streamId
        )
    }

    suspend fun saveEpisodes(mediaId: String, units: List<UnitStub>) {
        if (units.isEmpty()) return
        // Refreshes replace rows, so carry the read marker over or every update would
        // make the whole chapter list unread again.
        val readIds = episodeDao.forMedia(mediaId)
            .filter { it.read }
            .mapTo(mutableSetOf()) { it.id }
        episodeDao.upsertAll(
            units.map { unit ->
                val episodeId = Ids.episode(mediaId, unit.id)
                EpisodeEntity(
                    mediaId = mediaId,
                    id = episodeId,
                    orderIndex = unit.index,
                    title = unit.title,
                    season = unit.season,
                    number = unit.number,
                    url = unit.url,
                    payload = unit.payload,
                    durationMs = unit.durationMs,
                    pageCount = unit.pageCount,
                    sourceUnitId = unit.id,
                    memo = unit.memo,
                    read = episodeId in readIds
                )
            }
        )
    }

    suspend fun setEpisodeRead(mediaId: String, episodeId: String, read: Boolean) =
        episodeDao.setRead(mediaId, episodeId, read)

    suspend fun setAllEpisodesRead(mediaId: String, read: Boolean) =
        episodeDao.setAllRead(mediaId, read)

    suspend fun stubFor(media: MediaEntity) = MediaStub(
        key = media.sourceKey,
        title = media.title,
        coverUrl = media.coverUrl,
        subtitle = media.author
    )
}
