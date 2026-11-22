package app.allfun.ui.detail

import app.allfun.data.db.EpisodeEntity

/** Chapter ordering offered by the chapter-list sheet, matching Komikku's options. */
enum class ChapterSort(val label: String) {
    SOURCE("Source order"),
    NEWEST("Chapter number, newest first"),
    OLDEST("Chapter number, oldest first")
}

enum class ChapterFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    DOWNLOADED("Downloaded")
}

/**
 * Source order is whatever the provider returned, which is already the order readers
 * expect. Numeric sorts fall back to the stored index when chapter numbers repeat.
 */
fun orderedChapters(chapters: List<EpisodeEntity>, sort: ChapterSort): List<EpisodeEntity> =
    when (sort) {
        ChapterSort.SOURCE -> chapters
        ChapterSort.NEWEST -> chapters.sortedWith(
            compareByDescending<EpisodeEntity> { it.number }.thenByDescending { it.orderIndex }
        )
        ChapterSort.OLDEST -> chapters.sortedWith(
            compareBy<EpisodeEntity> { it.number }.thenBy { it.orderIndex }
        )
    }

fun filteredChapters(
    chapters: List<EpisodeEntity>,
    filter: ChapterFilter,
    downloadedIds: Set<String>
): List<EpisodeEntity> = when (filter) {
    ChapterFilter.ALL -> chapters
    ChapterFilter.UNREAD -> chapters.filter { !it.read }
    ChapterFilter.DOWNLOADED -> chapters.filter { it.id in downloadedIds }
}
