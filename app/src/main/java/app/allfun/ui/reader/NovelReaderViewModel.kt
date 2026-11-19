package app.allfun.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.model.MediaKind
import app.allfun.data.net.UserError
import app.allfun.data.net.toUserError
import app.allfun.data.settings.NovelTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NovelReaderViewModel(
    private val container: AppContainer,
    private val mediaId: String,
    private val startEpisodeId: String?
) : ViewModel() {

    data class State(
        val title: String = "",
        val chapters: List<EpisodeEntity> = emptyList(),
        val index: Int = 0,
        val chapter: EpisodeEntity? = null,
        /** Set for network sources; rendered directly instead of a file URL. */
        val chapterHtml: String? = null,
        val fontSize: Int = 100,
        val lineSpacing: Float = 1.72f,
        val theme: NovelTheme = NovelTheme.DARK,
        val loading: Boolean = true,
        val error: UserError? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Consumed by the WebView once the chapter has been laid out. */
    private var pendingRestore: Float = 0f

    private var saveJob: Job? = null
    private var lastFraction: Float = 0f

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                _state.update {
                    it.copy(
                        fontSize = settings.novelFontSize,
                        lineSpacing = settings.novelLineSpacing,
                        theme = settings.novelTheme
                    )
                }
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val item = container.library.media(mediaId)
                    ?: error("That book is no longer in your library")
                if (item.kind != MediaKind.NOVEL) error("That title is not a book")
                container.library.touchOpened(mediaId)
                val chapters = container.library.episodes(mediaId)
                if (chapters.isEmpty()) error("This book has no readable chapters")
                val saved = container.progress.get(mediaId)
                val wantedId = startEpisodeId ?: saved?.episodeId
                val index = chapters.indexOfFirst { it.id == wantedId }.coerceAtLeast(0)
                _state.update { it.copy(title = item.title, chapters = chapters) }
                val savedFraction = if (saved?.episodeId == chapters[index].id) {
                    (saved.position.toFloat() / saved.total.coerceAtLeast(1L)).coerceIn(0f, 1f)
                } else 0f
                openChapter(index, savedFraction)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update { it.copy(loading = false, error = t.toUserError("this book")) }
            }
        }
    }

    fun openChapter(index: Int, restore: Float = 0f) {
        val chapter = _state.value.chapters.getOrNull(index) ?: return
        pendingRestore = restore
        lastFraction = restore
        _state.update {
            it.copy(
                index = index,
                chapter = chapter,
                chapterHtml = null,
                loading = true,
                error = null
            )
        }
        container.appScope.launch {
            container.progress.save(mediaId, chapter.id, (restore * 1000f).toLong(), 1000L)
        }

        viewModelScope.launch {
            try {
                val item = container.library.media(mediaId)
                    ?: error("That book is no longer in your library")
                // Downloads are keyed by the local episode row.
                val downloaded = container.downloads.localChapter(mediaId, chapter.id)
                if (!downloaded.isNullOrBlank()) {
                    val looksLikeMarkup = downloaded.trimStart().startsWith("<")
                    _state.update {
                        it.copy(
                            chapterHtml = htmlDocument(chapter.title, downloaded, looksLikeMarkup),
                            loading = false
                        )
                    }
                    return@launch
                }
                val source = container.sourceHub.sourceFor(item.sourceId)
                if (source != null && source.chaptersAreRemote) {
                    val unitId = chapter.sourceUnitId ?: chapter.id
                    val body = source.chapterBody(item.sourceKey, unitId)
                    val text = body ?: source.chapterText(item.sourceKey, unitId)
                    if (text.isNullOrBlank()) {
                        error("This chapter returned no text. The site may need a browser.")
                    }
                    _state.update {
                        it.copy(
                            chapterHtml = htmlDocument(chapter.title, text.orEmpty(), body != null),
                            loading = false
                        )
                    }
                } else {
                    error(
                        "This book's provider is not installed. Reinstall the extension " +
                            "to read this title again."
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(loading = false, error = t.toUserError("this chapter"))
                }
            }
        }
    }

    fun consumeRestore(): Float {
        val value = pendingRestore
        pendingRestore = 0f
        return value
    }

    fun onScroll(fraction: Float) {
        val chapter = _state.value.chapter ?: return
        lastFraction = fraction.coerceIn(0f, 1f)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            container.progress.save(
                mediaId,
                chapter.id,
                (lastFraction * 1000f).toLong(),
                1000L
            )
        }
    }

    fun nextChapter() {
        val index = _state.value.index
        if (index + 1 < _state.value.chapters.size) openChapter(index + 1)
    }

    fun previousChapter() {
        val index = _state.value.index
        if (index > 0) openChapter(index - 1)
    }

    fun flush() {
        saveJob?.cancel()
        val chapter = _state.value.chapter ?: return
        val fraction = lastFraction
        container.appScope.launch {
            container.progress.save(mediaId, chapter.id, (fraction * 1000f).toLong(), 1000L)
        }
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    /** Wraps a chapter body in a minimal document the reader's CSS can style. */
    private fun htmlDocument(title: String, body: String, isHtml: Boolean): String {
        val content = if (isHtml) {
            body
        } else {
            body
                .split(Regex("\n\\s*\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n") { paragraph ->
                    "<p>" + escape(paragraph).replace("\n", "<br/>") + "</p>"
                }
        }
        // Remote chapter HTML usually carries its own title element and the reader's
        // top bar already shows the chapter name, so injecting a heading would print it
        // twice. Plain-text bodies have no markup of their own, so they get one.
        val heading = if (isHtml || title.isBlank()) "" else "<h2>" + escape(title) + "</h2>"
        return """
            <!doctype html>
            <html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            </head><body>$heading$content</body></html>
        """.trimIndent()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
