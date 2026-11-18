package app.allfun.ui.category

import app.allfun.data.content.ContentCategory
import app.allfun.data.content.ContentItem
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

    private val items = listOf(
        item("zeta", "Weeb Central"),
        item("Alpha", "MangaDex"),
        item("beta", "Weeb Central")
    )

    @Test
    fun featuredKeepsProviderOrder() {
        val state = CategoryViewModel.UiState(sort = CategorySort.FEATURED)
        assertEquals(
            listOf("zeta", "Alpha", "beta"),
            state.sorted(items).map { it.title }
        )
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
    fun titleDescendingReversesAlphabet() {
        val state = CategoryViewModel.UiState(sort = CategorySort.TITLE_DESC)
        assertEquals(
            listOf("zeta", "beta", "Alpha"),
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
