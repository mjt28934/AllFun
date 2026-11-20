package app.allfun.data.model

/** The four kinds of media AllFun handles. */
enum class MediaKind(val label: String, val unit: String) {
    MANGA("Manga", "Chapter"),
    NOVEL("Novel", "Chapter"),
    MOVIE("Movie", "Video"),
    SERIES("Series", "Episode");

    val isVideo: Boolean get() = this == MOVIE || this == SERIES
    val isReading: Boolean get() = this == MANGA || this == NOVEL
}

/** What a source produces. One source feeds exactly one [SourceType]. */
enum class SourceType(val label: String) {
    MANGA("Manga"),
    NOVEL("Novels"),
    VIDEO("Video")
}

/** A card-sized result, cheap to fetch in bulk. */
data class MediaStub(
    val key: String,
    val title: String,
    val coverUrl: String? = null,
    val subtitle: String? = null,
    val description: String? = null
)

/** Everything a detail screen needs. [kind] lets video sources distinguish movies from series. */
data class MediaDetails(
    val key: String,
    val title: String,
    val kind: MediaKind? = null,
    val author: String? = null,
    /** Separate creator credited by sources that actually provide one (manga artist). */
    val artist: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val backdropUrl: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val totalUnits: Int? = null,
    val unitLabel: String? = null,
    /** Source-private metadata (extension memo) needed to fetch chapters later. */
    val sourceMemo: String? = null
)

/** A chapter / episode. [url] and [payload] are source-defined handles. */
data class UnitStub(
    val id: String,
    val index: Int,
    val title: String,
    val season: Int = 0,
    val number: Double = 0.0,
    val url: String? = null,
    val payload: String? = null,
    val durationMs: Long? = null,
    val pageCount: Int? = null,
    /** Source-private metadata (chapter memo) needed to fetch pages later. */
    val memo: String? = null
)

/**
 * A playable BitTorrent stream. Stremio addons identify the torrent with either an
 * `infoHash` or a ready-made magnet; [fileIndex] selects one file inside a multi-file
 * torrent and [fileName] is the addon's filename hint when no index was given.
 */
data class TorrentRef(
    val magnet: String,
    val fileIndex: Int? = null,
    val fileName: String? = null,
    /** Advertised seed count, used to rank torrents from several addons. */
    val seeds: Int = 0
)

data class StreamInfo(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitleUrl: String? = null,
    /** Non-null when [url] is a magnet and playback must go through the torrent engine. */
    val torrent: TorrentRef? = null,
    /** Addon that supplied this stream, shown when several addons answer a title. */
    val addonName: String? = null,
    /** Addon's own label, such as "Torrentio · 1080p". */
    val name: String? = null,
    /** Longer addon description: release name, seeds and size for a torrent. */
    val detail: String? = null
)

data class Catalog(val id: String, val name: String)
