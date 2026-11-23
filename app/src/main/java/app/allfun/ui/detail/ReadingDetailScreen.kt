package app.allfun.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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
import app.allfun.ui.components.CoverImage
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.accent
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = media.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                        }
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
            )
        },
        floatingActionButton = {
            if (resumeChapter != null && episodes.any { !it.read }) {
                ExtendedFloatingActionButton(
                    text = { Text(if (hasRead) "Resume" else "Start") },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = { onOpen(mediaId, resumeChapter.id, media.kind) }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            } else {
                items(visibleChapters, key = { it.id }) { chapter ->
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
                        onRemoveDownload = { pendingRemove = it }
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
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove downloaded file?") },
            text = {
                Text(
                    "\"${row.unitTitle}\" is deleted from this device. You can download it " +
                        "again while ${media.title}'s provider is available."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeDownload(row.key)
                        pendingRemove = null
                    }
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReadingHeader(media: MediaEntity, sourceName: String) {
    val accent = media.kind.category().accent()
    val status = media.status?.takeIf { it.isNotBlank() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 214.dp)
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.22f),
                    1f to Color.Transparent
                )
            )
    ) {
        if (!media.backdropUrl.isNullOrBlank()) {
            CoverImage(
                url = media.backdropUrl,
                title = media.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(214.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(214.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.10f),
                            0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(150.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                CoverImage(
                    url = media.coverUrl,
                    title = media.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                ReadingInfoLine(
                    icon = Icons.Outlined.Person,
                    text = media.author?.takeIf { it.isNotBlank() } ?: "Unknown author"
                )
                if (!media.artist.isNullOrBlank() && media.artist != media.author) {
                    ReadingInfoLine(
                        icon = Icons.Outlined.Brush,
                        text = media.artist.orEmpty()
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    statusIcon(status)?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(16.dp)
                        )
                    }
                    Text(
                        text = listOfNotNull(
                            status,
                            media.year?.toString(),
                            sourceName.takeIf { it.isNotBlank() }
                        ).joinToString(" · ").ifBlank { sourceName },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingInfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(end = 4.dp)
                .size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    val actionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ReadingActionButton(
            title = if (media.inLibrary) "In library" else "Add to library",
            icon = if (media.inLibrary) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            color = if (media.inLibrary) MaterialTheme.colorScheme.primary else actionColor,
            onClick = onLibrary
        )
        ReadingActionButton(
            title = if (media.favorite) "Favorited" else "Favorite",
            icon = if (media.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            color = if (media.favorite) MaterialTheme.colorScheme.tertiary else actionColor,
            onClick = onFavorite
        )
        ReadingActionButton(
            title = if (allDownloaded) "Downloaded" else "Download",
            icon = when {
                allDownloaded -> Icons.Outlined.DownloadDone
                downloads.isNotEmpty() -> Icons.Outlined.Download
                else -> Icons.Outlined.Download
            },
            color = if (downloads.isNotEmpty()) MaterialTheme.colorScheme.primary else actionColor,
            enabled = canDownload && !allDownloaded,
            onClick = onDownloadAll
        )
        ReadingActionButton(
            title = "Sources",
            icon = Icons.Outlined.Public,
            color = actionColor,
            onClick = onSources
        )
    }
}

@Composable
private fun RowScope.ReadingActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) color else color.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                color = if (enabled) color else color.copy(alpha = 0.38f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
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
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis
            )
            if (text.length > 280) {
                TextButton(
                    onClick = onToggle,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    modifier = Modifier.semantics {
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
                ) {
                    Text(if (expanded) "Show less" else "Read more")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess
                        else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        if (genres.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                genres.forEach { genre ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterListHeader(count: Int, filtered: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$count ${if (count == 1) "chapter" else "chapters"}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = if (filtered) "Chapter list options, filter active"
            else "Chapter list options",
            tint = if (filtered) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
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
    onRemoveDownload: (DownloadEntity) -> Unit
) {
    val status = download?.status
    val read = chapter.read
    val readPercent = progress?.fraction
        ?.takeIf { it > 0f }
        ?.let { (it * 100).toInt() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!read) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = "Unread",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(8.dp)
                    )
                }
                Text(
                    text = chapter.title.ifBlank { "${kind.unit} ${chapter.number}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (read) 0.5f else 1f
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                val subtitleColor = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (read) 0.4f else 0.62f
                )
                Text(
                    text = chapterSubtitle(chapter, status),
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (readPercent != null) {
                    Text(
                        text = " · $readPercent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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
                tint = MaterialTheme.colorScheme.primary
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
                        strokeWidth = 2.dp
                    )
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Cancel download of ${chapter.title}",
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        DownloadStatus.FAILED -> IconButton(onClick = { onRetryDownload(download.key) }) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = "Retry download of ${chapter.title}",
                tint = MaterialTheme.colorScheme.error
            )
        }
        DownloadStatus.CANCELED, null -> IconButton(onClick = onDownload) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = "Download ${chapter.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun chapterSubtitle(chapter: EpisodeEntity, status: DownloadStatus?): String = when (status) {
    DownloadStatus.COMPLETED -> "Downloaded"
    DownloadStatus.RUNNING -> "Downloading"
    DownloadStatus.QUEUED, DownloadStatus.WAITING -> "Queued"
    DownloadStatus.FAILED -> "Download failed"
    DownloadStatus.CANCELED -> "Download cancelled"
    null -> chapter.pageCount?.let { "$it ${if (it == 1) "page" else "pages"}" }
        ?: chapter.durationMs?.let { "${it / 60_000} min" }
        ?: "Chapter ${chapter.number}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSourceSheet(
    state: DetailViewModel.SourceState,
    category: app.allfun.data.content.ContentCategory,
    onUse: (app.allfun.data.content.SourceOption) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            SectionHeader(
                title = "Available sources",
                subtitle = when {
                    state.loading -> "Checking your other installed sources…"
                    state.options.count { it.available } > 1 ->
                        "${state.options.count { it.available }} sources confirmed this title"
                    state.error != null -> "Source check failed"
                    else -> "No other provider confirmed this title right now"
                }
            )
            if (state.loading || state.switching) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                state.options.forEach { option ->
                    SourceOptionRow(
                        option = option,
                        category = category,
                        enabled = !state.switching,
                        onUse = { onUse(option) }
                    )
                }
                if (state.options.isEmpty() && !state.loading) {
                    OutlinedButtonRow(onRetry)
                }
            }
        }
    }
}

@Composable
private fun OutlinedButtonRow(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("Find sources") }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chapter list") },
        text = {
            Column {
                Text(
                    text = "Sort",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                ChapterSort.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSort(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = sort == option, onClick = { onSort(option) })
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Filter",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ChapterFilter.entries.forEach { option ->
                        val count = when (option) {
                            ChapterFilter.ALL -> total
                            ChapterFilter.UNREAD -> unread
                            ChapterFilter.DOWNLOADED -> downloaded
                        }
                        FilterChip(
                            selected = filter == option,
                            onClick = { onFilter(option) },
                            label = { Text("${option.label} ($count)") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = chapter.title.ifBlank { "${kind.unit} ${chapter.number}" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = listOfNotNull(
                    chapter.pageCount?.let { "$it pages" },
                    progress?.fraction?.takeIf { it > 0f }?.let { "${(it * 100).toInt()}% read" },
                    download?.status?.let { downloadStatusLabel(it) }
                ).joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
                        tint = MaterialTheme.colorScheme.error,
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
                            tint = MaterialTheme.colorScheme.error,
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
}

@Composable
private fun ChapterActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
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
