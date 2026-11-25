package app.allfun.ui.reader

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.net.UserError
import app.allfun.data.settings.MangaFit
import app.allfun.data.settings.ReaderMode
import app.allfun.data.settings.ReadingDirection
import app.allfun.data.settings.Settings
import app.allfun.ui.components.ChapterPickerDialog
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFOverline
import app.allfun.ui.glass.AFSpinner
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.GlassButton
import app.allfun.ui.glass.GlassPrimaryAction
import app.allfun.ui.glass.GlassSegmented
import app.allfun.ui.glass.GlassSheet
import app.allfun.ui.glass.GlassSlider
import app.allfun.ui.glass.GlassSurface
import app.allfun.ui.glass.GlassTier
import app.allfun.ui.glass.GlassTokens
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A tap this soon after scrolling stops is treated as the finger that stopped it. */
private const val TAP_GRACE_MS = 180L

/** The reading canvas stays neutral so artwork carries the colour, not the chrome. */
private val ReaderBlack = Color(0xFF06070A)

@Composable
fun MangaReaderScreen(
    container: AppContainer,
    mediaId: String,
    startEpisodeId: String?,
    onBack: () -> Unit
) {
    val vm = rememberViewModel { MangaReaderViewModel(container, mediaId, startEpisodeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var chrome by rememberSaveable { mutableStateOf(true) }
    var showChapters by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var seekRequest by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(state.currentEpisode?.id) { seekRequest = null }

    // Reading is a foreground activity; by default the screen should not dim mid-chapter.
    val view = LocalView.current
    DisposableEffect(view, settings.readerKeepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = settings.readerKeepScreenOn
        onDispose { view.keepScreenOn = previous }
    }

    // Komikku-style volume-key page turning (works in both reading modes).
    fun turnPage(direction: Int) {
        val s = state
        if (s.pages.isEmpty()) return
        val target = s.page + direction
        when {
            target in s.pages.indices -> seekRequest = target
            target < 0 && s.episodeIndex > 0 -> vm.previousChapter()
            target >= s.pages.size && s.episodeIndex < s.episodes.lastIndex -> vm.nextChapter()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ReaderBlack)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val forward = if (settings.readingDirection == ReadingDirection.RTL) -1 else 1
                when (event.key) {
                    Key.VolumeUp -> { turnPage(-forward); true }
                    Key.VolumeDown -> { turnPage(forward); true }
                    else -> false
                }
            }
    ) {
        when {
            state.error != null -> ReaderError(
                error = state.error ?: UserError("Could not load this chapter", null),
                onBack = onBack,
                onRetry = { vm.openChapter(state.episodeIndex) }
            )

            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AFSpinner(size = 32.dp, color = AFTheme.colors.textHi)
            }

            state.pages.isNotEmpty() -> key(state.currentEpisode?.id) {
                if (settings.readerMode == ReaderMode.VERTICAL) {
                    VerticalPages(
                        pages = state.pages,
                        startPage = state.page,
                        fit = settings.mangaFit,
                        seekRequest = seekRequest,
                        onSeekHandled = { seekRequest = null },
                        onPageChanged = vm::onPageChanged,
                        onTap = { chrome = !chrome },
                        onScrollStarted = { chrome = false },
                        chapterTitle = state.currentEpisode?.title.orEmpty(),
                        chapterIndex = state.episodeIndex,
                        chapterCount = state.episodes.size,
                        hasPrevious = state.episodeIndex > 0,
                        hasNext = state.episodeIndex < state.episodes.lastIndex,
                        onPreviousChapter = vm::previousChapter,
                        onNextChapter = vm::nextChapter
                    )
                } else {
                    PagedPages(
                        pages = state.pages,
                        startPage = state.page,
                        rtl = settings.readingDirection == ReadingDirection.RTL,
                        seekRequest = seekRequest,
                        onSeekHandled = { seekRequest = null },
                        onPageChanged = vm::onPageChanged,
                        hasPrevious = state.episodeIndex > 0,
                        hasNext = state.episodeIndex < state.episodes.lastIndex,
                        onPreviousChapter = vm::previousChapter,
                        onNextChapter = vm::nextChapter,
                        onToggleChrome = { chrome = !chrome },
                        scope = scope
                    )
                }
            }
        }

        // Thin chapter progress line, flush with the reading canvas.
        if (state.pages.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (state.page + 1).toFloat() / state.pages.size.toFloat())
                        .background(AFTheme.colors.accent.copy(alpha = 0.7f))
                )
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            MangaTopChrome(
                title = state.title,
                chapterTitle = state.currentEpisode?.title.orEmpty(),
                onBack = onBack,
                onOpenChapters = { showChapters = true },
                onOpenSettings = { showSettings = true }
            )
        }

        AnimatedVisibility(
            visible = chrome,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MangaBottomChrome(
                hasPrevious = state.episodeIndex > 0,
                hasNext = state.episodeIndex < state.episodes.lastIndex,
                page = state.page,
                pageCount = state.pages.size,
                chapterIndex = state.episodeIndex,
                chapterCount = state.episodes.size,
                onPrevious = vm::previousChapter,
                onNext = vm::nextChapter,
                onSeek = { seekRequest = it }
            )
        }
    }

    if (showChapters) {
        ChapterPickerDialog(
            chapters = state.episodes,
            selectedIndex = state.episodeIndex,
            onSelect = { vm.openChapter(it) },
            onDismiss = { showChapters = false }
        )
    }

    if (showSettings) {
        MangaReaderSettingsSheet(
            mode = settings.readerMode,
            fit = settings.mangaFit,
            direction = settings.readingDirection,
            onSetMode = { mode ->
                scope.launch { container.settings.setReaderMode(mode) }
            },
            onSetFit = { fit ->
                scope.launch { container.settings.setMangaFit(fit) }
            },
            onSetDirection = { dir ->
                scope.launch { container.settings.setReadingDirection(dir) }
            },
            onDismiss = { showSettings = false }
        )
    }

    DisposableEffect(Unit) {
        onDispose { vm.flush() }
    }
}

