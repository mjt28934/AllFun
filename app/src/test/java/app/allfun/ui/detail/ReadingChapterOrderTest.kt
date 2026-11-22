package app.allfun.ui.detail

import app.allfun.data.db.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingChapterOrderTest {

    private fun chapter(
        id: String,
        number: Double,
        index: Int,
        read: Boolean = false
    ) = EpisodeEntity(
        mediaId = "media",
        id = id,
        orderIndex = index,
        title = "Chapter $number",
        number = number,
        read = read
    )

    private val sourceOrder = listOf(
        chapter("c10", 10.0, 0, read = true),
        chapter("c9", 9.0, 1),
        chapter("c8", 8.0, 2),
        chapter("c8b", 8.0, 3, read = true)
    )

    @Test
    fun sourceOrderKeepsProviderOrder() {
        assertEquals(
            listOf("c10", "c9", "c8", "c8b"),
            orderedChapters(sourceOrder, ChapterSort.SOURCE).map { it.id }
        )
    }

    @Test
    fun newestSortsByNumberThenStoredIndex() {
        assertEquals(
            listOf("c10", "c9", "c8b", "c8"),
            orderedChapters(sourceOrder, ChapterSort.NEWEST).map { it.id }
        )
    }

    @Test
    fun oldestSortsByNumberThenStoredIndex() {
        assertEquals(
            listOf("c8", "c8b", "c9", "c10"),
            orderedChapters(sourceOrder, ChapterSort.OLDEST).map { it.id }
        )
    }

    @Test
    fun unreadFilterKeepsOnlyUnreadChapters() {
        assertEquals(
            listOf("c9", "c8"),
            filteredChapters(sourceOrder, ChapterFilter.UNREAD, emptySet()).map { it.id }
        )
    }

    @Test
    fun downloadedFilterUsesCompletedIds() {
        assertEquals(
            listOf("c10", "c8b"),
            filteredChapters(sourceOrder, ChapterFilter.DOWNLOADED, setOf("c10", "c8b"))
                .map { it.id }
        )
    }

    @Test
    fun allFilterDoesNotDropAnything() {
        assertEquals(
            sourceOrder,
            filteredChapters(sourceOrder, ChapterFilter.ALL, setOf("c10"))
        )
    }
}
