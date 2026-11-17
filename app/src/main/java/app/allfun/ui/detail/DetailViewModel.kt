package app.allfun.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaEntity
import app.allfun.data.db.ProgressEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val mediaId: String,
    private val container: AppContainer
) : ViewModel() {

    val media = container.library.observeMedia(mediaId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val episodes = container.library.observeEpisodes(mediaId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList<EpisodeEntity>()
    )

    val progress = container.progress.observe(mediaId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null as ProgressEntity?
    )

    init {
        viewModelScope.launch {
            container.library.touchOpened(mediaId)
            refresh()
        }
    }

    /** Pulls fresh metadata and units from the source, silently failing when offline. */
    suspend fun refresh() {
        try {
            val media = container.library.media(mediaId) ?: return
            val source = container.sourceHub.sourceFor(media.sourceId) ?: return
            val details = source.details(media.sourceKey)
            val units = runCatching { source.units(media.sourceKey) }.getOrDefault(emptyList())
            container.library.savePreview(media.sourceId, source.type, details, units)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Keep whatever is cached in the database.
        }
    }

    fun toggleLibrary() {
        viewModelScope.launch {
            val item: MediaEntity = container.library.media(mediaId) ?: return@launch
            container.library.setInLibrary(mediaId, !item.inLibrary)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch { container.library.toggleFavorite(mediaId) }
    }
}
