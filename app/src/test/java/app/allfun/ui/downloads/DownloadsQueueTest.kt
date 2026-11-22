package app.allfun.ui.downloads

import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue screen is the observable part of the download queue, so its pure state rules
 * are pinned here: order comes from the persisted [DownloadEntity.queueOrder], and the
 * two ways of pausing (one item, or the whole queue) read the same to the user.
 */
class DownloadsQueueTest {

    private fun row(
        key: String,
        mediaId: String = "media-1",
        mediaTitle: String = "Slow Test Movie",
        status: DownloadStatus = DownloadStatus.QUEUED,
        note: String? = null,
        order: Long = 0L,
        createdAt: Long = 0L,
        unitNumber: Double = 0.0
    ) = DownloadEntity(
        key = key,
        mediaId = mediaId,
        unitId = key,
        kind = MediaKind.MOVIE,
        mediaTitle = mediaTitle,
        unitTitle = key,
        status = status,
        note = note,
        queueOrder = order,
        createdAt = createdAt,
        unitNumber = unitNumber
    )

    @Test
    fun `queue order comes from the persisted position not creation time`() {
        val state = DownloadsUiState(
            rows = listOf(
                row("c", order = 3, createdAt = 3),
                row("a", order = 1, createdAt = 300),
                row("b", order = 2, createdAt = 200)
            )
        )
        // Moving an item to the top rewrites queueOrder; createdAt must not win.
        assertEquals(listOf("a", "b", "c"), state.active.map { it.key })
        assertEquals(3, state.activeCount)
    }

    @Test
    fun `whole queue pause and single item pause both count as paused`() {
        val state = DownloadsUiState(
            rows = listOf(
                // pauseAll parks running rows back in the queue with this note
                row("global", status = DownloadStatus.QUEUED, note = "Paused"),
                // pausing one item parks it in WAITING
                row("single", status = DownloadStatus.WAITING, note = "Paused"),
                // waiting for Wi-Fi is not the user's pause
                row("wifi", status = DownloadStatus.WAITING, note = "Waiting for Wi-Fi"),
                row("queued")
            )
        )
        assertEquals(2, state.pausedCount)
        assertTrue(state.rows.first { it.key == "global" }.isPaused)
        assertTrue(state.rows.first { it.key == "single" }.isPaused)
        assertFalse(state.rows.first { it.key == "wifi" }.isPaused)
        assertFalse(state.rows.first { it.key == "queued" }.isPaused)
    }

    @Test
    fun `groups follow the first queued item and keep one title together`() {
        val state = DownloadsUiState(
            rows = listOf(
                row("a1", mediaId = "a", mediaTitle = "Alpha", order = 2),
                row("b1", mediaId = "b", mediaTitle = "Beta", order = 1),
                row(
                    "b2",
                    mediaId = "b",
                    mediaTitle = "Beta",
                    status = DownloadStatus.COMPLETED,
                    order = 3,
                    createdAt = 9
                )
            )
        )
        assertEquals(listOf("Beta", "Alpha"), state.groups.map { it.title })
        val beta = state.groups.first()
        // Active parts sort before saved parts inside a title.
        assertEquals(listOf("b1", "b2"), beta.rows.map { it.key })
        assertEquals(1, beta.completed.size)
        assertEquals(1, beta.active.size)
    }

    @Test
    fun `group progress ignores rows that never started`() {
        val state = DownloadsUiState(
            rows = listOf(
                row("paused", status = DownloadStatus.QUEUED, note = "Paused", order = 1)
                    .copy(progress = 9),
                row("fresh", order = 2)
            )
        )
        // The untouched queue entry must not drag the header down to 0%.
        assertEquals(9, state.groups.single().progress)
    }

    @Test
    fun `sort by chapter number rewrites the visible order`() {
        val rows = listOf(
            row("ch12", order = 1, unitNumber = 12.0),
            row("ch2", order = 2, unitNumber = 2.0),
            row("ch7", order = 3, unitNumber = 7.0)
        )
        val sorted = DownloadsUiState(rows = rows.sortedByDescending { it.unitNumber })
        assertEquals(listOf("ch12", "ch7", "ch2"), sorted.rows.map { it.key })
    }
}
