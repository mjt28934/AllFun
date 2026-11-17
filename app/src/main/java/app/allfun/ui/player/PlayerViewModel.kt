package app.allfun.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.StreamInfo
import app.allfun.data.net.UserError
import app.allfun.data.net.toUserError
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
        val error: UserError? = null
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
        _state.update {
            it.copy(
                episodeIndex = index,
                episodeId = episode.id,
                subtitle = if (episode.title == item.title) "" else episode.title,
                stream = null,
                startPositionMs = startPositionMs,
                loading = true,
                error = null
            )
        }
        viewModelScope.launch {
            try {
                val source = container.sourceHub.sourceFor(item.sourceId)
                    ?: error("That source is no longer available")
                val stream = source.stream(
                    item.sourceKey,
                    episode.sourceUnitId ?: episode.id,
                    episode.payload
                ) ?: error(
                    "This source returned no playable stream. Torrent-only addons are not supported."
                )
                _state.update { it.copy(stream = stream, loading = false) }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(loading = false, error = t.toUserError("this stream"))
                }
            }
        }
    }

    fun retry() {
        open(_state.value.episodeIndex, 0L)
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
