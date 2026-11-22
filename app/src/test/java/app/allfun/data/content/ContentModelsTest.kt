package app.allfun.data.content

import app.allfun.data.model.MediaDetails
import app.allfun.data.model.MediaKind
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The aggregation layer's honesty rules live in these pure helpers: titles dedupe on a
 * normalised key, and format badges only appear when a provider actually supplied the
 * metadata that implies them.
 */
class ContentModelsTest {

    private fun item(
        title: String,
        category: ContentCategory = ContentCategory.MANGA,
        format: String? = null
    ) = ContentItem(
        title = title,
        stub = MediaStub(key = "k-$title", title = title),
        category = category,
        sources = listOf(
            ContentSourceRef("source", "Source", "k-$title", SourceType.MANGA, format)
        )
    )

    @Test
    fun `title key ignores case punctuation and spacing`() {
        assertEquals(titleKey("Solo Leveling"), titleKey("  solo   LEVELING! "))
        assertEquals(titleKey("Re:Zero"), titleKey("re zero"))
        assertEquals(titleKey("My Hero Academia"), titleKey("My-Hero_Academia"))
    }

    @Test
    fun `title key keeps genuinely different titles apart`() {
        assertTrue(titleKey("Berserk") != titleKey("Berserk! 2"))
        assertTrue(titleKey("One Piece") != titleKey("One-Punch Man"))
    }

    @Test
    fun `source type maps to exactly one category`() {
        assertEquals(ContentCategory.MOVIES, ContentCategory.of(SourceType.VIDEO))
        assertEquals(ContentCategory.BOOKS, ContentCategory.of(SourceType.NOVEL))
        assertEquals(ContentCategory.MANGA, ContentCategory.of(SourceType.MANGA))
        assertEquals(3, ContentCategory.entries.size)
    }

    @Test
    fun `media kind maps to its category`() {
        assertEquals(ContentCategory.MOVIES, MediaKind.MOVIE.category())
        assertEquals(ContentCategory.MOVIES, MediaKind.SERIES.category())
        assertEquals(ContentCategory.BOOKS, MediaKind.NOVEL.category())
        assertEquals(ContentCategory.MANGA, MediaKind.MANGA.category())
    }

    @Test
    fun `movie format comes from declared catalog format`() {
        val movie = item("Dune", ContentCategory.MOVIES, format = "Movie")
        assertEquals("Movie", movie.format())
        val series = item("Severance", ContentCategory.MOVIES, format = "Series")
        assertEquals("Series", series.format())
    }

    @Test
    fun `movie format falls back to details kind`() {
        val movie = item("Dune", ContentCategory.MOVIES)
        assertEquals(
            "Movie",
            movie.format(MediaDetails(key = "k", title = "Dune", kind = MediaKind.MOVIE))
        )
        assertEquals(
            "Series",
            movie.format(MediaDetails(key = "k", title = "Dune", kind = MediaKind.SERIES))
        )
    }

    @Test
    fun `no format badge when provider declared nothing`() {
        assertNull(item("Dune", ContentCategory.MOVIES).format())
        assertNull(item("Untitled manga").format())
    }

    @Test
    fun `manga format is inferred only from real genres`() {
        val manhwa = MediaDetails(
            key = "k",
            title = "Solo Leveling",
            genres = listOf("Action", "Manhwa")
        )
        assertEquals("Manhwa", item("Solo Leveling").format(manhwa))

        val plain = MediaDetails(key = "k", title = "Berserk", genres = listOf("Action"))
        assertNull(item("Berserk").format(plain))
    }

    @Test
    fun `book format distinguishes light novels`() {
        val lightNovel = MediaDetails(
            key = "k",
            title = "Re:Zero",
            kind = MediaKind.NOVEL,
            genres = listOf("Light Novel", "Fantasy")
        )
        assertEquals("Light novel", item("Re:Zero", ContentCategory.BOOKS).format(lightNovel))

        val novel = MediaDetails(key = "k", title = "Dune", kind = MediaKind.NOVEL)
        assertEquals("Novel", item("Dune", ContentCategory.BOOKS).format(novel))
    }

    @Test
    fun `format filter matches only when metadata agrees`() {
        val manhwa = item("Solo Leveling").format(
            MediaDetails(key = "k", title = "Solo Leveling", genres = listOf("Manhwa"))
        )!!
        // Without details the facet cannot be confirmed, so an unconfirmed title is hidden
        // rather than assumed to match.
        assertFalse(item("Solo Leveling").matchesFormat("Manhwa", null))
        assertFalse(item("Solo Leveling").matchesFormat("Manga", null))
        assertTrue(item("Solo Leveling").matchesFormat(null, null))
        assertTrue(
            item("Solo Leveling").matchesFormat(
                manhwa,
                MediaDetails(key = "k", title = "Solo Leveling", genres = listOf("Manhwa"))
            )
        )
    }

    @Test
    fun `preview opens from the card without waiting for provider metadata`() {
        val series = ContentItem(
            title = "Severance",
            stub = MediaStub(
                key = "series|tt11280740",
                title = "Severance",
                coverUrl = "https://example/poster.jpg"
            ),
            category = ContentCategory.MOVIES,
            sources = listOf(
                ContentSourceRef(
                    "catalogs", "Streaming Catalogs", "series|tt11280740", SourceType.VIDEO
                )
            )
        )
        val preview = series.previewDetails()
        // The kind comes from the id the provider published, not from metadata it lacks.
        assertEquals(MediaKind.SERIES, preview.kind)
        assertEquals("Episode", preview.unitLabel)
        assertEquals("Severance", preview.title)
        assertEquals("https://example/poster.jpg", preview.coverUrl)

        val movie = series.copy(
            stub = MediaStub(key = "movie|tt0133093", title = "The Matrix"),
            sources = listOf(
                ContentSourceRef(
                    "catalogs", "Streaming Catalogs", "movie|tt0133093", SourceType.VIDEO
                )
            )
        )
        assertEquals(MediaKind.MOVIE, movie.previewDetails().kind)

        val manga = item("Berserk")
        assertEquals(MediaKind.MANGA, manga.previewDetails().kind)
        assertEquals("Chapter", manga.previewDetails().unitLabel)
    }

    @Test
    fun `alternative providers are counted without inventing one`() {
        val single = item("Berserk")
        assertEquals(0, single.alternateCount)
        assertEquals("Source", single.sourceLabel)

        val merged = single.copy(
            sources = single.sources + ContentSourceRef(
                "source-2", "Second source", "b-2", SourceType.MANGA
            )
        )
        assertEquals(1, merged.alternateCount)
        assertEquals("Source", merged.sourceLabel)
    }
}
