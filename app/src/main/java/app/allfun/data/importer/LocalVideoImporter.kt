package app.allfun.data.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.allfun.data.Ids
import app.allfun.data.db.EpisodeDao
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaDao
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.data.source.SourceIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportSummary(val items: Int, val episodes: Int, val message: String)

/**
 * Imports a folder tree of videos. A subfolder with several videos becomes a series;
 * loose files become movies. Permissions on the tree are persisted so playback keeps working.
 */
class LocalVideoImporter(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao
) {

    private val videoExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "m2ts", "mpg", "mpeg", "flv", "3gp"
    )

    suspend fun importTree(treeUri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ImportSummary(0, 0, "That folder could not be opened")
        val rootName = root.name.orEmpty()

        val videos = mutableListOf<DocumentFile>()
        collect(root, videos, depth = 0)

        if (videos.isEmpty()) {
            return@withContext ImportSummary(0, 0, "No video files found in that folder")
        }

        val grouped = videos.groupBy { file ->
            val parent = file.parentFile?.name.orEmpty()
            if (parent.isBlank() || parent == rootName) "" else parent
        }

        var itemCount = 0
        var episodeCount = 0
        for ((folder, files) in grouped) {
            val sorted = files.sortedBy { it.name.orEmpty() }
            if (folder.isNotEmpty() && sorted.size >= 2) {
                val mediaId = Ids.media(SourceIds.LOCAL_VIDEO, treeUri.toString() + "#" + folder)
                mediaDao.upsert(
                    MediaEntity(
                        id = mediaId,
                        kind = MediaKind.SERIES,
                        sourceId = SourceIds.LOCAL_VIDEO,
                        sourceKey = treeUri.toString() + "#" + folder,
                        title = cleanTitle(folder),
                        status = "Local",
                        totalUnits = sorted.size,
                        unitLabel = "Episode",
                        inLibrary = true
                    )
                )
                val episodes = sorted.mapIndexed { index, file ->
                    val name = file.name.orEmpty()
                    EpisodeEntity(
                        mediaId = mediaId,
                        id = Ids.episode(mediaId, file.uri.toString()),
                        orderIndex = index,
                        title = extractEpisodeTitle(name, index),
                        season = 1,
                        number = episodeNumber(name) ?: (index + 1).toDouble(),
                        url = file.uri.toString(),
                        sourceUnitId = file.uri.toString()
                    )
                }
                episodeDao.upsertAll(episodes)
                itemCount++
                episodeCount += episodes.size
            } else {
                for (file in sorted) {
                    val name = file.name.orEmpty()
                    val mediaId = Ids.media(SourceIds.LOCAL_VIDEO, file.uri.toString())
                    mediaDao.upsert(
                        MediaEntity(
                            id = mediaId,
                            kind = MediaKind.MOVIE,
                            sourceId = SourceIds.LOCAL_VIDEO,
                            sourceKey = file.uri.toString(),
                            title = cleanTitle(name.substringBeforeLast('.')),
                            status = "Local",
                            totalUnits = 1,
                            unitLabel = "Video",
                            inLibrary = true
                        )
                    )
                    episodeDao.upsertAll(
                        listOf(
                            EpisodeEntity(
                                mediaId = mediaId,
                                id = Ids.episode(mediaId, "main"),
                                orderIndex = 0,
                                title = cleanTitle(name.substringBeforeLast('.')),
                                url = file.uri.toString(),
                                sourceUnitId = "main"
                            )
                        )
                    )
                    itemCount++
                    episodeCount++
                }
            }
        }
        ImportSummary(
            items = itemCount,
            episodes = episodeCount,
            message = "Imported $itemCount title${if (itemCount == 1) "" else "s"} ($episodeCount video${if (episodeCount == 1) "" else "s"})"
        )
    }

    private fun collect(dir: DocumentFile, out: MutableList<DocumentFile>, depth: Int) {
        if (depth > 4) return
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                collect(child, out, depth + 1)
            } else {
                val name = child.name.orEmpty()
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in videoExtensions) out.add(child)
            }
        }
    }

    private fun cleanTitle(value: String): String =
        value.replace('_', ' ')
            .replace('.', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(' ')
            .joinToString(" ") { word ->
                if (word.length > 1 && word == word.uppercase()) word
                else word.replaceFirstChar { it.uppercase() }
            }

    private fun episodeNumber(name: String): Double? =
        Regex("""(?:^|[^\d])(\d{1,3})(?:[^\d]|$)""").find(name)
            ?.groupValues?.get(1)?.toDoubleOrNull()

    private fun extractEpisodeTitle(name: String, index: Int): String {
        val base = cleanTitle(name.substringBeforeLast('.'))
        return base.ifBlank { "Episode ${index + 1}" }
    }
}
