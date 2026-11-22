package app.allfun.data.content

import app.allfun.data.model.MediaDetails
import app.allfun.data.model.MediaKind
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import java.util.Locale

/**
 * The three entertainment destinations the app is organised around.
 *
 * Extensions are infrastructure: every enabled source contributes to exactly one of
 * these categories by its [SourceType], and no source ever becomes a destination.
 */
enum class ContentCategory(
    val label: String,
    val shortLabel: String,
    val sourceType: SourceType,
    val tagline: String
) {
    MOVIES(
        label = "Movies & Shows",
        shortLabel = "Movies",
        sourceType = SourceType.VIDEO,
        tagline = "Films and series from your video add-ons"
    ),
    BOOKS(
        label = "Books & Novels",
        shortLabel = "Books",
        sourceType = SourceType.NOVEL,
        tagline = "Ebooks and web novels from every book source"
    ),
    MANGA(
        label = "Manhwa & Manga",
        shortLabel = "Manga",
        sourceType = SourceType.MANGA,
        tagline = "Manga, manhwa and comics from installed extensions"
    );

    val isVideo: Boolean get() = this == MOVIES
    val isReading: Boolean get() = this != MOVIES

    companion object {
        fun of(type: SourceType): ContentCategory = when (type) {
            SourceType.VIDEO -> MOVIES
            SourceType.NOVEL -> BOOKS
            SourceType.MANGA -> MANGA
        }

        fun fromName(value: String?): ContentCategory? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** Category a stored [MediaKind] belongs to. */
fun MediaKind.category(): ContentCategory = when (this) {
    MediaKind.MOVIE, MediaKind.SERIES -> ContentCategory.MOVIES
    MediaKind.NOVEL -> ContentCategory.BOOKS
    MediaKind.MANGA -> ContentCategory.MANGA
}

fun SourceType.category(): ContentCategory = ContentCategory.of(this)

/**
 * One concrete provider for a title: a source id plus the source's own key for it.
 * [format] is a hint the provider actually declared (for example a Stremio catalog
 * `type`), never a guess.
 */
data class ContentSourceRef(
    val sourceId: String,
    val sourceName: String,
    val key: String,
    val type: SourceType,
    val format: String? = null
)

/**
 * A result a provider really returned, merged with other providers that returned the
 * same title. [sources] keeps every confirmed provider so the detail screen can offer
 * a source selector without repeat searches.
 */
data class ContentItem(
    val title: String,
    val stub: MediaStub,
    val category: ContentCategory,
    val sources: List<ContentSourceRef>
) {
    val primary: ContentSourceRef get() = sources.first()
    val sourceLabel: String get() = primary.sourceName
    val alternateCount: Int get() = (sources.size - 1).coerceAtLeast(0)

    /**
     * The kind this card really represents. Video addons encode the declared type in the
     * key (`movie|tt…` / `series|tt…`), which is the provider's own answer, not a guess.
     */
    val kind: MediaKind
        get() = when (category) {
            ContentCategory.MANGA -> MediaKind.MANGA
            ContentCategory.BOOKS -> MediaKind.NOVEL
            ContentCategory.MOVIES -> if (
                primary.key.startsWith("series|", ignoreCase = true) ||
                primary.format.equals("Series", ignoreCase = true)
            ) {
                MediaKind.SERIES
            } else {
                MediaKind.MOVIE
            }
        }
}

/**
 * A local detail page built from the catalogue card. Opening a category result writes
 * this to the database first, so the screen appears as fast as a library row and the
 * provider's metadata fills in behind it instead of blocking navigation.
 */
fun ContentItem.previewDetails(): MediaDetails = MediaDetails(
    key = primary.key,
    title = stub.title,
    kind = kind,
    description = stub.description,
    coverUrl = stub.coverUrl,
    unitLabel = kind.unit
)

/** One shelf of real provider results (Popular, Trending, Latest updates, ...). */
data class ContentShelf(
    val id: String,
    val title: String,
    val items: List<ContentItem>
)

/** Truthful state of one installed source for a category or a search. */
enum class ProviderState(val label: String) {
    WORKING("Working"),
    LOADING("Loading"),
    NO_CONTENT("No content"),
    TEMPORARILY_UNAVAILABLE("Temporarily unavailable"),
    AUTH_REQUIRED("Sign-in required"),
    INVALID_RESPONSE("Invalid response"),
    DISABLED("Disabled"),
    UNSUPPORTED("Unsupported")
}

/** Truthful state of one installed source for a category or a search. */
data class ProviderStatus(
    val sourceId: String,
    val sourceName: String,
    val type: SourceType,
    val available: Boolean,
    val itemCount: Int = 0,
    /** Human-readable reason when [available] is false or a request failed. */
    val message: String? = null,
    /** Original exception text, shown only when the user asks for details. */
    val diagnostics: String? = null
) {
    val state: ProviderState get() = when {
        available && itemCount > 0 -> ProviderState.WORKING
        available -> ProviderState.NO_CONTENT
        message == null -> ProviderState.DISABLED
        message.contains("sign in", true) ||
            message.contains("sign-in", true) ||
            message.contains("auth", true) ||
            message.contains("token", true) ||
            message.contains("log in", true) -> ProviderState.AUTH_REQUIRED
        message.contains("web page", true) ||
            message.contains("could not read", true) ||
            message.contains("invalid response", true) ||
            message.contains("malformed", true) ||
            message.contains("unsupported response", true) -> ProviderState.INVALID_RESPONSE
        message.contains("missing", true) ||
            message.contains("not installed", true) ||
            message.contains("not loaded", true) -> ProviderState.DISABLED
        message.contains("does not support", true) ||
            message.contains("not supported", true) -> ProviderState.UNSUPPORTED
        else -> ProviderState.TEMPORARILY_UNAVAILABLE
    }
}

data class ContentSourceInfo(
    val id: String,
    val name: String,
    val type: SourceType
)

data class CategoryFeed(
    val category: ContentCategory,
    val shelves: List<ContentShelf>,
    val items: List<ContentItem>,
    val statuses: List<ProviderStatus>,
    val sources: List<ContentSourceInfo>,
    val canLoadMore: Boolean
)

/** Search results are grouped by category; each category is already deduplicated. */
data class SearchResponse(
    val query: String,
    val results: Map<ContentCategory, List<ContentItem>>,
    val statuses: List<ProviderStatus>
) {
    val total: Int get() = results.values.sumOf { it.size }
}

/** One additional page of real results, used by "Load more" in a category grid. */
data class ContentPage(
    val items: List<ContentItem>,
    val statuses: List<ProviderStatus>,
    val hasMore: Boolean
)

/**
 * One selectable provider on a detail screen. Only [available] sources confirmed a
 * matching title; the others carry the real reason they could not be used.
 */
data class SourceOption(
    val sourceId: String,
    val sourceName: String,
    val key: String?,
    val category: ContentCategory,
    val isPrimary: Boolean,
    val isPreferred: Boolean,
    val available: Boolean,
    val statusLabel: String?,
    val message: String? = null
)

/**
 * Real metadata a provider returned while the filter sheet was being prepared. Used
 * for genre/year/author/format facets; a missing value is simply not offered.
 */
data class ContentInfo(
    val refId: String,
    val details: MediaDetails?
)

fun refId(ref: ContentSourceRef): String = "${ref.sourceId}|${ref.key}"

fun refId(sourceId: String, key: String): String = "$sourceId|$key"

/** Normalises a title only for duplicate detection; display titles are untouched. */
fun titleKey(title: String): String =
    title.lowercase(Locale.ROOT)
        .replace(Regex("""[\s\p{Punct}]+"""), " ")
        .trim()

/**
 * Category-appropriate format label. Returns null when the provider did not say, so
 * the UI can omit the badge instead of inventing one.
 */
fun ContentItem.format(details: MediaDetails? = null): String? {
    if (category == ContentCategory.MOVIES) {
        when (details?.kind) {
            MediaKind.SERIES -> return "Series"
            MediaKind.MOVIE -> return "Movie"
            else -> Unit
        }
        return sources.firstNotNullOfOrNull { ref ->
            ref.format?.takeIf { it.isNotBlank() }
        }
    }
    if (details == null) return null
    val genres = details.genres.joinToString(" ").lowercase(Locale.ROOT)
    return when (category) {
        ContentCategory.MANGA -> when {
            genres.contains("manhwa") -> "Manhwa"
            genres.contains("manhua") -> "Manhua"
            genres.contains("comic") -> "Comic"
            genres.contains("manga") -> "Manga"
            else -> null
        }
        ContentCategory.BOOKS -> when {
            genres.contains("light novel") -> "Light novel"
            details.kind == MediaKind.NOVEL -> "Novel"
            else -> null
        }
        ContentCategory.MOVIES -> null
    }
}

/** True when a user-selected format facet matches this item's real metadata. */
fun ContentItem.matchesFormat(format: String?, details: MediaDetails?): Boolean {
    if (format.isNullOrBlank()) return true
    val actual = this.format(details) ?: return false
    return actual.equals(format, ignoreCase = true)
}
