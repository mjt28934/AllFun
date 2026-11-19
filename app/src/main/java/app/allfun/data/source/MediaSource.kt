package app.allfun.data.source

import app.allfun.data.model.Catalog
import app.allfun.data.model.MediaDetails
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import app.allfun.data.model.StreamInfo
import app.allfun.data.model.UnitStub

/**
 * The extension contract. A manga source, an EPUB library and a video addon all look the
 * same to the rest of the app; the optional hooks below are simply ignored by kinds that
 * do not implement them.
 *
 * Capability flags are part of the contract on purpose. The UI asks the source what it can
 * actually do instead of assuming every source has search, catalogs or streams; a missing
 * capability hides the control rather than producing a request the source cannot answer.
 */
abstract class MediaSource {
    abstract val id: String
    abstract val name: String
    abstract val type: SourceType

    /** True only when [search] returns real results for this source. */
    open val supportsSearch: Boolean get() = true

    /** True when the source has discoverable catalogs. */
    open val supportsCatalogs: Boolean get() = true

    open fun catalogs(): List<Catalog> = listOf(Catalog("popular", "Popular"))

    open suspend fun browse(catalog: String?, page: Int): List<MediaStub> = emptyList()

    open suspend fun search(query: String, page: Int): List<MediaStub> = emptyList()

    abstract suspend fun details(key: String): MediaDetails

    abstract suspend fun units(key: String): List<UnitStub>

    /** Image URLs for one manga chapter. */
    open suspend fun pages(key: String, unitId: String): List<String> = emptyList()

    /** Raw chapter text for text-first sources. */
    open suspend fun chapterText(key: String, unitId: String): String? = null

    /**
     * Chapter body as HTML for sources that produce markup. When non-null this takes
     * precedence over [chapterText] and is rendered in the reader's WebView.
     */
    open suspend fun chapterBody(key: String, unitId: String): String? = null

    /**
     * True when chapters are fetched over the network through [chapterText] instead of
     * being read from a local file. The reader uses this to decide between loading a
     * `file://` URL and rendering fetched HTML.
     */
    open val chaptersAreRemote: Boolean get() = false

    /** Playable stream for one movie/episode. */
    open suspend fun stream(key: String, unitId: String, payload: String?): StreamInfo? = null

    /**
     * True when AllFun can really save this source's content to disk: manga pages, remote
     * novel chapters, or direct HTTP video files. A source that only exposes browser pages
     * or torrents leaves this false, and the UI hides Download instead of failing later.
     */
    open val supportsDownloads: Boolean get() = when (type) {
        SourceType.MANGA -> true
        SourceType.NOVEL -> chaptersAreRemote
        SourceType.VIDEO -> true
    }

    /** Human-readable list of what this source can do, used by source management UI. */
    fun capabilityLabels(): List<String> = buildList {
        if (supportsCatalogs && catalogs().isNotEmpty()) add("Browse")
        if (supportsSearch) add("Search")
        when (type) {
            SourceType.MANGA -> add("Chapter pages")
            SourceType.NOVEL -> add("Chapter text")
            SourceType.VIDEO -> add("Streams")
        }
        if (supportsDownloads) add("Offline")
    }
}

object SourceIds {
    const val LOCAL_NOVEL = "local-novel"
    const val LOCAL_VIDEO = "local-video"
}
