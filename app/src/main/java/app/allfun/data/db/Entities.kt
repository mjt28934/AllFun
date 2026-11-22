package app.allfun.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.allfun.data.model.MediaKind
import app.allfun.data.model.SourceType

@Entity(
    tableName = "media",
    indices = [Index("inLibrary"), Index("sourceId"), Index("kind")]
)
data class MediaEntity(
    @PrimaryKey val id: String,
    val kind: MediaKind,
    val sourceId: String,
    val sourceKey: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val backdropUrl: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val genres: String = "",
    val cast: String = "",
    val totalUnits: Int = 0,
    val unitLabel: String = "Chapter",
    val inLibrary: Boolean = false,
    /** Pinned by the user, independent of [inLibrary]. */
    val favorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null,
    /** Extension-private JSON (SManga.memo) required for later chapter/page requests. */
    val memo: String? = null
) {
    val genreList: List<String>
        get() = genres.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val castList: List<String>
        get() = cast.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "episodes",
    primaryKeys = ["mediaId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mediaId")]
)
data class EpisodeEntity(
    val mediaId: String,
    val id: String,
    val orderIndex: Int,
    val title: String,
    val season: Int = 0,
    val number: Double = 0.0,
    val url: String? = null,
    val payload: String? = null,
    val durationMs: Long? = null,
    val pageCount: Int? = null,
    /** The source's own id for this unit, kept so refreshes never orphan progress. */
    val sourceUnitId: String? = null,
    /** Extension-private JSON (SChapter.memo) required to fetch pages. */
    val memo: String? = null,
    /** Set when the reader opens the chapter; drives the unread markers on the detail page. */
    val read: Boolean = false
)

@Entity(
    tableName = "progress",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProgressEntity(
    @PrimaryKey val mediaId: String,
    val episodeId: String,
    val position: Long,
    val total: Long,
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** 0..1. Position is pages for manga, a 0..1000 scroll fraction for novels, ms for video. */
    val fraction: Float
        get() = if (total <= 0L) 0f else (position.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val type: SourceType,
    val name: String,
    val baseUrl: String? = null,
    val configJson: String? = null,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    /** Optional repository the extension came from, for update checks. */
    val repoUrl: String? = null,
    /** Version reported by the repository/extension package at install time. */
    val version: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

/** Real lifecycle of an offline download; nothing is simulated. */
enum class DownloadStatus {
    QUEUED,
    WAITING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED;

    val isActive: Boolean get() = this == QUEUED || this == WAITING || this == RUNNING
}

/**
 * One downloaded chapter, episode or film. The row is keyed by media+unit so the same
 * chapter can never be queued twice, and a completed row is what makes an item appear
 * in the Library's Downloaded filter and be readable offline.
 */
@Entity(
    tableName = "downloads",
    indices = [Index("mediaId"), Index("status")]
)
data class DownloadEntity(
    @PrimaryKey val key: String,
    val mediaId: String,
    val unitId: String,
    val kind: MediaKind,
    val mediaTitle: String,
    val unitTitle: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    /** 0..100, or -1 while the server does not report a length. */
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    /** Directory for chapter pages, file path for a single-file download. */
    val path: String? = null,
    val error: String? = null,
    /** Real reason a queued row is not running, e.g. waiting for Wi-Fi. */
    val note: String? = null,
    /** Chapter/episode number at queue time, so the queue can be ordered like Komikku's. */
    val unitNumber: Double = 0.0,
    /**
     * Position in the download queue. The queue is explicit and persisted, like Komikku's:
     * moving an item to the top or bottom writes this value, and a restart restores the
     * same order instead of falling back to creation time.
     */
    val queueOrder: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
