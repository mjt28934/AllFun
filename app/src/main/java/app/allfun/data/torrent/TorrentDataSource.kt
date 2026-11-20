package app.allfun.data.torrent

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Media3 data source for a ready torrent. Reads come from the selected file through
 * TorrentStream's blocking input stream, which waits until the piece under the read
 * cursor has arrived. Every seek re-aims libtorrent at the new position.
 *
 * Non-torrent URLs (subtitle sidecars, downloaded files, Jellyfin) go to the normal
 * default data source, so one factory can serve the whole player.
 */
@UnstableApi
class TorrentDataSource(
    private val fallback: DataSource,
    private val engine: TorrentEngine
) : DataSource {

    private val listeners = mutableListOf<TransferListener>()
    private var input: InputStream? = null
    private var uri: Uri? = null
    private var remaining = 0L
    private var position = 0L
    private var usingTorrent = false
    private var dataSpec: DataSpec? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        fallback.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        close()
        this.dataSpec = dataSpec
        uri = dataSpec.uri
        if (!dataSpec.uri.scheme.equals(TorrentEngine.SCHEME, ignoreCase = true)) {
            usingTorrent = false
            return fallback.open(dataSpec)
        }

        usingTorrent = true
        listeners.forEach { it.onTransferInitializing(this, dataSpec, false) }
        val torrent = engine.currentTorrent()
            ?: throw IOException("The torrent is still buffering.")
        val file = torrent.getVideoFile() ?: throw IOException("The torrent has no video file.")
        val total = file.length()
        if (total <= 0) throw IOException("The torrent file has no readable bytes yet.")

        val start = dataSpec.position.coerceAtLeast(0L)
        if (start >= total) throw IOException("The torrent file ended before the seek position.")
        val stream = torrent.getVideoStream()
        val skipped = stream.skip(start)
        if (skipped != start) {
            runCatching { stream.close() }
            throw IOException("Could not seek inside the torrent file.")
        }
        input = stream
        position = start
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            total - start
        } else {
            min(dataSpec.length, total - start)
        }
        engine.seek(start)
        listeners.forEach { it.onTransferStart(this, dataSpec, false) }
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!usingTorrent) return fallback.read(buffer, offset, length)
        val stream = input ?: return C.RESULT_END_OF_INPUT
        if (remaining <= 0) return C.RESULT_END_OF_INPUT
        val wanted = min(length.toLong(), remaining).toInt()
        val read = stream.read(buffer, offset, wanted)
        if (read == -1) return C.RESULT_END_OF_INPUT
        position += read
        remaining -= read
        // A large read (or rapid playback) moves the play cursor; keep piece priority
        // just ahead of it rather than only where the player originally opened.
        engine.seek(position)
        dataSpec?.let { spec ->
            listeners.forEach { it.onBytesTransferred(this, spec, false, read) }
        }
        return read
    }

    override fun getUri(): Uri? = if (usingTorrent) uri else fallback.uri

    override fun close() {
        if (usingTorrent) {
            runCatching { input?.close() }
            input = null
            val spec = dataSpec
            if (spec != null) {
                listeners.forEach { it.onTransferEnd(this, spec, false) }
            }
            remaining = 0L
        } else {
            fallback.close()
        }
        usingTorrent = false
        dataSpec = null
    }
}

/** Builds a player data source that understands both HTTP files and torrent playback. */
@UnstableApi
class TorrentDataSourceFactory(
    private val context: Context,
    private val engine: TorrentEngine,
    private val httpFactory: DefaultHttpDataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource = TorrentDataSource(
        DefaultDataSource.Factory(context, httpFactory).createDataSource(),
        engine
    )
}