@Composable
private fun MangaTopChrome(
    title: String,
    chapterTitle: String,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSettings: () -> Unit
) {
    GlassSurface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(52.dp),
        tier = GlassTier.CLEAR,
        shape = RoundedCornerShape(26.dp),
        shadow = true
    ) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AFIconButton(
                icon = AFIcons.Back,
                contentDescription = "Back",
                onClick = onBack
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 4.dp)
            ) {
                AFText(
                    text = title,
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    onGlass = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chapterTitle.isNotBlank()) {
                    AFText(
                        text = chapterTitle,
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        onGlass = true,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AFIconButton(
                icon = AFIcons.Sliders,
                contentDescription = "Reader settings",
                onClick = onOpenSettings
            )
            AFIconButton(
                icon = AFIcons.List,
                contentDescription = "Choose chapter",
                onClick = onOpenChapters
            )
        }
    }
}

@Composable
private fun MangaBottomChrome(
    hasPrevious: Boolean,
    hasNext: Boolean,
    page: Int,
    pageCount: Int,
    chapterIndex: Int,
    chapterCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            tier = GlassTier.FROSTED,
            shape = RoundedCornerShape(GlassTokens.rPanel),
            shadow = true
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (pageCount > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AFText(
                            text = "${page + 1}",
                            style = AFTheme.type.numeric,
                            color = AFTheme.colors.textMid,
                            onGlass = true,
                            modifier = Modifier.width(32.dp)
                        )
                        GlassSlider(
                            value = if (dragging) dragValue else page.toFloat(),
                            valueRange = 0f..(pageCount - 1).toFloat(),
                            onValueChange = { value ->
                                dragging = true
                                dragValue = value
                                onSeek(value.roundToInt())
                            },
                            onValueChangeFinished = { dragging = false },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        )
                        AFText(
                            text = "$pageCount",
                            style = AFTheme.type.numeric,
                            color = AFTheme.colors.textMid,
                            onGlass = true,
                            modifier = Modifier.width(32.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AFIconButton(
                        icon = AFIcons.Back,
                        contentDescription = "Previous chapter",
                        onClick = onPrevious,
                        enabled = hasPrevious
                    )
                    AFText(
                        text = if (chapterCount == 0) "" else "Chapter ${chapterIndex + 1} of $chapterCount",
                        style = AFTheme.type.label,
                        color = AFTheme.colors.textHi,
                        onGlass = true
                    )
                    AFIconButton(
                        icon = AFIcons.Forward,
                        contentDescription = "Next chapter",
                        onClick = onNext,
                        enabled = hasNext
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaReaderSettingsSheet(
    mode: ReaderMode,
    fit: MangaFit,
    direction: ReadingDirection,
    onSetMode: (ReaderMode) -> Unit,
    onSetFit: (MangaFit) -> Unit,
    onSetDirection: (ReadingDirection) -> Unit,
    onDismiss: () -> Unit
) {
    val fits = MangaFit.entries
    GlassSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AFIcon(
                icon = AFIcons.Sliders,
                contentDescription = null,
                tint = AFTheme.colors.accent,
                size = 20.dp
            )
            Spacer(Modifier.width(10.dp))
            AFText(
                text = "Reader settings",
                style = AFTheme.type.title,
                color = AFTheme.colors.textHi
            )
        }
        Spacer(Modifier.height(18.dp))
        AFOverline("Reading mode")
        Spacer(Modifier.height(8.dp))
        GlassSegmented(
            options = listOf("Vertical scroll", "Paged"),
            selectedIndex = if (mode == ReaderMode.VERTICAL) 0 else 1,
            onSelect = { index ->
                onSetMode(if (index == 0) ReaderMode.VERTICAL else ReaderMode.PAGED)
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        AFOverline("Fit pages")
        Spacer(Modifier.height(8.dp))
        GlassSegmented(
            options = fits.map { it.label },
            selectedIndex = fits.indexOf(fit).coerceAtLeast(0),
            onSelect = { index -> onSetFit(fits[index]) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        AFOverline("Page order")
        Spacer(Modifier.height(8.dp))
        GlassSegmented(
            options = listOf("Left to right", "Right to left"),
            selectedIndex = if (direction == ReadingDirection.RTL) 1 else 0,
            onSelect = { index ->
                onSetDirection(
                    if (index == 1) ReadingDirection.RTL else ReadingDirection.LTR
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        AFText(
            text = "Volume keys turn pages in the direction you choose here.",
            style = AFTheme.type.caption,
            color = AFTheme.colors.textLo
        )
    }
}

/**
 * Komikku-style tap zones for paged mode:
 * Left 1/3 → previous page (or chapter if on first page)
 * Right 1/3 → next page (or chapter if on last page)
 * Center 1/3 → toggle chrome
 */
@Composable
private fun TapZoneOverlay(
    rtl: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    pagerState: androidx.compose.foundation.pager.PagerState,
    pages: List<String>,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleChrome: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.33f)
                .align(Alignment.CenterStart)
                .background(Color.Transparent)
                .clickable {
                    if (rtl) {
                        scope.launch {
                            val next = pagerState.currentPage + 1
                            if (next < pages.size) pagerState.scrollToPage(next)
                            else onNextChapter()
                        }
                    } else {
                        scope.launch {
                            val prev = pagerState.currentPage - 1
                            if (prev >= 0) pagerState.scrollToPage(prev)
                            else onPreviousChapter()
                        }
                    }
                }
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.33f)
                .align(Alignment.CenterEnd)
                .background(Color.Transparent)
                .clickable {
                    if (rtl) {
                        scope.launch {
                            val prev = pagerState.currentPage - 1
                            if (prev >= 0) pagerState.scrollToPage(prev)
                            else onPreviousChapter()
                        }
                    } else {
                        scope.launch {
                            val next = pagerState.currentPage + 1
                            if (next < pages.size) pagerState.scrollToPage(next)
                            else onNextChapter()
                        }
                    }
                }
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.34f)
                .align(Alignment.Center)
                .background(Color.Transparent)
                .clickable(onClick = onToggleChrome)
        )
    }
}

@Composable
private fun VerticalPages(
    pages: List<String>,
    startPage: Int,
    fit: MangaFit,
    seekRequest: Int?,
    onSeekHandled: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onScrollStarted: () -> Unit,
    chapterTitle: String,
    chapterIndex: Int,
    chapterCount: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    var lastScrollEnd by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { onPageChanged(it) }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) onScrollStarted()
                else lastScrollEnd = SystemClock.uptimeMillis()
            }
    }
    LaunchedEffect(seekRequest) {
        val target = seekRequest ?: return@LaunchedEffect
        listState.scrollToItem(target.coerceIn(0, pages.lastIndex))
        onSeekHandled()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // Chrome is toggled by a real tap only. Dragging to read must never flash the
            // bars, and a tap that merely stops a fling is ignored for a moment after.
            .pointerInput(listState) {
                detectTapGestures(
                    onTap = {
                        val sinceScroll = SystemClock.uptimeMillis() - lastScrollEnd
                        if (!listState.isScrollInProgress && sinceScroll > TAP_GRACE_MS) onTap()
                    }
                )
            }
    ) {
        items(pages.size) { index ->
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(pages[index]).build(),
                contentDescription = "Page ${index + 1}",
                contentScale = when (fit) {
                    MangaFit.WIDTH -> ContentScale.FillWidth
                    MangaFit.HEIGHT, MangaFit.WHOLE_PAGE -> ContentScale.Fit
                },
                modifier = Modifier.fillMaxWidth(),
                loading = { MangaPagePlaceholder(index + 1) },
                error = { MangaPagePlaceholder(index + 1) }
            )
        }
        item(key = "chapter-end") {
            ChapterEndCard(
                chapterTitle = chapterTitle,
                chapterIndex = chapterIndex,
                chapterCount = chapterCount,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                onPrevious = onPreviousChapter,
                onNext = onNextChapter
            )
        }
    }
}

/**
 * Komikku-style chapter footer: the end of a chapter is a real boundary, so the reader
 * says so and offers the next one instead of silently stopping at the last image.
 */
@Composable
private fun ChapterEndCard(
    chapterTitle: String,
    chapterIndex: Int,
    chapterCount: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReaderBlack)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            tier = GlassTier.FROSTED,
            shape = RoundedCornerShape(GlassTokens.rPanel)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AFText(
                    text = "End of chapter",
                    style = AFTheme.type.title,
                    color = AFTheme.colors.textHi,
                    onGlass = true
                )
                if (chapterTitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    AFText(
                        text = chapterTitle,
                        style = AFTheme.type.bodySmall,
                        color = AFTheme.colors.textMid,
                        onGlass = true,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(6.dp))
                AFText(
                    text = "Chapter ${chapterIndex + 1} of $chapterCount",
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textLo,
                    onGlass = true
                )
                Spacer(Modifier.height(20.dp))
                if (hasNext) {
                    GlassPrimaryAction(
                        label = "Next chapter",
                        onClick = onNext,
                        icon = AFIcons.Forward,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (hasPrevious) {
                    GlassButton(
                        label = "Previous chapter",
                        onClick = onPrevious,
                        icon = AFIcons.Back,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!hasNext) {
                    AFText(
                        text = "You are up to date",
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        onGlass = true,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PagedPages(
    pages: List<String>,
    startPage: Int,
    rtl: Boolean,
    seekRequest: Int?,
    onSeekHandled: () -> Unit,
    onPageChanged: (Int) -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleChrome: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val pagerState = rememberPagerState(initialPage = startPage) { pages.size }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect {
                scale = 1f
                offset = Offset.Zero
                isZoomed = false
                onPageChanged(it)
            }
    }
    LaunchedEffect(seekRequest) {
        val target = seekRequest ?: return@LaunchedEffect
        pagerState.scrollToPage(target.coerceIn(0, pages.lastIndex))
        onSeekHandled()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            reverseLayout = rtl,
            userScrollEnabled = !isZoomed && scale <= 1.01f,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var zooming = false
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                val multiTouch = pressed >= 2
                                if (multiTouch || zooming || scale > 1.01f) {
                                    if (multiTouch || zooming) {
                                        scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                    }
                                    if (scale > 1.01f) {
                                        offset += event.calculatePan()
                                        val maxX = containerSize.width * (scale - 1f) / 2f
                                        val maxY = containerSize.height * (scale - 1f) / 2f
                                        offset = Offset(
                                            offset.x.coerceIn(-maxX, maxX),
                                            offset.y.coerceIn(-maxY, maxY)
                                        )
                                    } else {
                                        offset = Offset.Zero
                                        isZoomed = false
                                    }
                                    event.changes.forEach { change ->
                                        if (change.pressed) change.consume()
                                    }
                                    zooming = multiTouch
                                }
                            } while (event.changes.any { it.pressed })
                            if (scale <= 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                                isZoomed = false
                            } else {
                                isZoomed = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(pages[index]).build(),
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loading = { MangaPagePlaceholder(index + 1) },
                    error = { MangaPagePlaceholder(index + 1) }
                )
            }
        }

        // Only show tap zones when not zoomed
        if (!isZoomed) {
            TapZoneOverlay(
                rtl = rtl,
                scope = scope,
                pagerState = pagerState,
                pages = pages,
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter,
                onToggleChrome = onToggleChrome
            )
        }
    }
}

@Composable
private fun MangaPagePlaceholder(page: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .background(Color(0xFF101117)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AFSpinner(size = 28.dp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(10.dp))
            AFText(
                text = "Page $page",
                style = AFTheme.type.caption,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ReaderError(error: UserError, onBack: () -> Unit, onRetry: () -> Unit) {
    var showTechnical by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            tier = GlassTier.FROSTED,
            shape = RoundedCornerShape(GlassTokens.rPanel)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AFText(
                    text = "Could not load",
                    style = AFTheme.type.title,
                    color = AFTheme.colors.textHi,
                    onGlass = true
                )
                Spacer(Modifier.height(8.dp))
                AFText(
                    text = error.message,
                    style = AFTheme.type.body,
                    color = AFTheme.colors.textMid,
                    onGlass = true,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                GlassPrimaryAction(
                    label = "Retry",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                GlassButton(
                    label = "Back",
                    onClick = onBack,
                    icon = AFIcons.Back,
                    modifier = Modifier.fillMaxWidth()
                )
                val technical = error.technical
                if (!technical.isNullOrBlank()) {
                    AFTextButton(
                        label = if (showTechnical) "Hide technical details" else "Technical details",
                        onClick = { showTechnical = !showTechnical },
                        color = AFTheme.colors.textLo,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (showTechnical) {
                        AFText(
                            text = technical,
                            style = AFTheme.type.caption,
                            color = AFTheme.colors.textLo,
                            onGlass = true
                        )
                    }
                }
            }
        }
    }
}
