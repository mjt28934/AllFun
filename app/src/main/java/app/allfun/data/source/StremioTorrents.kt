package app.allfun.data.source

import java.net.URLEncoder

/**
 * Pure helpers for the torrent half of the Stremio stream protocol.
 *
 * Addons describe a torrent in several shapes: a full `magnet` link, an `infoHash`
 * plus `sources` entries such as `tracker:udp://...` and `dht:<hash>`, and a
 * `behaviorHints.filename` that identifies the intended file. These functions only
 * translate that metadata; the torrent engine decides whether peers exist.
 */
internal object StremioTorrents {

    /** Trackers are the `sources` entries with a usable transport, without their prefix. */
    fun trackers(sources: List<String>): List<String> = sources.mapNotNull { source ->
        val value = source.trim()
            .removePrefix("tracker:")
            .takeIf { it.startsWith("udp://", true) ||
                it.startsWith("http://", true) ||
                it.startsWith("https://", true) }
            ?: return@mapNotNull null
        value
    }.distinct()

    /**
     * Builds a magnet from an info hash. [displayName] is only a suggestion to peers and
     * to the file list; the hash is the identity. Returns null for a malformed hash so a
     * bad addon entry is skipped instead of starting an empty session.
     */
    fun magnet(
        infoHash: String?,
        displayName: String?,
        trackers: List<String>
    ): String? {
        val hash = infoHash?.trim()?.lowercase() ?: return null
        val valid = hash.length == 40 && hash.all { it in '0'..'9' || it in 'a'..'f' } ||
            hash.length == 32 && hash.all { it in '2'..'7' || it in 'a'..'z' }
        if (!valid) return null

        return buildString {
            append("magnet:?xt=urn:btih:").append(hash)
            displayName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append("&dn=").append(encode(it))
            }
            val allTrackers = (trackers + if (trackers.isEmpty()) PUBLIC_TRACKERS else emptyList())
                .distinct()
            allTrackers.forEach { tracker ->
                append("&tr=").append(encode(tracker))
            }
        }
    }

    /** The 40-character info hash inside a magnet, used to deduplicate releases. */
    fun identity(magnet: String): String =
        magnet.substringAfter("xt=urn:btih:", magnet)
            .substringBefore('&')
            .lowercase()

    /**
     * Resolution preference for the default torrent. A 1080p release buffers on a phone
     * far faster than a 50 GB 4K remux, while 720p is a reasonable fallback. The stream
     * picker still lists every resolution for people who want the largest file.
     */
    fun qualityRank(stream: app.allfun.data.model.StreamInfo): Int {
        val text = listOfNotNull(
            stream.name,
            stream.detail,
            stream.torrent?.fileName
        ).joinToString(" ").lowercase()
        return when {
            "1080p" in text || "1080i" in text -> 5
            "720p" in text -> 4
            "2160p" in text || "4k" in text -> 3
            "480p" in text -> 2
            else -> 1
        }
    }

    /** Used when an addon sends a bare info hash with no tracker hints. */
    private val PUBLIC_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://explodie.org:6969/announce"
    )

    /**
     * Seed count as shown by addons (Torrentio uses "👤 450"). Zero means "not advertised",
     * which is still a candidate: DHT may find peers the addon did not report.
     */
    fun seeds(vararg text: String?): Int {
        val joined = text.filterNotNull().joinToString(" ")
        val after = SEEDS_AFTER.find(joined)?.groupValues?.getOrNull(1)
        val before = SEEDS_BEFORE.find(joined)?.groupValues?.getOrNull(1)
        return (after ?: before)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** Torrentio and most addons put the label first: "👤 450" / "Seeds: 12". */
    private val SEEDS_AFTER = Regex(
        """(?:👤|seeds?|seeders?)\s*[:：]?\s*(\d+)""",
        RegexOption.IGNORE_CASE
    )

    /** A few addons write the count first: "7 seeders". */
    private val SEEDS_BEFORE = Regex(
        """(\d+)\s*(?:seeds?|seeders?)""",
        RegexOption.IGNORE_CASE
    )
}
