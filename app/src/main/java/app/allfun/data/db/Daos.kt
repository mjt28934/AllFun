package app.allfun.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Media joined with its most recent progress entry. */
data class ContinueRow(
    @Embedded val media: MediaEntity,
    val progressEpisodeId: String,
    val progressPosition: Long,
    val progressTotal: Long,
    val progressUpdatedAt: Long
) {
    val fraction: Float
        get() = if (progressTotal <= 0L) 0f
        else (progressPosition.toFloat() / progressTotal.toFloat()).coerceIn(0f, 1f)
}

/** Library row with optional progress; media without progress have null fields. */
data class LibraryRow(
    @Embedded val media: MediaEntity,
    val progressEpisodeId: String?,
    val progressPosition: Long?,
    val progressTotal: Long?
) {
    val fraction: Float?
        get() {
            val total = progressTotal ?: return null
            val position = progressPosition ?: return null
            return if (total <= 0L) null
            else (position.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

@Dao
interface MediaDao {
    @Upsert
    suspend fun upsert(media: MediaEntity)

    @Upsert
    suspend fun upsertAll(items: List<MediaEntity>)

    @Query("SELECT * FROM media WHERE inLibrary = 1 ORDER BY updatedAt DESC")
    fun observeLibrary(): Flow<List<MediaEntity>>

    @Query(
        """
        SELECT m.*, p.episodeId AS progressEpisodeId, p.position AS progressPosition,
               p.total AS progressTotal
        FROM media m LEFT JOIN progress p ON p.mediaId = m.id
        WHERE m.inLibrary = 1
        ORDER BY m.updatedAt DESC
        """
    )
    fun observeLibraryWithProgress(): Flow<List<LibraryRow>>

    @Query("SELECT * FROM media WHERE id = :id")
    fun observeById(id: String): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun byId(id: String): MediaEntity?

    @Query("SELECT * FROM media WHERE sourceId = :sourceId ORDER BY title COLLATE NOCASE")
    suspend fun bySource(sourceId: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE inLibrary = 1 AND title LIKE '%' || :q || '%' ORDER BY title COLLATE NOCASE LIMIT 60")
    suspend fun searchLibrary(q: String): List<MediaEntity>

    @Query("UPDATE media SET inLibrary = :value, updatedAt = :now WHERE id = :id")
    suspend fun setInLibrary(id: String, value: Boolean, now: Long)

    @Query("UPDATE media SET favorite = :value, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, value: Boolean, now: Long)

    @Query("UPDATE media SET lastOpenedAt = :time WHERE id = :id")
    suspend fun touchOpened(id: String, time: Long)

    @Query("UPDATE media SET coverUrl = :cover WHERE id = :id")
    suspend fun setCover(id: String, cover: String?)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM media WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("SELECT id FROM media WHERE sourceId = :sourceId AND title = :title AND author = :author")
    suspend fun idsMatching(sourceId: String, title: String, author: String): List<String>

    @Query("SELECT COUNT(*) FROM media WHERE inLibrary = 1")
    suspend fun libraryCount(): Int

    @Query("SELECT * FROM media")
    suspend fun all(): List<MediaEntity>

    @Query(
        """
        SELECT m.*, p.episodeId AS progressEpisodeId, p.position AS progressPosition,
               p.total AS progressTotal, p.updatedAt AS progressUpdatedAt
        FROM media m INNER JOIN progress p ON p.mediaId = m.id
        ORDER BY p.updatedAt DESC LIMIT 30
        """
    )
    fun observeContinue(): Flow<List<ContinueRow>>
}

@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EpisodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EpisodeEntity)

    @Query("SELECT * FROM episodes WHERE mediaId = :mediaId ORDER BY season, orderIndex")
    fun observeForMedia(mediaId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE mediaId = :mediaId ORDER BY season, orderIndex")
    suspend fun forMedia(mediaId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE mediaId = :mediaId AND id = :episodeId")
    suspend fun one(mediaId: String, episodeId: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE mediaId = :mediaId AND sourceUnitId = :unitId LIMIT 1")
    suspend fun bySourceUnit(mediaId: String, unitId: String): EpisodeEntity?

    @Query("DELETE FROM episodes WHERE mediaId = :mediaId")
    suspend fun deleteForMedia(mediaId: String)

    @Query("SELECT * FROM episodes")
    suspend fun all(): List<EpisodeEntity>
}

@Dao
interface ProgressDao {
    @Upsert
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE mediaId = :mediaId")
    suspend fun get(mediaId: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE mediaId = :mediaId")
    fun observe(mediaId: String): Flow<ProgressEntity?>

    @Query("DELETE FROM progress WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)

    @Query("SELECT COUNT(*) FROM progress")
    suspend fun count(): Int

    @Query("SELECT * FROM progress")
    suspend fun all(): List<ProgressEntity>
}

@Dao
interface SourceDao {
    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Query("SELECT * FROM sources ORDER BY builtIn DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY builtIn DESC, name COLLATE NOCASE")
    suspend fun all(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun byId(id: String): SourceEntity?

    @Query("UPDATE sources SET enabled = :value WHERE id = :id")
    suspend fun setEnabled(id: String, value: Boolean)

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun count(): Int

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: SourceEntity)
}
