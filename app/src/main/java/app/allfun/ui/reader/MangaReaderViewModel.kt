package app.allfun.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.data.net.UserError
import app.allfun.data.net.toUserError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MangaReaderViewModel(
    private val container: AppContainer,
    private val mediaId: String,
    private val startEpisodeId: String?
) : ViewModel() {

    data class State(
        val title: String = "",
        val episodes: List<EpisodeEntity> = emptyList(),
        val episodeIndex: Int = 0,
        val currentEpisode: EpisodeEntity? = null,
        val pages: List<String> = emptyList(),
        val page: Int = 0,
        val loading: Boolean = true,
        val error: UserError? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var media: MediaEntity? = null
    private var saveJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val item = container.library.media(mediaId)
                    ?: error("That title is no longer in your library")
                if (item.kind != MediaKind.MANGA) error("That title is not a manga")
                media = item
                container.library.touchOpened(mediaId)
                val chapters = container.library.episodes(mediaId)
                if (chapters.isEmpty()) error("This title has no chapters yet")
                val saved = container.progress.get(mediaId)
                val wantedId = startEpisodeId ?: saved?.episodeId
                val index = chapters.indexOfFirst { it.id == wantedId }.coerceAtLeast(0)
                _state.update { it.copy(title = item.title, episodes = chapters) }
                val startPage = if (saved?.episodeId == chapters[index].id) {
                    saved.position.toInt()
                } else 0
                openChapter(index, startPage)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update { it.copy(loading = false, error = t.toUserError("this manga")) }
            }
        }
    }

    fun openChapter(index: Int, startPage: Int = 0) {
        val chapter = _state.value.episodes.getOrNull(index) ?: return
        val item = media ?: return
        _state.update {
            it.copy(
                episodeIndex = index,
                currentEpisode = chapter,
                pages = emptyList(),
                page = 0,
                loading = true,
                error = null
            )
        }
        viewModelScope.launch {
            // Opening a chapter is what marks it read, like Komikku's chapter list.
            container.library.setEpisodeRead(mediaId, chapter.id, true)
            try {
                // Downloads are keyed by the local episode row, which is stable even when
                // the provider changes its own chapter URL.
                val local = container.downloads.localPages(mediaId, chapter.id)
                if (local.isNotEmpty()) {
                    val page = startPage.coerceIn(0, local.lastIndex)
                    if (!isCurrent(chapter)) return@launch
                    _state.update { it.copy(pages = local, page = page, loading = false) }
                    persist(page, local.size)
                    return@launch
                }
                val source = container.sourceHub.sourceFor(item.sourceId)
                    ?: error("That source is no longer available")
                val pages = source.pages(
                    item.sourceKey,
                    chapter.sourceUnitId ?: chapter.id
                )
                if (!isCurrent(chapter)) return@launch
                if (pages.isEmpty()) error("This source returned no pages for that chapter")
                val page = startPage.coerceIn(0, pages.lastIndex)
                _state.update { it.copy(pages = pages, page = page, loading = false) }
                persist(page, pages.size)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (!isCurrent(chapter)) return@launch
                _state.update {
                    it.copy(loading = false, error = t.toUserError("this chapter"))
                }
            }
        }
    }

    /**
     * A fetch for a chapter the reader has already left must not overwrite the current
     * chapter; fast page-throughs leave several requests in flight.
     */
    private fun isCurrent(chapter: EpisodeEntity): Boolean =
        _state.value.currentEpisode?.id == chapter.id

    fun onPageChanged(page: Int) {
        val current = _state.value
        if (current.pages.isEmpty()) return
        val target = page.coerceIn(0, current.pages.lastIndex)
        if (target == current.page) return
        _state.update { it.copy(page = target) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350)
            persist(target, current.pages.size)
        }
    }

    fun nextChapter() {
        val index = _state.value.episodeIndex
        if (index + 1 < _state.value.episodes.size) openChapter(index + 1)
    }

    fun previousChapter() {
        val index = _state.value.episodeIndex
        if (index > 0) openChapter(index - 1)
    }

    fun flush() {
        saveJob?.cancel()
        val current = _state.value
        if (current.pages.isNotEmpty()) {
            container.appScope.launch { persist(current.page, current.pages.size) }
        }
    }

    private suspend fun persist(page: Int, total: Int) {
        val chapter = _state.value.currentEpisode ?: return
        container.progress.save(mediaId, chapter.id, page.toLong(), total.toLong())
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }
}
