package app.allfun.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.SourceOption
import app.allfun.data.content.category
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.ui.components.CoverImage
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.accent
import app.allfun.ui.components.accentColor
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.countLabel
import app.allfun.ui.formatDuration
import app.allfun.ui.formatBytes
import app.allfun.ui.formatNumber
import app.allfun.ui.theme.LocalReduceMotion
import app.allfun.ui.theme.Motion

/**
 * One detail layout for every provider. Movies, books and manga share the hero,
 * actions, source selector, description and unit list; the metadata labels and the
 * reader/player action change with the category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    container: AppContainer,
    mediaId: String,
    onBack: () -> Unit,
    onSwitchSource: (String) -> Unit,
    onOpen: (mediaId: String, episodeId: String?, kind: MediaKind) -> Unit
) {
    val vm = rememberViewModel { DetailViewModel(mediaId, container) }
    val media by vm.media.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val canDownload by vm.canDownload.collectAsStateWithLifecycle()
    val preparing by vm.preparing.collectAsStateWithLifecycle()
    val sourceState by vm.sources.collectAsStateWithLifecycle()
    val switched by vm.switchMediaId.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var expanded by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<DownloadEntity?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(switched) {
        val id = switched ?: return@LaunchedEffect
        onSwitchSource(id)
        vm.clearSwitch()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = media?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val saved = media?.inLibrary == true
                    val favorite = media?.favorite == true
                    val category = media?.kind?.category()
                    if (canDownload) {
                        val allDone = downloads.isNotEmpty() &&
                            downloads.none { it.status.isActive } &&
                            downloads.count { it.status == DownloadStatus.COMPLETED } == episodes.size
                        IconButton(
                            onClick = { vm.download() },
                            enabled = episodes.isNotEmpty() && !allDone
                        ) {
                            Icon(
                                imageVector = if (allDone) Icons.Outlined.DownloadDone
                                else Icons.Outlined.Download,
                                contentDescription = if (allDone) {
                                    "Everything is downloaded"
                                } else {
                                    "Download all ${media?.unitLabel?.lowercase() ?: "items"}"
                                },
                                tint = if (downloads.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    IconButton(onClick = { vm.toggleFavorite() }) {
                        Icon(
                            imageVector = if (favorite) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (favorite) {
                                "Remove from favorites"
                            } else {
                                "Add to favorites"
                            },
                            tint = if (favorite) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { vm.toggleLibrary() }) {
                        Icon(
                            imageVector = if (saved) Icons.Filled.Bookmark
                            else Icons.Outlined.BookmarkBorder,
                            contentDescription = when {
                                category == ContentCategory.MOVIES && saved ->
                                    "Remove from watchlist"
                                category == ContentCategory.MOVIES -> "Add to watchlist"
                                saved -> "Remove from library"
                                else -> "Add to library"
                            },
                            tint = if (saved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        val item = media
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val category = item.kind.category()
        val resume = episodes.firstOrNull { it.id == progress?.episodeId }
            ?: episodes.firstOrNull()
        val isCurrent = progress?.episodeId == resume?.id

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { DetailHero(item, category) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onOpen(mediaId, resume?.id, item.kind) },
                        enabled = resume != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (item.kind.isVideo) Icons.Filled.PlayArrow
                            else Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                isCurrent -> "Continue"
                                item.kind.isVideo -> "Play"
                                else -> "Read"
                            }
                        )
                    }
                    OutlinedButton(onClick = { vm.toggleLibrary() }) {
                        Icon(
                            imageVector = if (item.inLibrary) Icons.Filled.Bookmark
                            else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                category == ContentCategory.MOVIES && item.inLibrary ->
                                    "In watchlist"
                                category == ContentCategory.MOVIES -> "Watchlist"
                                item.inLibrary -> "Saved"
                                else -> "Save"
                            }
                        )
                    }
                }
            }

            if (!item.description.isNullOrBlank()) {
                item {
                    val reduceMotion = LocalReduceMotion.current
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateContentSize(
                                animationSpec = if (reduceMotion) {
                                    androidx.compose.animation.core.snap()
                                } else {
                                    tween(Motion.MEDIUM, easing = Motion.EaseOut)
                                }
                            )
                    ) {
                        Text(
                            text = item.description.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis
                        )
                        if ((item.description?.length ?: 0) > 260) {
                            TextButton(
                                onClick = { expanded = !expanded },
                                modifier = Modifier.semantics {
                                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                                }
                            ) {
                                Text(if (expanded) "Show less" else "Read more")
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (expanded) {
                                        Icons.Outlined.ExpandLess
                                    } else {
                                        Icons.Outlined.ExpandMore
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                SourceSection(
                    state = sourceState,
                    category = category,
                    onUse = vm::useSource,
                    onRetry = vm::findSources
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            item {
                SectionHeader(
                    title = unitsTitle(item),
                    actionLabel = "${episodes.size}",
                    onAction = null
                )
            }

            if (episodes.isEmpty()) {
                item {
                    if (preparing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Looking for ${item.unitLabel.lowercase()}s…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "No ${item.unitLabel.lowercase()}s yet. The source did not return " +
                                "a ${item.unitLabel.lowercase()} list for this title.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            } else if (item.kind == MediaKind.SERIES) {
                episodes.groupBy { it.season }.forEach { (season, list) ->
                    if (season > 0) {
                        item("season-$season") {
                            Text(
                                text = "Season $season",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(list, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            kind = item.kind,
                            position = progress?.takeIf { it.episodeId == episode.id }?.position,
                            total = progress?.takeIf { it.episodeId == episode.id }?.total,
                            download = downloads.firstOrNull { it.unitId == episode.id },
                            canDownload = canDownload,
                            onClick = { onOpen(mediaId, episode.id, item.kind) },
                            onDownload = { vm.download(episode.id) },
                            onCancelDownload = vm::cancelDownload,
                            onRetryDownload = vm::retryDownload,
                            onRemoveDownload = { pendingRemove = it }
                        )
                    }
                }
            } else {
                items(episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        kind = item.kind,
                        position = progress?.takeIf { it.episodeId == episode.id }?.position,
                        total = progress?.takeIf { it.episodeId == episode.id }?.total,
                        download = downloads.firstOrNull { it.unitId == episode.id },
                        canDownload = canDownload,
                        onClick = { onOpen(mediaId, episode.id, item.kind) },
                        onDownload = { vm.download(episode.id) },
                        onCancelDownload = vm::cancelDownload,
                        onRetryDownload = vm::retryDownload,
                        onRemoveDownload = { pendingRemove = it }
                    )
                }
            }
        }
    }

    pendingRemove?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove downloaded file?") },
            text = {
                Text(
                    "\"${row.unitTitle}\" is deleted from this device. You can download it " +
                        "again while ${media?.title ?: "this title"}'s provider is available."
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
private fun DetailHero(media: MediaEntity, category: ContentCategory) {
    val accent = category.accent()
    val format = formatLabel(media, category)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(238.dp)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.24f), Color.Transparent)
                )
            )
    ) {
        if (!media.backdropUrl.isNullOrBlank()) {
            CoverImage(
                url = media.backdropUrl,
                title = media.title,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.25f),
                            1f to Color.Black.copy(alpha = 0.86f)
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(168.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                CoverImage(
                    url = media.coverUrl,
                    title = media.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = media.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent.copy(alpha = 0.20f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = format,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = countLabel(media.totalUnits, media.unitLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val meta = listOfNotNull(
                    media.year?.toString(),
                    media.status?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val creator = when (category) {
                    ContentCategory.BOOKS -> media.author?.let { "By $it" }
                    ContentCategory.MANGA -> listOfNotNull(
                        media.author?.let { "By $it" },
                        media.artist?.let { "Art by $it" }
                    ).joinToString(" · ").takeIf { it.isNotBlank() }
                    ContentCategory.MOVIES -> media.author?.takeIf { it.isNotBlank() }
                }
                if (!creator.isNullOrBlank()) {
                    Text(
                        text = creator,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (media.genreList.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = media.genreList.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (category == ContentCategory.MOVIES && media.castList.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Cast: " + media.castList.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceSection(
    state: DetailViewModel.SourceState,
    category: ContentCategory,
    onUse: (SourceOption) -> Unit,
    onRetry: () -> Unit
) {
    val confirmed = state.options.count { it.available }
    SectionHeader(
        title = "Available sources",
        subtitle = when {
            state.loading -> "Checking your other installed sources…"
            confirmed > 1 -> "$confirmed sources confirmed this title"
            confirmed == 1 -> "Only one source confirmed this title"
            state.error != null -> "Source check failed"
            else -> "No provider confirmed this title right now"
        }
    )
    if (state.loading || state.switching) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
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
            OutlinedButton(onClick = onRetry) { Text("Find sources") }
        }
    }
}

@Composable
private fun SourceOptionRow(
    option: SourceOption,
    category: ContentCategory,
    enabled: Boolean,
    onUse: () -> Unit
) {
    Surface(
        color = if (option.isPrimary || option.isPreferred) {
            category.accent().copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(
                if (option.available && enabled) Modifier.clickable(onClick = onUse)
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.sourceName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (option.isPreferred) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.Star,
                            contentDescription = "Preferred source",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                val detail = option.statusLabel
                    ?: option.message
                    ?: if (option.available) "Available" else "Unavailable"
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (option.available) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onUse,
                enabled = option.available && enabled
            ) {
                Text(
                    when {
                        option.isPrimary -> "Current"
                        option.available -> "Use"
                        else -> "Unavailable"
                    }
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeEntity,
    kind: MediaKind,
    position: Long?,
    total: Long?,
    download: DownloadEntity?,
    canDownload: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onRemoveDownload: (DownloadEntity) -> Unit
) {
    val fraction = if (total != null && total > 0L && position != null) {
        (position.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val status = download?.status

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (fraction > 0f) kind.accentColor().copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            if (episode.number <= 0.0) {
                // Providers that only expose titles leave the numeric index unset. A topic
                // icon reads better than the placeholder number "-1".
                Icon(
                    imageVector = if (kind.isVideo) Icons.Filled.PlayArrow
                    else Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = if (fraction > 0f) kind.accentColor()
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = formatNumber(episode.number),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (fraction > 0f) kind.accentColor()
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when (status) {
                DownloadStatus.COMPLETED ->
                    "Downloaded · ${formatBytes(download?.totalBytes ?: 0L)}"
                DownloadStatus.RUNNING ->
                    if ((download?.progress ?: -1) >= 0) {
                        "Downloading ${download?.progress}%"
                    } else {
                        "Downloading"
                    }
                DownloadStatus.QUEUED, DownloadStatus.WAITING ->
                    download?.note ?: "Queued"
                DownloadStatus.FAILED -> download?.error ?: "Download failed"
                DownloadStatus.CANCELED -> "Download cancelled"
                null -> when {
                    kind.isVideo && episode.durationMs != null ->
                        formatDuration(episode.durationMs)
                    episode.pageCount != null -> "${episode.pageCount} pages"
                    else -> ""
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (status) {
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (fraction > 0f) {
            Icon(
                imageVector = Icons.Outlined.PlayCircle,
                contentDescription = "Continue",
                tint = kind.accentColor()
            )
        }
        if (canDownload) {
            when (status) {
                DownloadStatus.COMPLETED -> IconButton(onClick = { onRemoveDownload(download) }) {
                    Icon(
                        Icons.Outlined.DownloadDone,
                        contentDescription = "Remove downloaded ${episode.title}",
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
                                contentDescription = "Cancel download of ${episode.title}",
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                DownloadStatus.FAILED -> IconButton(onClick = { onRetryDownload(download.key) }) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = "Retry download of ${episode.title}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                DownloadStatus.CANCELED, null -> IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = "Download ${episode.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun unitsTitle(media: MediaEntity): String = when (media.kind) {
    MediaKind.SERIES -> "Episodes"
    MediaKind.MOVIE -> "Videos"
    else -> "${media.unitLabel}s"
}

private fun formatLabel(media: MediaEntity, category: ContentCategory): String {
    val genres = media.genreList.joinToString(" ").lowercase()
    return when (category) {
        ContentCategory.MOVIES -> if (media.kind == MediaKind.SERIES) "Series" else "Movie"
        ContentCategory.BOOKS -> if (genres.contains("light novel")) "Light novel" else "Novel"
        ContentCategory.MANGA -> when {
            genres.contains("manhwa") -> "Manhwa"
            genres.contains("manhua") -> "Manhua"
            genres.contains("comic") -> "Comic"
            else -> "Manga"
        }
    }
}
