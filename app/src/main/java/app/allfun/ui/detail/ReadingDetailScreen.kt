package app.allfun.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaEntity
import app.allfun.data.db.ProgressEntity
import app.allfun.data.content.category
import app.allfun.data.content.displayDescription
import app.allfun.data.model.MediaKind
import app.allfun.ui.components.AllFunButton
import app.allfun.ui.components.AllFunButtonVariant
import app.allfun.ui.components.AllFunChip
import app.allfun.ui.components.AllFunSheet
import app.allfun.ui.components.CoverImage
import app.allfun.ui.components.Overline
import app.allfun.ui.components.TopBarAction
import app.allfun.ui.components.accent
import app.allfun.ui.components.staggeredReveal
import app.allfun.ui.formatDuration
import app.allfun.ui.formatNumber
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.AllFunType
import app.allfun.ui.theme.LocalAllFunRadii
import app.allfun.ui.theme.LocalReduceMotion
import app.allfun.ui.theme.Motion

/**
 * Komikku's manga detail page, mapped onto AllFun's providers: backdrop header, action
 * row, expandable description with tags, sortable chapter list with read markers, and a
 * continue-reading button. Books use the same page with book wording.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReadingDetailScreen(
    vm: DetailViewModel,
    media: MediaEntity,
    mediaId: String,
    onBack: () -> Unit,
    onSwitchSource: (String) -> Unit,
    onOpen: (mediaId: String, episodeId: String?, kind: MediaKind) -> Unit
) {
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val canDownload by vm.canDownload.collectAsStateWithLifecycle()
    val preparing by vm.preparing.collectAsStateWithLifecycle()
    val sourceState by vm.sources.collectAsStateWithLifecycle()
    val sourceName by vm.sourceName.collectAsStateWithLifecycle()
    val switched by vm.switchMediaId.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showChapterDialog by rememberSaveable { mutableStateOf(false) }
    var actionChapter by remember { mutableStateOf<EpisodeEntity?>(null) }
    var pendingRemove by remember { mutableStateOf<DownloadEntity?>(null) }
    var sort by rememberSaveable { mutableStateOf(ChapterSort.SOURCE) }
    var filter by rememberSaveable { mutableStateOf(ChapterFilter.ALL) }
    var descriptionExpanded by rememberSaveable(media.id) {
        mutableStateOf(!media.inLibrary)
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(switched) {
        val id = switched ?: return@LaunchedEffect
        onSwitchSource(id)
        vm.clearSwitch()
    }

    val downloadedIds = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED }.mapTo(mutableSetOf()) { it.unitId }
    }
    val visibleChapters = remember(episodes, sort, filter, downloadedIds) {
        filteredChapters(orderedChapters(episodes, sort), filter, downloadedIds)
    }
    val resumeChapter = remember(episodes, progress, sort) {
        val current = episodes.firstOrNull { it.id == progress?.episodeId }
            ?.takeIf { (progress?.fraction ?: 0f) < 0.999f }
        current
            ?: orderedChapters(episodes, sort).firstOrNull { !it.read }
            ?: episodes.firstOrNull()
    }
    val hasRead = remember(episodes) { episodes.any { it.read } }
    val allDownloaded = episodes.isNotEmpty() &&
        downloads.count { it.status == DownloadStatus.COMPLETED } == episodes.size

    // The title bar starts transparent over the header and gains its surface once the
    // header has scrolled away, mirroring the video detail page.
    val listState = rememberLazyListState()
    val collapsed by remember {
        androidx.compose.runtime.derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 96
        }
    }
    val barColor by animateColorAsState(
        targetValue = if (collapsed) {
            AllFun.colors.bg.copy(alpha = 0.97f)
        } else {
            AllFun.colors.bg.copy(alpha = 0.35f)
        },
        animationSpec = if (LocalReduceMotion.current) {
            androidx.compose.animation.core.snap()
        } else {
            tween(Motion.MEDIUM, easing = Motion.EaseOut)
        },
        label = "readingBar"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DetailBar(
                title = media.title,
                collapsed = collapsed,
                barColor = barColor,
                onBack = onBack
            ) {
                Box {
                    TopBarAction(
                        icon = Icons.Outlined.MoreVert,
                        contentDescription = "More actions",
                        onClick = { showOverflow = true },
                        tint = AllFun.colors.textPrimary
                    )
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            onClick = {
                                showOverflow = false
                                vm.refreshNow()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mark all as read") },
                            leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                            enabled = episodes.any { !it.read },
                            onClick = {
                                showOverflow = false
                                vm.markAllRead()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Available sources") },
                            leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
                            onClick = {
                                showOverflow = false
                                showSources = true
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (resumeChapter != null && episodes.any { !it.read }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AllFun.colors.bg)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    AllFunButton(
                        text = if (hasRead) "Resume reading" else "Start reading",
                        onClick = { onOpen(mediaId, resumeChapter.id, media.kind) },
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                ReadingHeader(media = media, sourceName = sourceName)
            }

            item {
                ReadingActionRow(
                    media = media,
                    downloads = downloads,
                    canDownload = canDownload,
                    allDownloaded = allDownloaded,
                    onLibrary = vm::toggleLibrary,
                    onFavorite = vm::toggleFavorite,
                    onDownloadAll = { vm.download() },
                    onSources = { showSources = true }
                )
            }

            item {
                ReadingDescription(
                    description = media.description,
                    genres = media.genreList,
                    expanded = descriptionExpanded,
                    onToggle = { descriptionExpanded = !descriptionExpanded }
                )
            }

            item {
                ChapterListHeader(
                    count = visibleChapters.size,
                    filtered = filter != ChapterFilter.ALL || sort != ChapterSort.SOURCE,
                    onClick = { showChapterDialog = true }
                )
            }

            if (visibleChapters.isEmpty()) {
                item {
                    Text(
                        text = when {
                            preparing -> "Looking for ${media.unitLabel.lowercase()}s…"
                            episodes.isEmpty() -> "No ${media.unitLabel.lowercase()}s yet. " +
                                "The source did not return a ${media.unitLabel.lowercase()} list."
                            else -> "No ${media.unitLabel.lowercase()}s match this filter."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AllFun.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(visibleChapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                    ReadingChapterRow(
                        chapter = chapter,
                        kind = media.kind,
                        progress = progress?.takeIf { it.episodeId == chapter.id },
                        download = downloads.firstOrNull { it.unitId == chapter.id },
                        canDownload = canDownload,
                        onClick = { onOpen(mediaId, chapter.id, media.kind) },
                        onLongClick = { actionChapter = chapter },
                        onDownload = { vm.download(chapter.id) },
                        onCancelDownload = vm::cancelDownload,
                        onRetryDownload = vm::retryDownload,
                        onRemoveDownload = { pendingRemove = it },
                        modifier = Modifier
                            .staggeredReveal(index)
                            .animateItem()
                    )
                }
            }
        }
    }

    if (showSources) {
        ReadingSourceSheet(
            state = sourceState,
            category = media.kind.category(),
            onUse = { option ->
                showSources = false
                vm.useSource(option)
            },
            onRetry = vm::findSources,
            onDismiss = { showSources = false }
        )
    }

    if (showChapterDialog) {
        ChapterListDialog(
            sort = sort,
            filter = filter,
            total = episodes.size,
            unread = episodes.count { !it.read },
            downloaded = episodes.count { it.id in downloadedIds },
            onSort = { sort = it },
            onFilter = { filter = it },
            onDismiss = { showChapterDialog = false }
        )
    }

    actionChapter?.let { chapter ->
        ChapterActionSheet(
            chapter = chapter,
            kind = media.kind,
            progress = progress?.takeIf { it.episodeId == chapter.id },
            download = downloads.firstOrNull { it.unitId == chapter.id },
            canDownload = canDownload,
            onRead = {
                actionChapter = null
                onOpen(mediaId, chapter.id, media.kind)
            },
            onToggleRead = {
                vm.setRead(chapter.id, !chapter.read)
                actionChapter = null
            },
            onDownload = {
                vm.download(chapter.id)
                actionChapter = null
            },
            onCancelDownload = { key ->
                vm.cancelDownload(key)
                actionChapter = null
            },
            onRetryDownload = { key ->
                vm.retryDownload(key)
                actionChapter = null
            },
            onRemoveDownload = { row ->
                actionChapter = null
                pendingRemove = row
            },
            onDismiss = { actionChapter = null }
        )
    }

    pendingRemove?.let { row ->
        AllFunSheet(title = "Remove download?", onDismiss = { pendingRemove = null }) {
            Text(
                text = "\"${row.unitTitle}\" is deleted from this device. You can download it " +
                    "again while ${media.title}'s provider is available.",
                style = MaterialTheme.typography.bodyLarge,
                color = AllFun.colors.textSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AllFunButton(
                    text = "Cancel",
                    onClick = { pendingRemove = null },
                    variant = AllFunButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                AllFunButton(
                    text = "Remove",
                    onClick = {
                        vm.removeDownload(row.key)
                        pendingRemove = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReadingHeader(media: MediaEntity, sourceName: String) {
    val accent = media.kind.category().accent()
    val status = media.status?.takeIf { it.isNotBlank() }
    val radii = LocalAllFunRadii.current
    val coverShape = RoundedCornerShape(radii.cover)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(150.dp)
                .clip(coverShape)
                .background(AllFun.colors.surface2)
                .border(1.dp, AllFun.colors.hairline, coverShape)
        ) {
            CoverImage(
                url = media.coverUrl,
                title = media.title,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.headlineMedium,
                color = AllFun.colors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = media.author?.takeIf { it.isNotBlank() } ?: "Unknown author",
                style = MaterialTheme.typography.bodySmall,
                color = AllFun.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!media.artist.isNullOrBlank() && media.artist != media.author) {
                Text(
                    text = "Art by ${media.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AllFun.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = listOfNotNull(
                    status,
                    media.year?.toString(),
                    sourceName.takeIf { it.isNotBlank() }
                ).joinToString(" \u00B7 ").ifBlank { sourceName },
                style = AllFunType.mono,
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReadingActionRow(
    media: MediaEntity,
    downloads: List<DownloadEntity>,
    canDownload: Boolean,
    allDownloaded: Boolean,
    onLibrary: () -> Unit,
    onFavorite: () -> Unit,
    onDownloadAll: () -> Unit,
    onSources: () -> Unit
) {
    val colors = AllFun.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        ReadingActionButton(
            title = if (media.inLibrary) "In library" else "Library",
            icon = if (media.inLibrary) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            active = media.inLibrary,
            onClick = onLibrary
        )
        ReadingActionButton(
            title = if (media.favorite) "Favorited" else "Favorite",
            icon = if (media.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            active = media.favorite,
            onClick = onFavorite
        )
        ReadingActionButton(
            title = if (allDownloaded) "Downloaded" else "Download",
            icon = if (allDownloaded) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
            active = downloads.isNotEmpty(),
            enabled = canDownload && !allDownloaded,
            onClick = onDownloadAll
        )
        ReadingActionButton(
            title = "Sources",
            icon = Icons.Outlined.Public,
            active = false,
            onClick = onSources
        )
    }
}

@Composable
private fun RowScope.ReadingActionButton(
    title: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val colors = AllFun.colors
    val tint = when {
        !enabled -> colors.textTertiary.copy(alpha = 0.45f)
        active -> colors.accent
        else -> colors.textSecondary
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingDescription(
    description: String?,
    genres: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val text = description?.displayDescription()?.takeIf { it.isNotBlank() }
    if (text == null && genres.isEmpty()) return
    val reduceMotion = LocalReduceMotion.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 20.dp, end = 20.dp)
            .animateContentSize(
                animationSpec = if (reduceMotion) {
                    androidx.compose.animation.core.snap()
                } else {
                    tween(Motion.MEDIUM, easing = Motion.EaseOut)
                }
            )
    ) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = AllFun.colors.textPrimary,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis
            )
            if (text.length > 280) {
                AllFunButton(
                    text = if (expanded) "Show less" else "Read more",
                    onClick = onToggle,
                    variant = AllFunButtonVariant.TEXT,
                    icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    modifier = Modifier.semantics {
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
                )
            }
        }
        if (genres.isNotEmpty()) {
            Text(
                text = genres.joinToString(" \u00B7 "),
                style = AllFunType.mono,
                color = AllFun.colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ChapterListHeader(count: Int, filtered: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$count ${if (count == 1) "chapter" else "chapters"}",
            style = MaterialTheme.typography.titleMedium,
            color = AllFun.colors.textPrimary
        )
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = if (filtered) "Chapter list options, filter active"
            else "Chapter list options",
            tint = if (filtered) AllFun.colors.accent else AllFun.colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingChapterRow(
    chapter: EpisodeEntity,
    kind: MediaKind,
    progress: ProgressEntity?,
    download: DownloadEntity?,
    canDownload: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onRemoveDownload: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val status = download?.status
    val read = chapter.read
    val readPercent = progress?.fraction
        ?.takeIf { it > 0f }
        ?.let { (it * 100).toInt() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!read) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = "Unread",
                        tint = AllFun.colors.accent,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(6.dp)
                    )
                }
                Text(
                    text = chapterLabel(chapter, kind),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (read) AllFun.colors.textSecondary else AllFun.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                Text(
                    text = chapterSubtitle(chapter, status),
                    style = AllFunType.mono,
                    color = if (read) AllFun.colors.textTertiary else AllFun.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (readPercent != null) {
                    Text(
                        text = " \u00B7 $readPercent%",
                        style = AllFunType.mono,
                        color = AllFun.colors.textTertiary,
                        maxLines = 1
                    )
                }
            }
        }
        if (canDownload) {
            ChapterDownloadIndicator(
                chapter = chapter,
                download = download,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onRetryDownload = onRetryDownload,
                onRemoveDownload = onRemoveDownload
            )
        }
    }
}

@Composable
private fun ChapterDownloadIndicator(
    chapter: EpisodeEntity,
    download: DownloadEntity?,
    onDownload: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onRemoveDownload: (DownloadEntity) -> Unit
) {
    when (download?.status) {
        DownloadStatus.COMPLETED -> IconButton(onClick = { onRemoveDownload(download) }) {
            Icon(
                Icons.Outlined.DownloadDone,
                contentDescription = "Remove downloaded ${chapter.title}",
                tint = AllFun.colors.success
            )
        }
        DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.WAITING ->
            IconButton(onClick = { onCancelDownload(download.key) }) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = {
                            ((download.progress.takeIf { it >= 0 } ?: 0) / 100f)
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = AllFun.colors.accent,
                        trackColor = AllFun.colors.hairline
                    )
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Cancel download of ${chapter.title}",
                        modifier = Modifier.size(11.dp),
                        tint = AllFun.colors.textSecondary
                    )
                }
            }
        DownloadStatus.FAILED -> IconButton(onClick = { onRetryDownload(download.key) }) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = "Retry download of ${chapter.title}",
                tint = AllFun.colors.danger
            )
        }
        DownloadStatus.CANCELED, null -> IconButton(onClick = onDownload) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = "Download ${chapter.title}",
                tint = AllFun.colors.textSecondary
            )
        }
    }
}

/**
 * Providers that expose no numbering send -1.0. Printing that as "Chapter -1.0" told the
 * reader nothing, so the row describes the chapter instead: its real page count, its
 * duration for text chapters, or a plain label when the provider sent no size at all.
 */
