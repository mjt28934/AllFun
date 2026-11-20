package app.allfun.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioTorrentsTest {

    @Test
    fun buildsMagnetFromInfoHashWithEncodedNameAndTrackers() {
        val magnet = StremioTorrents.magnet(
            infoHash = "61065EA115B7CC3E8DB9FB5AB1F6F327F08BD1C9",
            displayName = "The Matrix 1999",
            trackers = listOf(
                "udp://tracker.opentrackr.org:1337/announce",
                "http://tracker.example/announce?key=a b"
            )
        )

        assertTrue(magnet!!.startsWith("magnet:?xt=urn:btih:61065ea115b7cc3e8db9fb5ab1f6f327f08bd1c9"))
        assertTrue(magnet.contains("&dn=The%20Matrix%201999"))
        assertTrue(magnet.contains("&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"))
        assertTrue(magnet.contains("&tr=http%3A%2F%2Ftracker.example%2Fannounce%3Fkey%3Da%20b"))
    }

    @Test
    fun rejectsHashesThatAreNotFortyHexOrThirtyTwoBase32() {
        assertNull(StremioTorrents.magnet("not-a-hash", null, emptyList()))
        assertNull(StremioTorrents.magnet("", null, emptyList()))
        assertNull(StremioTorrents.magnet("zzzz", null, emptyList()))
    }

    @Test
    fun acceptsThirtyTwoCharacterBase32InfoHash() {
        val magnet = StremioTorrents.magnet(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567",
            null,
            emptyList()
        )
        assertTrue(magnet!!.startsWith("magnet:?xt=urn:btih:abcdefghijklmnopqrstuvwxyz234567"))
        // A bare info hash still gets public trackers so metadata can be found.
        assertTrue(magnet.contains("&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"))
    }

    @Test
    fun identityIgnoresTrailingParametersAndCase() {
        val magnet = StremioTorrents.magnet(
            "61065EA115B7CC3E8DB9FB5AB1F6F327F08BD1C9",
            "The Matrix",
            emptyList()
        )
        assertEquals(
            "61065ea115b7cc3e8db9fb5ab1f6f327f08bd1c9",
            StremioTorrents.identity(magnet!!)
        )
    }

    @Test
    fun prefers1080pOverFourKForTheDefaultTorrent() {
        fun torrentStream(name: String) = app.allfun.data.model.StreamInfo(
            url = "magnet:?xt=urn:btih:x",
            torrent = app.allfun.data.model.TorrentRef(magnet = "magnet:x"),
            name = name
        )
        val fourK = torrentStream("Torrentio 4k HDR")
        val hd = torrentStream("Torrentio 1080p")
        val sevenTwenty = torrentStream("Torrentio 720p")
        assertTrue(StremioTorrents.qualityRank(hd) > StremioTorrents.qualityRank(sevenTwenty))
        assertTrue(StremioTorrents.qualityRank(sevenTwenty) > StremioTorrents.qualityRank(fourK))
    }

    @Test
    fun stripsTrackerPrefixAndIgnoresDhtAndWebSocketHints() {
        val trackers = StremioTorrents.trackers(
            listOf(
                "tracker:udp://tracker.opentrackr.org:1337/announce",
                "tracker:https://tracker.example/announce",
                "dht:61065ea115b7cc3e8db9fb5ab1f6f327f08bd1c9",
                "wss://tracker.webtorrent.dev",
                "",
                "tracker:udp://tracker.opentrackr.org:1337/announce"
            )
        )
        assertEquals(
            listOf(
                "udp://tracker.opentrackr.org:1337/announce",
                "https://tracker.example/announce"
            ),
            trackers
        )
    }

    @Test
    fun parsesSeedCountsFromTorrentioStyleLabels() {
        assertEquals(450, StremioTorrents.seeds("👤 450 💾 18.45 GB"))
        assertEquals(12, StremioTorrents.seeds("Seeds: 12"))
        assertEquals(7, StremioTorrents.seeds("7 seeders"))
        assertEquals(0, StremioTorrents.seeds("no peers listed"))
        assertEquals(0, StremioTorrents.seeds(null, ""))
    }
}
