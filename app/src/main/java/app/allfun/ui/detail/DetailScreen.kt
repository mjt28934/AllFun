package app.allfun.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.SourceOption
import app.allfun.data.content.category
import app.allfun.data.content.displayDescription
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.ui.components.AllFunButton
import app.allfun.ui.components.AllFunButtonVariant
import app.allfun.ui.components.AllFunSheet
import app.allfun.ui.components.AppTopBar
import app.allfun.ui.components.CoverImage
import app.allfun.ui.components.LoadingState
import app.allfun.ui.components.Overline
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.TopBarAction
import app.allfun.ui.components.accent
import app.allfun.ui.components.accentColor
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.components.staggeredReveal
import app.allfun.ui.countLabel
import app.allfun.ui.formatDuration
import app.allfun.ui.formatBytes
import app.allfun.ui.formatNumber
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.AllFunType
import app.allfun.ui.theme.LocalAllFunRadii
import app.allfun.ui.theme.LocalReduceMotion
import app.allfun.ui.theme.Motion

/**
 * One detail route for every provider. Movies keep their hero/watchlist layout; manga and
 * books use the Komikku-style reading page with chapter rows, read markers and sorting.
 */
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
    val item = media
    if (item == null) {
        DetailLoading(onBack)
        return
    }
    when (item.kind.category()) {
        ContentCategory.MOVIES -> MoviesDetailScreen(
            vm = vm,
            mediaId = mediaId,
            onBack = onBack,
            onSwitchSource = onSwitchSource,
            onOpen = onOpen
        )
        else -> ReadingDetailScreen(
            vm = vm,
            media = item,
            mediaId = mediaId,
            onBack = onBack,
            onSwitchSource = onSwitchSource,
            onOpen = onOpen
        )
    }
}

@Composable
private fun DetailLoading(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        AppTopBar(title = "Title", onBack = onBack)
        LoadingState("Loading this title")
    }
}

/**
 * Header for the detail route. Over the hero it is only the back button and up to three
 * actions; once the page scrolls, the title fades in at row size.
 */
