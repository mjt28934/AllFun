package app.allfun.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.SourceOption
import app.allfun.data.content.category
import app.allfun.data.content.displayDescription
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.countLabel
import app.allfun.ui.formatBytes
import app.allfun.ui.formatDuration
import app.allfun.ui.formatNumber
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFOverline
import app.allfun.ui.glass.AFSpinner
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.BottomScrim
import app.allfun.ui.glass.GlassButton
import app.allfun.ui.glass.GlassDialog
import app.allfun.ui.glass.GlassEmpty
import app.allfun.ui.glass.GlassPanel
import app.allfun.ui.glass.GlassPrimaryAction
import app.allfun.ui.glass.GlassProgress
import app.allfun.ui.glass.GlassSheet
import app.allfun.ui.glass.GlassToastHost
import app.allfun.ui.glass.GlassTopCapsule
import app.allfun.ui.glass.PosterArtwork
import app.allfun.ui.glass.Section
import app.allfun.ui.glass.SheetRow
import app.allfun.ui.glass.TopScrim
import app.allfun.ui.glass.WorldBackdrop
import app.allfun.ui.glass.rememberGlassToastState
import app.allfun.ui.glass.rememberHaptics
import app.allfun.ui.theme.LocalReduceMotion

private val NAV_RESERVE = 140.dp

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
    WorldBackdrop(section = Section.MOVIES) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AFSpinner(size = 28.dp)
            }
            GlassTopCapsule(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                onBack = onBack
            ) {}
        }
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
    val toast = rememberGlassToastState()
    var expanded by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<DownloadEntity?>(null) }
    var sourcesOpen by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(Unit) {
        vm.messages.collect { toast.show(it) }
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
    val item = media
    val category = item?.kind?.category() ?: ContentCategory.MOVIES
    val resume = item?.let {
        episodes.firstOrNull { episode -> episode.id == progress?.episodeId }
            ?: episodes.firstOrNull()
    }
    val isCurrent = progress?.episodeId == resume?.id
    val allDone = downloads.isNotEmpty() &&
        downloads.none { it.status.isActive } &&
        downloads.count { it.status == DownloadStatus.COMPLETED } == episodes.size

    WorldBackdrop(section = Section.MOVIES) {
        Box(Modifier.fillMaxSize()) {
            if (item == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AFSpinner(size = 28.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = statusTop + 72.dp,
                        bottom = NAV_RESERVE
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "hero") { DetailHero(item, category) }

                    item(key = "actions") {
                        Column {
                            GlassPrimaryAction(
                                label = when {
                                    isCurrent -> "Continue"
                                    item.kind.isVideo -> "Play"
                                    else -> "Read"
                                },
                                onClick = { onOpen(mediaId, resume?.id, item.kind) },
                                icon = if (item.kind.isVideo) AFIcons.Play else AFIcons.Book,
                                enabled = resume != null,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton(
                                    label = when {
                                        item.inLibrary && category == ContentCategory.MOVIES ->
                                            "In watchlist"
                                        item.inLibrary -> "Saved"
                                        category == ContentCategory.MOVIES -> "Watchlist"
                                        else -> "Save"
                                    },
                                    onClick = vm::toggleLibrary,
                                    icon = AFIcons.Bookmark,
                                    modifier = Modifier.weight(1f)
                                )
                                GlassButton(
                                    label = if (item.favorite) "Favorited" else "Favorite",
                                    onClick = vm::toggleFavorite,
                                    icon = if (item.favorite) AFIcons.HeartFilled else AFIcons.Heart,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (!item.description.isNullOrBlank()) {
                        item(key = "about") {
                            val description = item.description.orEmpty().displayDescription()
                            if (description.isNotBlank()) {
                                GlassPanel(header = "About") {
                                    AFText(
                                        text = description,
                                        style = AFTheme.type.bodySmall,
                                        color = AFTheme.colors.textMid,
                                        onGlass = true,
                                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.animateContentSize(
                                            animationSpec = if (reduceMotion) {
                                                androidx.compose.animation.core.snap()
                                            } else {
                                                tween(200)
                                            }
                                        )
                                    )
                                    if (description.length > 260) {
                                        AFTextButton(
                                            label = if (expanded) "Show less" else "Read more",
                                            onClick = { expanded = !expanded }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "sources") {
                        SourceSummary(
                            state = sourceState,
                            onOpen = { sourcesOpen = true },
                            onRetry = vm::findSources
                        )
                    }

                    item(key = "units-header") {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AFText(
                                text = unitsTitle(item),
                                style = AFTheme.type.heading,
                                color = AFTheme.colors.textHi,
                                modifier = Modifier.weight(1f)
                            )
                            if (episodes.isNotEmpty()) {
                                AFText(
                                    text = episodes.size.toString(),
                                    style = AFTheme.type.numeric,
                                    color = AFTheme.colors.textLo
                                )
                            }
                        }
                    }

                    if (episodes.isEmpty()) {
                        item(key = "units-empty") {
                            if (preparing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AFSpinner(size = 16.dp)
                                    Spacer(Modifier.width(10.dp))
                                    AFText(
                                        text = "Looking for ${item.unitLabel.lowercase()}s…",
                                        style = AFTheme.type.bodySmall,
                                        color = AFTheme.colors.textLo
                                    )
                                }
                            } else {
                                AFText(
                                    text = "This source did not return a " +
                                        "${item.unitLabel.lowercase()} list for this title.",
                                    style = AFTheme.type.bodySmall,
                                    color = AFTheme.colors.textLo
                                )
                            }
                        }
                    } else if (item.kind == MediaKind.SERIES) {
                        episodes.groupBy { it.season }.forEach { (season, list) ->
                            if (season > 0) {
                                item(key = "season-$season") {
                                    AFOverline(
                                        text = "Season $season",
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                            items(list, key = { it.id }) { episode ->
                                EpisodeRow(
                                    episode = episode,
                                    kind = item.kind,
                                    position = progress?.takeIf { it.episodeId == episode.id }
                                        ?.position,
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
                                position = progress?.takeIf { it.episodeId == episode.id }
                                    ?.position,
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

            TopScrim(Modifier.align(Alignment.TopCenter))
            BottomScrim(Modifier.align(Alignment.BottomCenter))

            GlassTopCapsule(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                onBack = onBack
            ) {
                AnimatedVisibility(
                    visible = collapsed,
                    enter = fadeIn(tween(if (reduceMotion) 0 else 160)),
                    exit = fadeOut(tween(if (reduceMotion) 0 else 120)),
                    modifier = Modifier.weight(1f)
                ) {
                    AFText(
                        text = item?.title.orEmpty(),
                        style = AFTheme.type.heading,
                        color = AFTheme.colors.textHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                if (!collapsed) Spacer(Modifier.weight(1f))
                if (canDownload) {
                    AFIconButton(
                        icon = if (allDone) AFIcons.Check else AFIcons.Download,
                        contentDescription = if (allDone) {
                            "Everything is downloaded"
                        } else {
                            "Download all ${item?.unitLabel?.lowercase() ?: "items"}"
                        },
                        onClick = { vm.download() },
                        enabled = episodes.isNotEmpty() && !allDone,
                        tint = if (allDone) AFTheme.colors.ok else AFTheme.colors.textHi
                    )
                }
                AFIconButton(
                    icon = if (item?.favorite == true) AFIcons.HeartFilled else AFIcons.Heart,
                    contentDescription = if (item?.favorite == true) {
                        "Remove from favorites"
                    } else {
                        "Add to favorites"
                    },
                    onClick = vm::toggleFavorite,
                    tint = if (item?.favorite == true) AFTheme.colors.accent
                    else AFTheme.colors.textHi
                )
                AFIconButton(
                    icon = AFIcons.Bookmark,
                    contentDescription = when {
                        item?.inLibrary == true -> "Remove from library"
                        category == ContentCategory.MOVIES -> "Add to watchlist"
                        else -> "Add to library"
                    },
                    onClick = vm::toggleLibrary,
                    tint = if (item?.inLibrary == true) AFTheme.colors.accent
                    else AFTheme.colors.textHi
                )
            }

            GlassToastHost(
                state = toast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 108.dp)
            )
        }
    }

    if (sourcesOpen) {
        SourceSheet(
            state = sourceState,
            category = category,
            onUse = { option ->
                sourcesOpen = false
                vm.useSource(option)
            },
            onRetry = vm::findSources,
            onDismiss = { sourcesOpen = false }
        )
    }

    pendingRemove?.let { row ->
        GlassDialog(
            title = "Remove downloaded file?",
            body = "\"${row.unitTitle}\" is deleted from this device. You can download it again " +
                "while ${media?.title ?: "this title"}'s provider is available.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                vm.removeDownload(row.key)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null }
        )
    }
}

@Composable
private fun DetailHero(media: MediaEntity, category: ContentCategory) {
    val format = formatLabel(media, category)
    GlassPanel {
        Row {
            Box(
                Modifier
                    .width(112.dp)
                    .height(168.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                PosterArtwork(media.title, media.coverUrl, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                AFOverline(format)
                Spacer(Modifier.height(6.dp))
                AFText(
                    text = media.title,
                    style = AFTheme.type.title,
                    color = AFTheme.colors.textHi,
                    onGlass = true,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                val count = if (media.totalUnits > 0) {
                    countLabel(media.totalUnits, media.unitLabel)
                } else {
                    null
                }
                val meta = listOfNotNull(
                    media.year?.toString(),
                    media.status?.takeIf { it.isNotBlank() },
                    count
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    AFText(
                        text = meta,
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        onGlass = true,
                        maxLines = 2,
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
                    Spacer(Modifier.height(4.dp))
                    AFText(
                        text = creator,
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textMid,
                        onGlass = true,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (media.genreList.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    AFText(
                        text = media.genreList.take(4).joinToString(" · "),
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        onGlass = true,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (category == ContentCategory.MOVIES && media.castList.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    AFText(
                        text = "Cast: " + media.castList.take(4).joinToString(", "),
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        onGlass = true,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceSummary(
    state: DetailViewModel.SourceState,
    onOpen: () -> Unit,
    onRetry: () -> Unit
) {
    val confirmed = state.options.count { it.available }
    val streamMode = state.streamProviders
    val current = state.options.firstOrNull { it.isPrimary }
        ?: state.options.firstOrNull { it.isPreferred }
        ?: state.options.firstOrNull { it.available }
    val summary = when {
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
    GlassPanel(header = if (streamMode) "Stream providers" else "Sources") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AFText(
                    text = current?.sourceName ?: "No source selected",
                    style = AFTheme.type.body,
                    color = AFTheme.colors.textHi,
                    onGlass = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AFText(
                    text = summary,
                    style = AFTheme.type.caption,
                    color = if (state.error != null) AFTheme.colors.failed
                    else AFTheme.colors.textLo,
                    onGlass = true,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.loading || state.switching) {
                AFSpinner(size = 18.dp)
            } else {
                AFTextButton(
                    label = if (state.options.isEmpty()) "Find" else "Change",
                    onClick = if (state.options.isEmpty()) onRetry else onOpen
                )
            }
        }
        if (state.error != null) {
            Spacer(Modifier.height(4.dp))
            AFTextButton("Retry", onRetry, color = AFTheme.colors.failed)
        }
    }
}

@Composable
private fun SourceSheet(
    state: DetailViewModel.SourceState,
    category: ContentCategory,
    onUse: (SourceOption) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassSheet(onDismiss = onDismiss) {
        AFText(
            text = if (state.streamProviders) "Stream providers" else "Sources",
            style = AFTheme.type.title,
            color = AFTheme.colors.textHi
        )
        Spacer(Modifier.height(4.dp))
        AFText(
            text = "Every installed source that knows this title. Switching keeps your " +
                "progress and library entry.",
            style = AFTheme.type.caption,
            color = AFTheme.colors.textLo
        )
        Spacer(Modifier.height(10.dp))
        if (state.options.isEmpty() && !state.loading) {
            AFText(
                text = "No source is available for this title right now.",
                style = AFTheme.type.bodySmall,
                color = AFTheme.colors.textLo
            )
            Spacer(Modifier.height(8.dp))
            GlassButton(label = "Find sources", onClick = onRetry, icon = AFIcons.Search)
        }
        state.options.forEach { option ->
            SourceOptionRow(
                option = option,
                category = category,
                enabled = !state.switching,
                streamProviders = state.streamProviders,
                onUse = { onUse(option) }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * One source inside the sheet. The whole row is the action; the current source carries a
 * check instead of a "Use" button, so there is never a button that does nothing.
 */
@Composable
internal fun SourceOptionRow(
    option: SourceOption,
    category: ContentCategory,
    enabled: Boolean,
    streamProviders: Boolean = false,
    onUse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val colors = AFTheme.colors
    val current = option.isPrimary || option.isPreferred
    val clickable = option.available && enabled && !option.isPrimary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (clickable) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Use ${option.sourceName}"
                    ) {
                        haptics.tick()
                        onUse()
                    }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            AFText(
                text = option.sourceName,
                style = AFTheme.type.body,
                color = if (option.available) colors.textHi else colors.textLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = option.statusLabel
                ?: option.message
                ?: if (option.available) "Available" else "Unavailable"
            AFText(
                text = detail,
                style = AFTheme.type.caption,
                color = if (option.available) colors.textLo else colors.failed,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        when {
            option.isPrimary -> AFIcon(AFIcons.Check, "Current source", tint = colors.accent)
            !option.available -> AFIcon(AFIcons.Close, "Unavailable", tint = colors.failed, size = 18.dp)
            option.isPreferred -> AFIcon(AFIcons.Sparkle, "Preferred", tint = colors.accent, size = 18.dp)
            else -> AFIcon(AFIcons.ChevronRight, null, tint = colors.textLo, size = 18.dp)
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
    onRemoveDownload: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AFTheme.colors
    val haptics = rememberHaptics()
    val fraction = if (total != null && total > 0L && position != null) {
        (position.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val status = download?.status
    val started = fraction > 0f || status != null

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Open ${episode.title}"
                ) {
                    haptics.tick()
                    onClick()
                }
                .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.width(36.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (episode.number <= 0.0) {
                    AFIcon(
                        icon = if (kind.isVideo) AFIcons.Play else AFIcons.Book,
                        contentDescription = null,
                        tint = if (started) colors.accent else colors.textLo,
                        size = 18.dp
                    )
                } else {
                    AFText(
                        text = formatNumber(episode.number),
                        style = AFTheme.type.numeric,
                        color = if (started) colors.accent else colors.textLo
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                AFText(
                    text = episode.title,
                    style = AFTheme.type.body,
                    color = colors.textHi,
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
                    AFText(
                        text = subtitle,
                        style = AFTheme.type.caption,
                        color = when (status) {
                            DownloadStatus.FAILED -> colors.failed
                            DownloadStatus.COMPLETED -> colors.ok
                            else -> colors.textLo
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (fraction > 0f) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(88.dp)) {
                        GlassProgress(progress = fraction, height = 3.dp)
                    }
                }
            }
            if (canDownload) {
                when (status) {
                    DownloadStatus.COMPLETED -> AFIconButton(
                        icon = AFIcons.Check,
                        contentDescription = "Remove downloaded ${episode.title}",
                        onClick = { onRemoveDownload(download) },
                        tint = colors.ok,
                        visualSize = 40.dp
                    )
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.WAITING ->
                        AFIconButton(
                            icon = AFIcons.Close,
                            contentDescription = "Cancel download of ${episode.title}",
                            onClick = { onCancelDownload(download.key) },
                            tint = colors.textMid,
                            visualSize = 40.dp
                        )
                    DownloadStatus.FAILED -> AFIconButton(
                        icon = AFIcons.Refresh,
                        contentDescription = "Retry download of ${episode.title}",
                        onClick = { onRetryDownload(download.key) },
                        tint = colors.failed,
                        visualSize = 40.dp
                    )
                    DownloadStatus.CANCELED, null -> AFIconButton(
                        icon = AFIcons.Download,
                        contentDescription = "Download ${episode.title}",
                        onClick = onDownload,
                        tint = colors.textMid,
                        visualSize = 40.dp
                    )
                }
            } else if (fraction > 0f) {
                AFIcon(
                    icon = AFIcons.Play,
                    contentDescription = "Continue",
                    tint = colors.accent,
                    size = 18.dp
                )
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
