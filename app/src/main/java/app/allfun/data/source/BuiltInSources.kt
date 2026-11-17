package app.allfun.data.source

import app.allfun.data.db.EpisodeDao
import app.allfun.data.db.MediaDao
import app.allfun.data.model.SourceType
import app.allfun.data.model.StreamInfo

/** Imported EPUBs. */
class LocalNovelSource(
    mediaDao: MediaDao,
    episodeDao: EpisodeDao
) : DbBackedSource(mediaDao, episodeDao) {
    override val id = SourceIds.LOCAL_NOVEL
    override val name = "Imported books"
    override val type = SourceType.NOVEL
}

/** Imported video files. */
class LocalVideoSource(
    mediaDao: MediaDao,
    episodeDao: EpisodeDao
) : DbBackedSource(mediaDao, episodeDao) {
    override val id = SourceIds.LOCAL_VIDEO
    override val name = "Local videos"
    override val type = SourceType.VIDEO

    override suspend fun stream(key: String, unitId: String, payload: String?): StreamInfo? {
        val row = row(key) ?: return null
        val episode = episodeDao.forMedia(row.id).firstOrNull { (it.sourceUnitId ?: it.id) == unitId }
            ?: return null
        return episode.url?.let { StreamInfo(it) }
    }
}
