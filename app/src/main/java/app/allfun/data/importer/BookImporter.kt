package app.allfun.data.importer

import android.content.Context
import android.net.Uri
import app.allfun.data.Ids
import app.allfun.data.db.EpisodeDao
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaDao
import app.allfun.data.db.MediaEntity
import app.allfun.data.epub.EpubParser
import app.allfun.data.model.MediaKind
import app.allfun.data.source.SourceIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Imports EPUB files into the app's private book storage and turns them into library rows. */
class BookImporter(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao
) {
    suspend fun importUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = queryDisplayName(uri) ?: "imported.epub"
        val input = resolver.openInputStream(uri) ?: error("Could not open the selected file")
        importStream(input, name)
    }

    suspend fun importAsset(assetPath: String): String = withContext(Dispatchers.IO) {
        context.assets.open(assetPath).use { input ->
            importStream(input, assetPath.substringAfterLast('/'))
        }
    }

    private suspend fun importStream(input: InputStream, fileName: String): String {
        val mediaId = Ids.fresh()
        val temp = File(context.cacheDir, "import-$mediaId.epub")
        input.use { source ->
            temp.outputStream().use { target -> source.copyTo(target) }
        }
        return try {
            val book = EpubParser.extract(temp, bookDir(mediaId))
            val media = MediaEntity(
                id = mediaId,
                kind = MediaKind.NOVEL,
                sourceId = SourceIds.LOCAL_NOVEL,
                sourceKey = mediaId,
                title = book.title.ifBlank { fileName.substringBeforeLast('.') },
                author = book.author,
                description = null,
                coverUrl = book.coverFile?.let { Uri.fromFile(it).toString() },
                status = "Imported",
                totalUnits = book.chapters.size,
                unitLabel = "Chapter",
                inLibrary = true
            )
            mediaDao.upsert(media)
            episodeDao.upsertAll(
                book.chapters.mapIndexed { index, chapter ->
                    EpisodeEntity(
                        mediaId = mediaId,
                        id = Ids.episode(mediaId, chapter.id),
                        orderIndex = index,
                        title = chapter.title,
                        number = (index + 1).toDouble(),
                        url = chapter.href,
                        sourceUnitId = chapter.id
                    )
                }
            )
            mediaId
        } finally {
            temp.delete()
        }
    }

    fun bookDir(mediaId: String): File = File(context.filesDir, "books/$mediaId")

    fun chapterUri(mediaId: String, href: String): String =
        Uri.fromFile(File(bookDir(mediaId), href)).toString()

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}
