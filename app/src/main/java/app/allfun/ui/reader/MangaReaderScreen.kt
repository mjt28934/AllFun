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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
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
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A tap this soon after scrolling stops is treated as the finger that stopped it. */
private const val TAP_GRACE_MS = 180L

@OptIn(ExperimentalMaterial3Api::class)
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
            .background(Color.Black)
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

            state.loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )

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
                        onScrollStarted = { chrome = false }
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

        // Thin chapter progress bar at the very bottom
        if (state.pages.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (state.page + 1).toFloat() / state.pages.size.toFloat())
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
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
    Surface(color = Color.Black.copy(alpha = 0.82f), contentColor = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chapterTitle.isNotBlank()) {
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Reader settings")
            }
            IconButton(onClick = onOpenChapters) {
                Icon(
                    Icons.AutoMirrored.Outlined.FormatListBulleted,
                    contentDescription = "Choose chapter"
                )
            }
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

    Surface(color = Color.Black.copy(alpha = 0.82f), contentColor = Color.White) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (pageCount > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${page + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.width(28.dp)
                    )
                    Slider(
                        value = if (dragging) dragValue else page.toFloat(),
                        onValueChange = { value ->
                            dragging = true
                            dragValue = value
                            onSeek(value.roundToInt())
                        },
                        onValueChangeFinished = { dragging = false },
                        valueRange = 0f..(pageCount - 1).toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "$pageCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.width(28.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPrevious, enabled = hasPrevious) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous chapter")
                }
                Text(
                    text = if (chapterCount == 0) "" else "Chapter ${chapterIndex + 1} of $chapterCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onNext, enabled = hasNext) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Next chapter")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Reader settings", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()

            SettingsGroup("Reading mode") {
                SettingsChoice(
                    label = "Vertical scroll",
                    selected = mode == ReaderMode.VERTICAL,
                    icon = Icons.Outlined.ViewAgenda,
                    onClick = { onSetMode(ReaderMode.VERTICAL) }
                )
                SettingsChoice(
                    label = "Paged",
                    selected = mode == ReaderMode.PAGED,
                    icon = Icons.Outlined.ViewCarousel,
                    onClick = { onSetMode(ReaderMode.PAGED) }
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))

            SettingsGroup("Fit pages") {
                MangaFit.entries.forEach { option ->
                    SettingsChoice(label = option.label, selected = fit == option, onClick = { onSetFit(option) })
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))

            SettingsGroup("Page order") {
                SettingsChoice(
                    label = "Left to right",
                    selected = direction == ReadingDirection.LTR,
                    onClick = { onSetDirection(ReadingDirection.LTR) }
                )
                SettingsChoice(
                    label = "Right to left (manga)",
                    selected = direction == ReadingDirection.RTL,
                    onClick = { onSetDirection(ReadingDirection.RTL) }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        content()
    }
}

@Composable
private fun SettingsChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
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
    onScrollStarted: () -> Unit
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
            .background(Color(0xFF14141C)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White.copy(alpha = 0.7f),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Page $page",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ReaderError(error: UserError, onBack: () -> Unit, onRetry: () -> Unit) {
    var showTechnical by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Could not load", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = error.message,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
        val technical = error.technical
        if (!technical.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showTechnical = !showTechnical }) {
                Text(
                    text = if (showTechnical) "Hide technical details" else "Technical details",
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            if (showTechnical) {
                Text(
                    text = technical,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
    }
}