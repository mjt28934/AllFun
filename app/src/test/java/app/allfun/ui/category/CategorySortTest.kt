package app.allfun.ui.category

import app.allfun.data.content.CategoryFeed
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.ContentItem
import app.allfun.data.content.ContentShelf
import app.allfun.data.content.ContentSourceRef
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySortTest {

    private fun item(title: String, sourceName: String, key: String = title) = ContentItem(
        title = title,
        stub = MediaStub(key = key, title = title),
        category = ContentCategory.MANGA,
        sources = listOf(
            ContentSourceRef(
                sourceId = sourceName.lowercase(),
                sourceName = sourceName,
                key = key,
                type = SourceType.MANGA
            )
        )
    )

    private val zeta = item("zeta", "Weeb Central")
    private val alpha = item("Alpha", "MangaDex")
    private val beta = item("beta", "Weeb Central")

    private val items = listOf(zeta, alpha, beta)

    /** Popular shelf order: beta, zeta. Latest shelf order: alpha, zeta. */
    private fun feed() = CategoryFeed(
        category = ContentCategory.MANGA,
        shelves = listOf(
            ContentShelf(
                id = "MANGA:POPULAR",
                title = "Popular",
                items = listOf(beta, zeta)
            ),
            ContentShelf(
                id = "MANGA:LATEST",
                title = "Latest updates",
                items = listOf(alpha, zeta)
            )
        ),
        items = items,
        statuses = emptyList(),
        sources = emptyList(),
        canLoadMore = false
    )

    @Test
    fun popularFollowsThePopularShelfThenTheRest() {
        val state = CategoryViewModel.UiState(feed = feed(), sort = CategorySort.POPULAR)
        assertEquals(
            listOf("beta", "zeta", "Alpha"),
            state.sorted(items).map { it.title }
        )
    }

    @Test
    fun latestFollowsTheLatestShelfThenTheRest() {
        val state = CategoryViewModel.UiState(feed = feed(), sort = CategorySort.LATEST)
        assertEquals(
            listOf("Alpha", "zeta", "beta"),
            state.sorted(items).map { it.title }
        )
    }

    @Test
    fun laneSortKeepsProviderOrderWithoutShelves() {
        val state = CategoryViewModel.UiState(sort = CategorySort.LATEST)
        assertEquals(
            listOf("zeta", "Alpha", "beta"),
            state.sorted(items).map { it.title }
        )
    }

    @Test
    fun laneSortAppliesToFilteredAndSearchResults() {
        val state = CategoryViewModel.UiState(feed = feed(), sort = CategorySort.POPULAR)
        val filtered = state.sorted(listOf(alpha, beta))
        assertEquals(listOf("beta", "Alpha"), filtered.map { it.title })
    }

    @Test
    fun titleAscendingIsCaseInsensitive() {
        val state = CategoryViewModel.UiState(sort = CategorySort.TITLE_ASC)
        assertEquals(
            listOf("Alpha", "beta", "zeta"),
            state.sorted(items).map { it.title }
        )
    }

    @Test
    fun sourceGroupsProvidersThenTitles() {
        val state = CategoryViewModel.UiState(sort = CategorySort.SOURCE)
        assertEquals(
            listOf("Alpha", "beta", "zeta"),
            state.sorted(items).map { it.title }
        )
        assertEquals(
            listOf("MangaDex", "Weeb Central", "Weeb Central"),
            state.sorted(items).map { it.sourceLabel }
        )
    }
}