@Composable
internal fun DetailBar(
    title: String,
    collapsed: Boolean,
    barColor: Color,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = AllFun.colors.textPrimary
            )
        }
        Column(Modifier.weight(1f)) {
            AnimatedVisibility(
                visible = collapsed,
                enter = fadeIn(tween(Motion.FAST)),
                exit = fadeOut(tween(Motion.FAST))
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = AllFun.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

@Composable
private fun MoviesDetailScreen(
    vm: DetailViewModel,
    mediaId: String,
    onBack: () -> Unit,
    onSwitchSource: (String) -> Unit,
    onOpen: (mediaId: String, episodeId: String?, kind: MediaKind) -> Unit
) {
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

    val listState = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf {
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
        label = "detailBar"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DetailBar(
                title = media?.title.orEmpty(),
                collapsed = collapsed,
                barColor = barColor,
                onBack = onBack
            ) {
                val saved = media?.inLibrary == true
                val favorite = media?.favorite == true
                val category = media?.kind?.category()
                if (canDownload) {
                    val allDone = downloads.isNotEmpty() &&
                        downloads.none { it.status.isActive } &&
                        downloads.count { it.status == DownloadStatus.COMPLETED } == episodes.size
                    TopBarAction(
                        icon = if (allDone) Icons.Outlined.DownloadDone
                        else Icons.Outlined.Download,
                        contentDescription = if (allDone) {
                            "Everything is downloaded"
                        } else {
                            "Download all ${media?.unitLabel?.lowercase() ?: "items"}"
                        },
                        onClick = { vm.download() },
                        tint = if (downloads.isNotEmpty()) AllFun.colors.accent
                        else AllFun.colors.textPrimary,
                        enabled = episodes.isNotEmpty() && !allDone
                    )
                }
                TopBarAction(
                    icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (favorite) {
                        "Remove from favorites"
                    } else {
                        "Add to favorites"
                    },
                    onClick = { vm.toggleFavorite() },
                    tint = if (favorite) AllFun.colors.accent else AllFun.colors.textPrimary
                )
                TopBarAction(
                    icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = when {
                        category == ContentCategory.MOVIES && saved ->
                            "Remove from watchlist"
                        category == ContentCategory.MOVIES -> "Add to watchlist"
                        saved -> "Remove from library"
                        else -> "Add to library"
                    },
                    onClick = { vm.toggleLibrary() },
                    tint = if (saved) AllFun.colors.accent else AllFun.colors.textPrimary
                )
            }
        }
    ) { padding ->
        val item = media
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AllFun.colors.accent) }
            return@Scaffold
        }

        val category = item.kind.category()
        val resume = episodes.firstOrNull { it.id == progress?.episodeId }
            ?: episodes.firstOrNull()
        val isCurrent = progress?.episodeId == resume?.id

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "hero") { DetailHero(item, category) }

            item(key = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AllFunButton(
                        text = when {
                            isCurrent -> "Continue"
                            item.kind.isVideo -> "Play"
                            else -> "Read"
                        },
                        onClick = { onOpen(mediaId, resume?.id, item.kind) },
                        enabled = resume != null,
                        icon = if (item.kind.isVideo) {
                            Icons.Filled.PlayArrow
                        } else {
                            Icons.AutoMirrored.Outlined.MenuBook
                        },
                        modifier = Modifier.weight(1f)
                    )
                    AllFunButton(
                        text = when {
                            category == ContentCategory.MOVIES && item.inLibrary ->
                                "In watchlist"
                            category == ContentCategory.MOVIES -> "Watchlist"
                            item.inLibrary -> "Saved"
                            else -> "Save"
                        },
                        onClick = { vm.toggleLibrary() },
                        variant = AllFunButtonVariant.SECONDARY,
                        icon = if (item.inLibrary) Icons.Filled.Bookmark
                        else Icons.Outlined.BookmarkBorder
                    )
                }
            }

            if (!item.description.isNullOrBlank()) {
                item {
                    val reduceMotion = LocalReduceMotion.current
                    val description = item.description.orEmpty().displayDescription()
                    // A description that was nothing but tracking links has no prose left.
                    if (description.isBlank()) return@item
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .animateContentSize(
                                animationSpec = if (reduceMotion) {
                                    androidx.compose.animation.core.snap()
                                } else {
                                    tween(Motion.MEDIUM, easing = Motion.EaseOut)
                                }
                            )
                    ) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AllFun.colors.textSecondary,
                            maxLines = if (expanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (description.length > 260) {
                            AllFunButton(
                                text = if (expanded) "Show less" else "Read more",
                                onClick = { expanded = !expanded },
                                variant = AllFunButtonVariant.TEXT,
                                icon = if (expanded) {
                                    Icons.Outlined.ExpandLess
                                } else {
                                    Icons.Outlined.ExpandMore
                                },
                                modifier = Modifier.semantics {
                                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                                }
                            )
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
                    modifier = Modifier.padding(top = 8.dp, start = 20.dp, end = 20.dp),
                    thickness = 1.dp,
                    color = AllFun.colors.hairline
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
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AllFun.colors.accent
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Looking for ${item.unitLabel.lowercase()}s\u2026",
                                style = AllFunType.mono,
                                color = AllFun.colors.textSecondary
                            )
                        }
                    } else {
                        Text(
                            text = "No ${item.unitLabel.lowercase()}s yet. The source did not return " +
                                "a ${item.unitLabel.lowercase()} list for this title.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AllFun.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
            } else if (item.kind == MediaKind.SERIES) {
                episodes.groupBy { it.season }.forEach { (season, list) ->
                    if (season > 0) {
                        item("season-$season") {
                            Overline(
                                text = "Season $season",
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
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
                            onRemoveDownload = { pendingRemove = it },
                            modifier = Modifier.animateItem()
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
                        onRemoveDownload = { pendingRemove = it },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    pendingRemove?.let { row ->
        AllFunSheet(
            title = "Remove downloaded file?",
            onDismiss = { pendingRemove = null }
        ) {
            Text(
                text = "\"${row.unitTitle}\" is deleted from this device. You can download it " +
                    "again while ${media?.title ?: "this title"}'s provider is available.",
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
private fun DetailHero(media: MediaEntity, category: ContentCategory) {
    val accent = category.accent()
    val format = formatLabel(media, category)
    val radii = LocalAllFunRadii.current
    val coverShape = RoundedCornerShape(radii.cover)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(156.dp)
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
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.headlineMedium,
                color = AllFun.colors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = listOf(format, countLabel(media.totalUnits, media.unitLabel))
                    .joinToString(" \u00B7 "),
                style = AllFunType.mono,
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                media.year?.toString(),
                media.status?.takeIf { it.isNotBlank() }
            ).joinToString(" \u00B7 ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = AllFun.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val creator = when (category) {
                ContentCategory.BOOKS -> media.author?.let { "By $it" }
                ContentCategory.MANGA -> listOfNotNull(
                    media.author?.let { "By $it" },
                    media.artist?.let { "Art by $it" }
                ).joinToString(" \u00B7 ").takeIf { it.isNotBlank() }
                ContentCategory.MOVIES -> media.author?.takeIf { it.isNotBlank() }
            }
            if (!creator.isNullOrBlank()) {
                Text(
                    text = creator,
                    style = MaterialTheme.typography.bodySmall,
                    color = AllFun.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (media.genreList.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = media.genreList.joinToString(" \u00B7 "),
                    style = AllFunType.mono,
                    color = AllFun.colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (category == ContentCategory.MOVIES && media.castList.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Cast: " + media.castList.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = AllFun.colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
    val streamMode = state.streamProviders
    SectionHeader(
        title = if (streamMode) "Stream providers" else "Available sources",
        subtitle = when {
            state.loading && streamMode -> "Checking which addons can stream this title…"
            state.loading -> "Checking your other installed sources…"
            streamMode -> when (confirmed) {
                0 -> "No installed addon can stream this title right now"
                1 -> "1 addon can stream this title"
                else -> "$confirmed addons can stream this title"
            }
            confirmed > 1 -> "$confirmed sources confirmed this title"
            confirmed == 1 -> "Only one source confirmed this title"
            state.error != null -> "Source check failed"
            else -> "No provider confirmed this title right now"
        }
    )
    if (state.loading || state.switching) {
        LinearProgressIndicator(
            Modifier.fillMaxWidth(),
            color = AllFun.colors.accent,
            trackColor = AllFun.colors.hairline
        )
    }
    state.error?.let { error ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
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
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        state.options.forEachIndexed { index, option ->
            SourceOptionRow(
                option = option,
                category = category,
                enabled = !state.switching,
                streamProviders = streamMode,
                onUse = { onUse(option) },
                modifier = Modifier.staggeredReveal(index)
            )
        }
        if (state.options.isEmpty() && !state.loading) {
            AllFunButton(
                text = if (streamMode) "Find stream add-ons" else "Find sources",
                onClick = onRetry,
                variant = AllFunButtonVariant.SECONDARY
            )
        }
    }
}

@Composable
internal fun SourceOptionRow(
    option: SourceOption,
    category: ContentCategory,
    enabled: Boolean,
    streamProviders: Boolean = false,
    onUse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radii = LocalAllFunRadii.current
    val shape = RoundedCornerShape(radii.group)
    val marked = option.isPrimary || option.isPreferred
    val markColor by animateColorAsState(
        targetValue = if (marked) category.accent() else AllFun.colors.hairline,
        animationSpec = tween(Motion.MEDIUM, easing = Motion.EaseOut),
        label = "sourceRowColor"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(if (marked) AllFun.colors.surface2 else AllFun.colors.bg)
            .border(1.dp, AllFun.colors.hairline, shape)
            .then(
                if (option.available && enabled) Modifier.clickable(onClick = onUse)
                else Modifier
            )
            .padding(start = 0.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A 2 dp rule marks the primary or preferred source; no tinted card.
        Box(
            Modifier
                .width(2.dp)
                .height(44.dp)
                .background(markColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.sourceName,
                    style = MaterialTheme.typography.titleSmall,
                    color = AllFun.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (option.isPreferred) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = if (streamProviders) {
                            "Preferred stream add-on"
                        } else {
                            "Preferred source"
                        },
                        tint = AllFun.colors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            val detail = option.statusLabel
                ?: option.message
                ?: if (option.available) "Available" else "Unavailable"
            Text(
                text = detail,
                style = AllFunType.mono,
                color = if (option.available) {
                    AllFun.colors.textSecondary
                } else {
                    AllFun.colors.danger
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        AllFunButton(
            text = when {
                streamProviders && option.isPreferred -> "Selected"
                streamProviders -> "Use"
                option.isPrimary -> "Current"
                option.available -> "Use"
                else -> "Unavailable"
            },
            onClick = onUse,
            variant = AllFunButtonVariant.TEXT,
            enabled = option.available && enabled
        )
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
    onRemoveDownload: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = if (total != null && total > 0L && position != null) {
        (position.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val status = download?.status

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (episode.number <= 0.0) {
                // Providers that only expose titles leave the numeric index unset. A
                // topic icon reads better than the placeholder number "-1".
                Icon(
                    imageVector = if (kind.isVideo) Icons.Filled.PlayArrow
                    else Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = if (fraction > 0f) kind.accentColor()
                    else AllFun.colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = formatNumber(episode.number),
                    style = AllFunType.mono,
                    color = if (fraction > 0f) kind.accentColor()
                    else AllFun.colors.textTertiary
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyLarge,
                color = AllFun.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when (status) {
                DownloadStatus.COMPLETED ->
                    "Downloaded \u00B7 ${formatBytes(download?.totalBytes ?: 0L)}"
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
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = AllFunType.mono,
                    color = when (status) {
                        DownloadStatus.FAILED -> AllFun.colors.danger
                        DownloadStatus.COMPLETED -> AllFun.colors.success
                        else -> AllFun.colors.textTertiary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (fraction > 0f) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(AllFun.colors.hairline)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(2.dp)
                            .background(kind.accentColor())
                    )
                }
            }
        }
        if (canDownload) {
            when (status) {
                DownloadStatus.COMPLETED -> IconButton(onClick = { onRemoveDownload(download) }) {
                    Icon(
                        Icons.Outlined.DownloadDone,
                        contentDescription = "Remove downloaded ${episode.title}",
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
                                contentDescription = "Cancel download of ${episode.title}",
                                modifier = Modifier.size(11.dp),
                                tint = AllFun.colors.textSecondary
                            )
                        }
                    }
                DownloadStatus.FAILED -> IconButton(onClick = { onRetryDownload(download.key) }) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = "Retry download of ${episode.title}",
                        tint = AllFun.colors.danger
                    )
                }
                DownloadStatus.CANCELED, null -> IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = "Download ${episode.title}",
                        tint = AllFun.colors.textTertiary
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
