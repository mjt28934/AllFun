package app.allfun.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.StreamInfo
import app.allfun.data.net.UserError
import app.allfun.data.net.toUserError
import app.allfun.data.torrent.TorrentEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val container: AppContainer,
    private val mediaId: String,
    private val startEpisodeId: String?
) : ViewModel() {

    data class State(
        val title: String = "",
        val subtitle: String = "",
        val stream: StreamInfo? = null,
        val startPositionMs: Long = 0L,
        val episodeId: String = "",
        val episodeIndex: Int = 0,
        val episodeCount: Int = 0,
        val loading: Boolean = true,
        val error: UserError? = null,
        /** Human-readable torrent preparation state, null for direct streams. */
        val torrentStatus: String? = null,
        /** Every option the installed addons returned for this episode. */
        val streams: List<StreamInfo> = emptyList(),
        /** True while the viewer is choosing between [streams]. */
        val showStreamPicker: Boolean = false,
        val selectedStreamIndex: Int = -1
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var media: MediaEntity? = null
    private var episodes: List<EpisodeEntity> = emptyList()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val item = container.library.media(mediaId)
                    ?: error("That title is no longer in your library")
                if (!item.kind.isVideo) error("That title is not a video")
                media = item
                episodes = container.library.episodes(mediaId)
                if (episodes.isEmpty()) error("No playable videos for this title")
                container.library.touchOpened(mediaId)
                val saved = container.progress.get(mediaId)
                val wantedId = startEpisodeId ?: saved?.episodeId
                val index = episodes.indexOfFirst { it.id == wantedId }.coerceAtLeast(0)
                val startMs = if (saved?.episodeId == episodes[index].id) saved.position else 0L
                _state.update {
                    it.copy(title = item.title, episodeCount = episodes.size)
                }
                open(index, startMs)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update { it.copy(loading = false, error = t.toUserError("this video")) }
            }
        }
    }

    private fun open(index: Int, startPositionMs: Long) {
        val item = media ?: return
        val episode = episodes.getOrNull(index) ?: return
        // A new episode replaces the old torrent; leaving it running would keep seeding
        // while the UI claims to be on a different video.
        container.torrents.stop()
        _state.update {
            it.copy(
                episodeIndex = index,
                episodeId = episode.id,
                subtitle = if (episode.title == item.title) "" else episode.title,
                stream = null,
                startPositionMs = startPositionMs,
                loading = true,
                error = null,
                torrentStatus = null,
                streams = emptyList(),
                showStreamPicker = false,
                selectedStreamIndex = -1
            )
        }
        viewModelScope.launch {
            try {
                val unitId = episode.sourceUnitId ?: episode.id
                // A downloaded film or episode plays from the app's own storage first.
                // Downloads are keyed by the local episode row.
                val localFile = container.downloads.localStream(mediaId, episode.id)
                if (localFile != null) {
                    _state.update {
                        it.copy(stream = StreamInfo(url = localFile), loading = false)
                    }
                    return@launch
                }
                container.sourceHub.sourceFor(item.sourceId)
                    ?: error("That source is no longer available")
                val streams = container.sourceHub.streamsFor(
                    originSourceId = item.sourceId,
                    key = item.sourceKey,
                    unitId = unitId,
                    payload = episode.payload
                )
                if (streams.isEmpty()) {
                    error(
                        "This source returned no playable stream. The addon may be offline, " +
                            "or the release may not have an available seed."
                    )
                }
                if (streams.size == 1) {
                    play(streams, 0)
                } else {
                    _state.update {
                        it.copy(
                            streams = streams,
                            showStreamPicker = true,
                            loading = false
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(loading = false, error = t.toUserError("this stream"))
                }
            }
        }
    }

    /** Plays one entry of the current list; used by the picker and by Retry. */
    fun chooseStream(index: Int) {
        val streams = _state.value.streams
        if (index !in streams.indices) return
        play(streams, index)
    }

    private fun play(streams: List<StreamInfo>, index: Int) {
        val stream = streams.getOrNull(index) ?: return
        _state.update {
            it.copy(
                streams = streams,
                selectedStreamIndex = index,
                showStreamPicker = false,
                stream = null,
                loading = true,
                error = null,
                torrentStatus = null
            )
        }
        val torrent = stream.torrent
        if (torrent != null) {
            startTorrent(stream)
        } else {
            _state.update { it.copy(stream = stream, loading = false) }
        }
    }

    /** Returns to the list after a dead torrent or a stream the device cannot play. */
    fun chooseAnotherStream() {
        container.torrents.stop()
        _state.update {
            if (it.streams.size > 1) {
                it.copy(
                    stream = null,
                    loading = false,
                    error = null,
                    torrentStatus = null,
                    showStreamPicker = true
                )
            } else {
                it.copy(error = null, torrentStatus = null, loading = true)
            }
        }
        if (_state.value.streams.size <= 1) {
            open(_state.value.episodeIndex, _state.value.startPositionMs)
        }
    }

    /**
     * Torrent playback becomes a normal [StreamInfo] only after libtorrent has the
     * pieces around the start position. Until then the player shows peer progress
     * instead of opening ExoPlayer against an incomplete file.
     */
    private fun startTorrent(stream: StreamInfo) {
        val torrent = stream.torrent ?: return
        _state.update {
            it.copy(stream = null, loading = true, torrentStatus = "Finding peers…")
        }
        container.torrents.play(
            torrent,
            object : TorrentEngine.Callbacks {
                override fun onStatus(status: TorrentEngine.Status) {
                    _state.update { it.copy(torrentStatus = torrentStatusText(status)) }
                }

                override fun onReady() {
                    _state.update {
                        it.copy(
                            stream = stream.copy(url = TorrentEngine.PLAYBACK_URI),
                            loading = false,
                            torrentStatus = null
                        )
                    }
                }

                override fun onError(message: String, technical: String?) {
                    _state.update {
                        it.copy(
                            loading = false,
                            torrentStatus = null,
                            error = UserError(message, technical)
                        )
                    }
                }
            }
        )
    }

    private fun torrentStatusText(status: TorrentEngine.Status): String {
        val buffer = status.bufferPercent.coerceIn(0, 100)
        val peers = if (status.seeds > 0) " · ${status.seeds} seeds" else ""
        val speed = when {
            status.downloadSpeed <= 0 -> ""
            status.downloadSpeed >= 1024 * 1024 ->
                " · %.1f MB/s".format(status.downloadSpeed / (1024.0 * 1024.0))
            status.downloadSpeed >= 1024 ->
                " · ${status.downloadSpeed / 1024} KB/s"
            else -> " · ${status.downloadSpeed} B/s"
        }
        return "Buffering torrent $buffer%$peers$speed"
    }

    /** Releases libtorrent when the player screen goes away, not only when the VM dies. */
    fun stopTorrentPlayback() {
        container.torrents.stop()
    }

    override fun onCleared() {
        container.torrents.stop()
        super.onCleared()
    }

    fun retry() {
        container.torrents.stop()
        val current = _state.value
        if (current.selectedStreamIndex in current.streams.indices) {
            play(current.streams, current.selectedStreamIndex)
        } else {
            open(current.episodeIndex, 0L)
        }
    }

    fun nextEpisode() {
        val next = _state.value.episodeIndex + 1
        if (next < episodes.size) open(next, 0L)
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L) return
        val episodeId = _state.value.episodeId
        if (episodeId.isBlank()) return
        val total = when {
            durationMs > 0L -> durationMs
            else -> episodes.firstOrNull { it.id == episodeId }?.durationMs ?: 0L
        }
        if (total <= 0L) return
        container.appScope.launch {
            container.progress.save(mediaId, episodeId, positionMs, total)
        }
    }
}
