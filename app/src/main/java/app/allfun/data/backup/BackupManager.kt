package app.allfun.data.backup

import app.allfun.data.db.EpisodeDao
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaDao
import app.allfun.data.db.MediaEntity
import app.allfun.data.db.ProgressDao
import app.allfun.data.db.ProgressEntity
import app.allfun.data.model.MediaKind
import app.allfun.data.settings.LibrarySort
import app.allfun.data.settings.LibraryView
import app.allfun.data.settings.MangaFit
import app.allfun.data.settings.NovelTheme
import app.allfun.data.settings.ReaderMode
import app.allfun.data.settings.ReadingDirection
import app.allfun.data.settings.RepositoryConfig
import app.allfun.data.settings.RepositoryKind
import app.allfun.data.settings.Settings
import app.allfun.data.settings.SettingsStore
import app.allfun.data.settings.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/** A backup the current build cannot understand, with a message safe to show the user. */
class BackupFormatException(message: String) : IOException(message)

@Serializable
data class BackupFile(
    val format: String = BackupManager.FORMAT,
    val schemaVersion: Int = BackupManager.SCHEMA_VERSION,
    val appVersion: String = "",
    val createdAt: Long = 0L,
    val settings: BackupSettings? = null,
    val media: List<BackupMedia> = emptyList(),
    val episodes: List<BackupEpisode> = emptyList(),
    val progress: List<BackupProgress> = emptyList()
)

@Serializable
data class BackupSettings(
    val readerMode: String = ReaderMode.VERTICAL.name,
    val novelFontSize: Int = 100,
    val novelLineSpacing: Float = 1.72f,
    val novelTheme: String = NovelTheme.DARK.name,
    val dynamicColor: Boolean = true,
    val themeMode: String = ThemeMode.DARK.name,
    val libraryView: String = LibraryView.GRID.name,
    val librarySort: String = LibrarySort.RECENTLY_UPDATED.name,
    val mangaFit: String = MangaFit.WIDTH.name,
    val readingDirection: String = ReadingDirection.LTR.name,
    val reduceMotion: Boolean = false,
    val resumePlayback: Boolean = true,
    val subtitlesEnabled: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val notifyUpdates: Boolean = true,
    val repositories: List<BackupRepository> = emptyList()
)

@Serializable
data class BackupRepository(val kind: String, val url: String)

@Serializable
data class BackupMedia(
    val id: String,
    val kind: String,
    val sourceId: String,
    val sourceKey: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val genres: String = "",
    val totalUnits: Int = 0,
    val unitLabel: String = "Chapter",
    val inLibrary: Boolean = false,
    val favorite: Boolean = false,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastOpenedAt: Long? = null,
    val memo: String? = null
)

@Serializable
data class BackupEpisode(
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
    val sourceUnitId: String? = null,
    val memo: String? = null,
    /** Absent in older backups; treated as unread. */
    val read: Boolean = false
)

@Serializable
data class BackupProgress(
    val mediaId: String,
    val episodeId: String,
    val position: Long,
    val total: Long,
    val updatedAt: Long = 0L
)

data class ImportSummary(
    val media: Int,
    val episodes: Int,
    val progress: Int,
    val repositories: Int,
    val settingsRestored: Boolean,
    /** Entries skipped because this version cannot read them (unknown kind, orphaned rows). */
    val skipped: Int
) {
    fun describe(): String {
        val parts = buildList {
            add("$media ${if (media == 1) "title" else "titles"}")
            if (episodes > 0) add("$episodes chapters/episodes")
            if (progress > 0) add("$progress progress entries")
            if (repositories > 0) add("$repositories repositories")
            if (settingsRestored) add("your settings")
        }
        val restored = if (parts.isEmpty()) "Nothing new" else "Restored ${parts.joinToString(", ")}"
        return if (skipped > 0) {
            "$restored. Skipped $skipped ${if (skipped == 1) "entry" else "entries"} this " +
                "version cannot read."
        } else {
            "$restored."
        }
    }
}

/**
 * Exports and restores the data that is AllFun's own: preferences, repository URLs, the
 * library, its chapters/episodes and reading/watching progress.
 *
 * Deliberately excluded: extension code and Jellyfin/Stremio credentials. Those belong to
 * the user's accounts or to signed packages, so a backup cannot smuggle credentials into a
 * new device (or restore code that no longer matches the installed app).
 *
 * Import is additive: existing rows are replaced by id, nothing is deleted. A backup from a
 * newer schema is refused rather than partially applied.
 */
