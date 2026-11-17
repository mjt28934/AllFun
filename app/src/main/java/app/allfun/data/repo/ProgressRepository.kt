package app.allfun.data.repo

import app.allfun.data.db.ProgressDao
import app.allfun.data.db.ProgressEntity
import kotlinx.coroutines.flow.Flow

class ProgressRepository(private val dao: ProgressDao) {
    fun observe(mediaId: String): Flow<ProgressEntity?> = dao.observe(mediaId)

    suspend fun get(mediaId: String): ProgressEntity? = dao.get(mediaId)

    suspend fun save(mediaId: String, episodeId: String, position: Long, total: Long) {
        if (position <= 0L && total <= 0L) return
        dao.upsert(
            ProgressEntity(
                mediaId = mediaId,
                episodeId = episodeId,
                position = position.coerceAtLeast(0L),
                total = total.coerceAtLeast(0L)
            )
        )
    }

    suspend fun clear(mediaId: String) = dao.delete(mediaId)
}