private fun chapterSubtitle(chapter: EpisodeEntity, status: DownloadStatus?): String = when (status) {
    DownloadStatus.COMPLETED -> "Downloaded"
    DownloadStatus.RUNNING -> "Downloading"
    DownloadStatus.QUEUED, DownloadStatus.WAITING -> "Queued"
    DownloadStatus.FAILED -> "Download failed"
    DownloadStatus.CANCELED -> "Download cancelled"
    null -> when {
        chapter.pageCount != null && chapter.pageCount > 0 ->
            "$chapter.pageCount ${if (chapter.pageCount == 1) "page" else "pages"}"
        chapter.durationMs != null && chapter.durationMs > 0 ->
            formatDuration(chapter.durationMs).ifBlank { "Ready to read" }
        chapter.number > 0 ->
            "Chapter ${formatNumber(chapter.number)}"
        else -> "Ready to read"
    }
}

/** Falls back to the provider's numbering only when it is a real, positive number. */
private fun chapterLabel(chapter: EpisodeEntity, kind: MediaKind): String =
    chapter.title.ifBlank {
        if (chapter.number > 0) "${kind.unit} ${formatNumber(chapter.number)}" else kind.unit
    }

@Composable
private fun ReadingSourceSheet(
    state: DetailViewModel.SourceState,
    category: app.allfun.data.content.ContentCategory,
    onUse: (app.allfun.data.content.SourceOption) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AllFunSheet(title = "Sources", onDismiss = onDismiss) {
        Text(
            text = when {
                state.loading -> "Checking your other installed sources\u2026"
                state.options.count { it.available } > 1 ->
                    "${state.options.count { it.available }} sources confirmed this title"
                state.error != null -> "Source check failed"
                else -> "No other provider confirmed this title right now"
            },
            style = MaterialTheme.typography.bodySmall,
            color = AllFun.colors.textSecondary
        )
        if (state.loading || state.switching) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                color = AllFun.colors.accent,
                trackColor = AllFun.colors.hairline
            )
        }
        state.error?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = AllFun.colors.danger,
                    modifier = Modifier.weight(1f)
                )
                AllFunButton(
                    text = "Retry",
                    onClick = onRetry,
                    variant = AllFunButtonVariant.TEXT
                )
            }
        }
        Column(Modifier.padding(top = 8.dp)) {
            state.options.forEach { option ->
                SourceOptionRow(
                    option = option,
                    category = category,
                    enabled = !state.switching,
                    onUse = { onUse(option) }
                )
            }
            if (state.options.isEmpty() && !state.loading) {
                AllFunButton(
                    text = "Find sources",
                    onClick = onRetry,
                    variant = AllFunButtonVariant.SECONDARY,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterListDialog(
    sort: ChapterSort,
    filter: ChapterFilter,
    total: Int,
    unread: Int,
    downloaded: Int,
    onSort: (ChapterSort) -> Unit,
    onFilter: (ChapterFilter) -> Unit,
    onDismiss: () -> Unit
) {
    AllFunSheet(title = "Chapter list", onDismiss = onDismiss) {
        Overline("Sort")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        ) {
            ChapterSort.entries.forEach { option ->
                AllFunChip(
                    label = option.label,
                    selected = sort == option,
                    onClick = { onSort(option) }
                )
            }
        }
        Overline("Filter")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            ChapterFilter.entries.forEach { option ->
                val count = when (option) {
                    ChapterFilter.ALL -> total
                    ChapterFilter.UNREAD -> unread
                    ChapterFilter.DOWNLOADED -> downloaded
                }
                AllFunChip(
                    label = "${option.label} ($count)",
                    selected = filter == option,
                    onClick = { onFilter(option) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.End
        ) {
            AllFunButton(
                text = "Done",
                onClick = onDismiss,
                variant = AllFunButtonVariant.PRIMARY
            )
        }
    }
}

@Composable
private fun ChapterActionSheet(
    chapter: EpisodeEntity,
    kind: MediaKind,
    progress: ProgressEntity?,
    download: DownloadEntity?,
    canDownload: Boolean,
    onRead: () -> Unit,
    onToggleRead: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onRemoveDownload: (DownloadEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AllFunSheet(title = chapterLabel(chapter, kind), onDismiss = onDismiss) {
        val detail = listOfNotNull(
            chapter.pageCount?.let { "$it pages" },
            progress?.fraction?.takeIf { it > 0f }?.let { "${(it * 100).toInt()}% read" },
            download?.status?.let { downloadStatusLabel(it) }
        ).joinToString(" \u00B7 ")
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = AllFunType.mono,
                color = AllFun.colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        ChapterActionItem(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            label = "Read now",
            onClick = onRead
        )
        ChapterActionItem(
            icon = if (chapter.read) Icons.Outlined.RemoveDone else Icons.Outlined.Done,
            label = if (chapter.read) "Mark as unread" else "Mark as read",
            onClick = onToggleRead
        )
        if (canDownload) {
            when (download?.status) {
                DownloadStatus.COMPLETED -> ChapterActionItem(
                    icon = Icons.Outlined.Delete,
                    label = "Delete downloaded file",
                    tint = AllFun.colors.danger,
                    onClick = { onRemoveDownload(download) }
                )
                DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.WAITING ->
                    ChapterActionItem(
                        icon = Icons.Outlined.Close,
                        label = "Cancel download",
                        onClick = { onCancelDownload(download.key) }
                    )
                DownloadStatus.FAILED -> {
                    ChapterActionItem(
                        icon = Icons.Outlined.Download,
                        label = "Retry download",
                        onClick = { onRetryDownload(download.key) }
                    )
                    ChapterActionItem(
                        icon = Icons.Outlined.Delete,
                        label = "Remove failed download",
                        tint = AllFun.colors.danger,
                        onClick = { onRemoveDownload(download) }
                    )
                }
                DownloadStatus.CANCELED, null -> ChapterActionItem(
                    icon = Icons.Outlined.Download,
                    label = "Download",
                    onClick = onDownload
                )
            }
        }
    }
}

@Composable
private fun ChapterActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AllFun.colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

private fun downloadStatusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.COMPLETED -> "Downloaded"
    DownloadStatus.RUNNING -> "Downloading"
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.WAITING -> "Waiting"
    DownloadStatus.FAILED -> "Download failed"
    DownloadStatus.CANCELED -> "Download cancelled"
}

private fun statusIcon(status: String?): ImageVector? = when (status?.lowercase()) {
    "ongoing", "publishing" -> Icons.Outlined.Schedule
    "completed", "finished", "complete" -> Icons.Outlined.DoneAll
    "licensed" -> Icons.Outlined.AttachMoney
    "hiatus", "on hiatus" -> Icons.Outlined.Pause
    "cancelled", "canceled" -> Icons.Outlined.Block
    else -> null
}