class BackupManager(
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao,
    private val progressDao: ProgressDao,
    private val settingsStore: SettingsStore
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun export(appVersion: String): String {
        val settings = settingsStore.current()
        val media = mediaDao.all()
        val ids = media.mapTo(HashSet()) { it.id }
        val episodes = episodeDao.all().filter { it.mediaId in ids }
        val progress = progressDao.all().filter { it.mediaId in ids }

        val file = BackupFile(
            appVersion = appVersion,
            createdAt = System.currentTimeMillis(),
            settings = settings.toBackup(),
            media = media.map { it.toBackup() },
            episodes = episodes.map { it.toBackup() },
            progress = progress.map { it.toBackup() }
        )
        return json.encodeToString(file)
    }

    /**
     * Parses [text] completely before writing anything, so a truncated file leaves the
     * current library untouched.
     */
    suspend fun import(text: String): ImportSummary {
        if (text.isBlank()) {
            throw BackupFormatException("That file is empty. Choose the JSON file AllFun exported.")
        }
        val file = try {
            json.decodeFromString<BackupFile>(text)
        } catch (e: Exception) {
            throw BackupFormatException(
                "That file is not an AllFun backup. Choose the .json file exported from " +
                    "Settings › Backup and restore."
            )
        }
        if (file.format != FORMAT) {
            throw BackupFormatException(
                "That JSON is not an AllFun backup. Its format marker is missing or different."
            )
        }
        if (file.schemaVersion > SCHEMA_VERSION) {
            throw BackupFormatException(
                "This backup was made by a newer version of AllFun (backup format " +
                    "${file.schemaVersion}). Update AllFun, then restore it."
            )
        }

        var skipped = 0
        val parsedMedia = file.media.mapNotNull { item ->
            val kind = MediaKind.entries.firstOrNull { it.name == item.kind }
            if (kind == null || item.id.isBlank() || item.title.isBlank()) {
                skipped++
                null
            } else {
                item.toEntity(kind)
            }
        }
        val parsedEpisodes = file.episodes.mapNotNull { item ->
            if (item.mediaId.isBlank() || item.id.isBlank()) {
                skipped++
                null
            } else {
                item.toEntity()
            }
        }.distinctBy { "${it.mediaId}\u0000${it.id}" }

        val parsedProgress = file.progress.mapNotNull { item ->
            if (item.mediaId.isBlank() || item.episodeId.isBlank()) {
                skipped++
                null
            } else {
                item.toEntity()
            }
        }.distinctBy { it.mediaId }

        file.settings?.let { settingsStore.apply(it.toSettings()) }

        // Media first: episodes and progress carry a foreign key to media.id.
        if (parsedMedia.isNotEmpty()) mediaDao.upsertAll(parsedMedia)
        val knownIds = parsedMedia.mapTo(HashSet()) { it.id }
        val episodes = parsedEpisodes.filter { it.mediaId in knownIds }
        skipped += parsedEpisodes.size - episodes.size
        if (episodes.isNotEmpty()) episodeDao.upsertAll(episodes)
        val progress = parsedProgress.filter { it.mediaId in knownIds }
        skipped += parsedProgress.size - progress.size
        progress.forEach { progressDao.upsert(it) }

        val repositories = file.settings?.repositories.orEmpty()
            .mapNotNull { repo ->
                val kind = RepositoryKind.entries.firstOrNull { it.name == repo.kind } ?: return@mapNotNull null
                val url = repo.url.trim()
                if (url.isBlank()) null else RepositoryConfig(kind, url)
            }

        return ImportSummary(
            media = parsedMedia.size,
            episodes = episodes.size,
            progress = progress.size,
            repositories = repositories.size,
            settingsRestored = file.settings != null,
            skipped = skipped
        )
    }

    private fun Settings.toBackup() = BackupSettings(
        readerMode = readerMode.name,
        novelFontSize = novelFontSize,
        novelLineSpacing = novelLineSpacing,
        novelTheme = novelTheme.name,
        dynamicColor = dynamicColor,
        themeMode = themeMode.name,
        libraryView = libraryView.name,
        librarySort = librarySort.name,
        mangaFit = mangaFit.name,
        readingDirection = readingDirection.name,
        reduceMotion = reduceMotion,
        resumePlayback = resumePlayback,
        subtitlesEnabled = subtitlesEnabled,
        autoCheckUpdates = autoCheckUpdates,
        notifyUpdates = notifyUpdates,
        repositories = repositories.map { BackupRepository(it.kind.name, it.url) }
    )

    private fun BackupSettings.toSettings(): Settings {
        val current = Settings()
        return Settings(
            readerMode = readerMode.enumOr(ReaderMode.entries, current.readerMode),
            novelFontSize = novelFontSize.coerceIn(70, 180),
            novelLineSpacing = novelLineSpacing.coerceIn(1.2f, 2.4f),
            novelTheme = novelTheme.enumOr(NovelTheme.entries, current.novelTheme),
            dynamicColor = dynamicColor,
            themeMode = themeMode.enumOr(ThemeMode.entries, current.themeMode),
            libraryView = libraryView.enumOr(LibraryView.entries, current.libraryView),
            librarySort = librarySort.enumOr(LibrarySort.entries, current.librarySort),
            mangaFit = mangaFit.enumOr(MangaFit.entries, current.mangaFit),
            readingDirection = readingDirection.enumOr(ReadingDirection.entries, current.readingDirection),
            reduceMotion = reduceMotion,
            resumePlayback = resumePlayback,
            subtitlesEnabled = subtitlesEnabled,
            autoCheckUpdates = autoCheckUpdates,
            notifyUpdates = notifyUpdates,
            repositories = repositories.mapNotNull { repo ->
                val kind = RepositoryKind.entries.firstOrNull { it.name == repo.kind }
                    ?: return@mapNotNull null
                val url = repo.url.trim()
                if (url.isBlank()) null else RepositoryConfig(kind, url)
            }
        )
    }

    private fun MediaEntity.toBackup() = BackupMedia(
        id = id,
        kind = kind.name,
        sourceId = sourceId,
        sourceKey = sourceKey,
        title = title,
        author = author,
        description = description,
        coverUrl = coverUrl,
        status = status,
        year = year,
        genres = genres,
        totalUnits = totalUnits,
        unitLabel = unitLabel,
        inLibrary = inLibrary,
        favorite = favorite,
        addedAt = addedAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        memo = memo
    )

    private fun BackupMedia.toEntity(kind: MediaKind) = MediaEntity(
        id = id,
        kind = kind,
        sourceId = sourceId,
        sourceKey = sourceKey,
        title = title,
        author = author,
        description = description,
        coverUrl = coverUrl,
        status = status,
        year = year,
        genres = genres,
        totalUnits = totalUnits,
        unitLabel = unitLabel,
        inLibrary = inLibrary,
        favorite = favorite,
        addedAt = addedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
        updatedAt = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
        lastOpenedAt = lastOpenedAt,
        memo = memo
    )

    private fun EpisodeEntity.toBackup() = BackupEpisode(
        mediaId = mediaId,
        id = id,
        orderIndex = orderIndex,
        title = title,
        season = season,
        number = number,
        url = url,
        payload = payload,
        durationMs = durationMs,
        pageCount = pageCount,
        sourceUnitId = sourceUnitId,
        memo = memo,
        read = read
    )

    private fun BackupEpisode.toEntity() = EpisodeEntity(
        mediaId = mediaId,
        id = id,
        orderIndex = orderIndex,
        title = title,
        season = season,
        number = number,
        url = url,
        payload = payload,
        durationMs = durationMs,
        pageCount = pageCount,
        sourceUnitId = sourceUnitId,
        memo = memo,
        read = read
    )

    private fun ProgressEntity.toBackup() = BackupProgress(
        mediaId = mediaId,
        episodeId = episodeId,
        position = position,
        total = total,
        updatedAt = updatedAt
    )

    private fun BackupProgress.toEntity() = ProgressEntity(
        mediaId = mediaId,
        episodeId = episodeId,
        position = position.coerceAtLeast(0L),
        total = total.coerceAtLeast(0L),
        updatedAt = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
    )

    private inline fun <reified T : Enum<T>> String.enumOr(values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == this } ?: fallback

    companion object {
        const val FORMAT = "allfun-backup"
        const val SCHEMA_VERSION = 1
    }
}
