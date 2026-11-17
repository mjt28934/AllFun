package app.allfun.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
    var chrome by remember { mutableStateOf(true) }
    var showChapters by remember { mutableStateOf(false) }
    var seekRequest by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.currentEpisode?.id) { seekRequest = null }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { chrome = !chrome })
                        }
                ) {
                    if (settings.readerMode == ReaderMode.VERTICAL) {
                        VerticalPages(
                            pages = state.pages,
                            startPage = state.page,
                            fit = settings.mangaFit,
                            seekRequest = seekRequest,
                            onSeekHandled = { seekRequest = null },
                            onPageChanged = vm::onPageChanged
                        )
                    } else {
                        PagedPages(
                            pages = state.pages,
                            startPage = state.page,
                            rtl = settings.readingDirection == ReadingDirection.RTL,
                            seekRequest = seekRequest,
                            onSeekHandled = { seekRequest = null },
                            onPageChanged = vm::onPageChanged
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopChrome(
                title = state.title,
                chapterTitle = state.currentEpisode?.title.orEmpty(),
                mode = settings.readerMode,
                onBack = onBack,
                onOpenChapters = { showChapters = true },
                onToggleMode = {
                    scope.launch {
                        container.settings.setReaderMode(
                            if (settings.readerMode == ReaderMode.VERTICAL) ReaderMode.PAGED
                            else ReaderMode.VERTICAL
                        )
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomChrome(
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

    DisposableEffect(Unit) {
        onDispose { vm.flush() }
    }
}

@Composable
private fun TopChrome(
    title: String,
    chapterTitle: String,
    mode: ReaderMode,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onToggleMode: () -> Unit
) {
    Surface(color = Color.Black.copy(alpha = 0.78f), contentColor = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOpenChapters) {
                Icon(
                    Icons.AutoMirrored.Outlined.FormatListBulleted,
                    contentDescription = "Choose chapter"
                )
            }
            IconButton(onClick = onToggleMode) {
                Icon(
                    imageVector = if (mode == ReaderMode.VERTICAL) Icons.Outlined.ViewCarousel
                    else Icons.Outlined.ViewAgenda,
                    contentDescription = if (mode == ReaderMode.VERTICAL) {
                        "Switch to paged reading"
                    } else {
                        "Switch to vertical reading"
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomChrome(
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

    Surface(color = Color.Black.copy(alpha = 0.78f), contentColor = Color.White) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            if (pageCount > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${page + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
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
                        color = Color.White.copy(alpha = 0.75f)
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
                    text = if (chapterCount == 0) {
                        ""
                    } else {
                        "Chapter ${chapterIndex + 1} of $chapterCount"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(onClick = onNext, enabled = hasNext) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Next chapter")
                }
            }
        }
    }
}

@Composable
private fun VerticalPages(
    pages: List<String>,
    startPage: Int,
    fit: MangaFit,
    seekRequest: Int?,
    onSeekHandled: () -> Unit,
    onPageChanged: (Int) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { onPageChanged(it) }
    }
    LaunchedEffect(seekRequest) {
        val target = seekRequest ?: return@LaunchedEffect
        listState.scrollToItem(target.coerceIn(0, pages.lastIndex))
        onSeekHandled()
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(pages.size) { index ->
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(pages[index]).build(),
                contentDescription = "Page ${index + 1}",
                contentScale = when (fit) {
                    MangaFit.WIDTH -> ContentScale.FillWidth
                    MangaFit.HEIGHT, MangaFit.WHOLE_PAGE -> ContentScale.Fit
                },
                modifier = Modifier.fillMaxWidth(),
                loading = { PagePlaceholder(index + 1) },
                error = { PagePlaceholder(index + 1) }
            )
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
    onPageChanged: (Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startPage) { pages.size }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect {
                scale = 1f
                offset = Offset.Zero
                onPageChanged(it)
            }
    }
    LaunchedEffect(seekRequest) {
        val target = seekRequest ?: return@LaunchedEffect
        pagerState.scrollToPage(target.coerceIn(0, pages.lastIndex))
        onSeekHandled()
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = rtl,
        userScrollEnabled = scale <= 1.01f,
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
                loading = { PagePlaceholder(index + 1) },
                error = { PagePlaceholder(index + 1) }
            )
        }
    }
}

@Composable
private fun PagePlaceholder(page: Int) {
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
            androidx.compose.material3.Button(onClick = onBack) { Text("Back") }
            androidx.compose.material3.OutlinedButton(onClick = onRetry) { Text("Retry") }
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
