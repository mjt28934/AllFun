package app.allfun.data.torrent

import android.content.Context
import android.util.Log
import app.allfun.data.model.TorrentRef
import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.TorrentOptions
import com.github.se_bastiaan.torrentstream.TorrentStream
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the one libtorrent session used for playback.
 *
 * A Stremio addon can return dozens of torrents for one episode. Only one is streamed
 * at a time: starting a new file stops the previous torrent and removes its temporary
 * pieces, so leaving the player does not leave a seeder running or fill the cache.
 *
 * The engine is deliberately callback-based. libtorrent only reports readiness after
 * the first and last pieces of the selected file are on disk, which can take a while;
 * the player shows that progress instead of freezing on a spinner.
 */
class TorrentEngine(context: Context) {

    data class Status(
        val bufferPercent: Int,
        val torrentPercent: Int,
        val seeds: Int,
        val downloadSpeed: Int
    )

    interface Callbacks {
        fun onStatus(status: Status)
        fun onReady()
        fun onError(message: String, technical: String?)
    }

    private val root = File(context.cacheDir, "torrent-stream")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var session: TorrentStream? = null
    private var timeout: Job? = null
    private var client: Callbacks? = null
    private var ref: TorrentRef? = null

    @Volatile
    private var ready = false

    @Volatile
    private var activeTorrent: Torrent? = null

    @Volatile
    private var generation = 0

    private val listener = object : TorrentListener {
        override fun onStreamPrepared(torrent: Torrent) {
            if (client == null) return
            val wanted = ref
            val index = chooseFile(torrent, wanted)
            Log.i(TAG, "prepared ${torrent.getFileNames()?.size ?: 0} file(s), using index $index")
            runCatching { torrent.setSelectedFileIndex(index) }
                .onFailure { Log.w(TAG, "file selection failed", it) }
            torrent.startDownload()
        }

        override fun onStreamStarted(torrent: Torrent) {
            // Progress arrives separately; the UI already says "connecting".
        }

        override fun onStreamReady(torrent: Torrent) {
            if (client == null) return
            Log.i(TAG, "ready: ${torrent.getVideoFile()?.name}")
            activeTorrent = torrent
            ready = true
            timeout?.cancel()
            timeout = null
            client?.onReady()
        }

        override fun onStreamProgress(torrent: Torrent, status: StreamStatus) {
            if (client == null) return
            client?.onStatus(
                Status(
                    bufferPercent = status.bufferProgress.coerceIn(0, 100),
                    torrentPercent = status.progress.coerceIn(0f, 100f).toInt(),
                    seeds = status.seeds.coerceAtLeast(0),
                    downloadSpeed = status.downloadSpeed.coerceAtLeast(0)
                )
            )
        }

        override fun onStreamError(torrent: Torrent?, e: Exception?) {
            if (client == null) return
            Log.w(TAG, "torrent error", e)
            val message = e?.message?.takeIf { it.isNotBlank() }
                ?: "The torrent addon returned a file that could not be started."
            fail(
                "This torrent could not be started. It may have no peers, or the addon " +
                    "may have returned a stale info hash.",
                technical = e?.let { "${it.javaClass.simpleName}: ${it.message}" }
                    ?: message
            )
        }

        override fun onStreamStopped() {
            // stop() already clears the client; nothing to forward.
        }
    }

    /**
     * Starts [ref] and reports readiness through [callbacks]. Any previous session is
     * stopped first, including its temporary files.
     */
    fun play(ref: TorrentRef, callbacks: Callbacks) {
        stop()
        val token = ++generation
        this.ref = ref
        client = callbacks
        ready = false
        activeTorrent = null

        val directory = File(root, "session-$token")
        runCatching { directory.mkdirs() }
        val options = TorrentOptions.Builder()
            .saveLocation(directory)
            .removeFilesAfterStop(true)
            // The listener below selects the addon's file index before starting, so
            // automatic download must not race ahead with the largest file.
            .autoDownload(false)
            .maxConnections(80)
            .prepareSize(32L * 1024L * 1024L)
            .build()

        try {
            // TorrentStream.init() builds its libtorrent SessionManager on its own
            // handler thread and only then applies the options passed to init(). Calling
            // setOptions() right after init() would dereference a null session, so the
            // first stream relies on the options given to init() and only a reused
            // session (already initialised) receives updated options.
            val stream = session
            if (stream == null) {
                TorrentStream.init(options).also {
                    session = it
                    it.addListener(listener)
                }
            } else {
                stream.setOptions(options)
            }
            session?.startStream(ref.magnet)
        } catch (t: Throwable) {
            Log.e(TAG, "could not start torrent", t)
            fail(
                "The torrent engine could not start. Try another stream.",
                technical = "${t.javaClass.simpleName}: ${t.message}"
            )
            return
        }

        timeout = scope.launch {
            delay(TIMEOUT_MS)
            if (token == generation && !ready) {
                fail(
                    "No peers answered in time. The torrent may be old or unseeded; " +
                        "try another stream.",
                    technical = "Torrent metadata or pieces were not available after " +
                        "${TIMEOUT_MS / 1000} seconds."
                )
            }
        }
    }

    /** The active torrent, once its first pieces are ready for the player. */
    fun currentTorrent(): Torrent? = activeTorrent.takeIf { ready }

    /**
     * Tells libtorrent which part of the file the player is about to read. Without this
     * a seek near the end would wait for ordinary sequential downloading to reach it.
     */
    fun seek(offset: Long) {
        val torrent = activeTorrent ?: return
        runCatching { torrent.setInterestedBytes(offset) }
    }

    /** Stops playback, removes temporary pieces and invalidates pending callbacks. */
    fun stop() {
        generation++
        timeout?.cancel()
        timeout = null
        client = null
        ref = null
        ready = false
        activeTorrent = null
        runCatching { session?.stopStream() }
    }

    private fun chooseFile(torrent: Torrent, ref: TorrentRef?): Int {
        val names = torrent.getFileNames()?.toList().orEmpty()
        ref?.fileIndex?.takeIf { it in names.indices }?.let { return it }
        val wanted = ref?.fileName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        if (wanted != null && names.isNotEmpty()) {
            val match = names.indexOfFirst {
                it.substringAfterLast('/').equals(wanted, ignoreCase = true)
            }
            if (match >= 0) return match
        }
        // -1 tells TorrentStream to pick the largest file, which is the right guess for a
        // movie torrent and for single-file episode torrents.
        return -1
    }

    private fun fail(message: String, technical: String?) {
        val callbacks = client ?: return
        client = null
        ready = false
        activeTorrent = null
        timeout?.cancel()
        timeout = null
        generation++
        runCatching { session?.stopStream() }
        callbacks.onError(message, technical)
    }

    companion object {
        private const val TAG = "TorrentEngine"
        private const val TIMEOUT_MS = 120_000L

        /** Pseudo-URL consumed by [TorrentDataSource]; never opened by the OS. */
        const val PLAYBACK_URI = "torrent://allfun/stream"
        const val SCHEME = "torrent"
    }
}
