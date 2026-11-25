package app.allfun.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.SourceOption
import app.allfun.data.content.category
import app.allfun.data.content.displayDescription
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.ui.formatDuration
import app.allfun.ui.formatNumber
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFDivider
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFOverline
import app.allfun.ui.glass.AFSpinner
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.BottomScrim
import app.allfun.ui.glass.GlassButton
import app.allfun.ui.glass.GlassChip
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
 * The reading detail page: cover, one primary action, the source it came from, the
 * description and the chapter list. Chapters are rows on the world, not a stack of cards,
 * so a title with 800 chapters still scrolls at the same cost as one with 8.
 */
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

    val toast = rememberGlassToastState()
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showChapterSheet by rememberSaveable { mutableStateOf(false) }
    var actionChapter by remember { mutableStateOf<EpisodeEntity?>(null) }
    var pendingRemove by remember { mutableStateOf<DownloadEntity?>(null) }
    var sort by rememberSaveable { mutableStateOf(ChapterSort.SOURCE) }
    var filter by rememberSaveable { mutableStateOf(ChapterFilter.ALL) }
    var descriptionExpanded by rememberSaveable(media.id) {
        mutableStateOf(!media.inLibrary)
    }
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

    val downloadedIds = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED }
            .mapTo(mutableSetOf()) { it.unitId }
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
    val unread = remember(episodes) { episodes.count { !it.read } }
    val allDownloaded = episodes.isNotEmpty() &&
        downloads.count { it.status == DownloadStatus.COMPLETED } == episodes.size
    val listState = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 96
        }
    }

    WorldBackdrop(section = sectionFor(media)) {
        Box(Modifier.fillMaxSize()) {
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
                item(key = "hero") { ReadingHero(media = media, sourceName = sourceName) }

                item(key = "primary") {
                    GlassPrimaryAction(
                        label = when {
                            episodes.isEmpty() -> "No ${media.unitLabel.lowercase()}s yet"
                            hasRead || progress != null -> "Continue"
                            else -> "Start reading"
                        },
                        onClick = {
                            resumeChapter?.let { onOpen(mediaId, it.id, media.kind) }
                        },
                        icon = if (media.kind.isVideo) AFIcons.Play else AFIcons.Book,
                        enabled = resumeChapter != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            label = if (media.inLibrary) "Saved" else "Save",
                            onClick = vm::toggleLibrary,
                            icon = AFIcons.Bookmark,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            label = if (media.favorite) "Liked" else "Like",
                            onClick = vm::toggleFavorite,
                            icon = if (media.favorite) AFIcons.HeartFilled else AFIcons.Heart,
                            modifier = Modifier.weight(1f)
                        )
                        if (canDownload) {
                            GlassButton(
                                label = if (allDownloaded) "Saved offline" else "Download",
                                onClick = vm::download,
                                icon = if (allDownloaded) AFIcons.Check else AFIcons.Download,
                                enabled = !allDownloaded,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item(key = "source") {
                    ReadingSourcePanel(
                        state = sourceState,
                        currentName = sourceName,
                        onOpen = { showSources = true },
                        onRetry = vm::findSources
                    )
                }

                if (!media.description.isNullOrBlank() || media.genreList.isNotEmpty()) {
                    item(key = "about") {
                        val description = media.description.orEmpty().displayDescription()
                        GlassPanel(header = "About") {
                            if (description.isNotBlank()) {
                                AFText(
                                    text = description,
                                    style = AFTheme.type.bodySmall,
                                    color = AFTheme.colors.textMid,
                                    onGlass = true,
                                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 6,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.animateContentSize(
                                        animationSpec = if (reduceMotion) snap() else tween(200)
                                    )
                                )
                                if (description.length > 280) {
                                    AFTextButton(
                                        label = if (descriptionExpanded) {
                                            "Show less"
                                        } else {
                                            "Read more"
                                        },
                                        onClick = {
                                            descriptionExpanded = !descriptionExpanded
                                        }
                                    )
                                }
                            }
                            if (media.genreList.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                AFText(
                                    text = media.genreList.take(6).joinToString(" · "),
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

                item(key = "chapters-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AFText(
                            text = "Chapters",
                            style = AFTheme.type.heading,
                            color = AFTheme.colors.textHi,
                            modifier = Modifier.weight(1f)
                        )
                        if (episodes.isNotEmpty()) {
                            AFText(
                                text = visibleChapters.size.toString(),
                                style = AFTheme.type.numeric,
                                color = AFTheme.colors.textLo,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            AFIconButton(
                                icon = AFIcons.Filter,
                                contentDescription = "Sort and filter chapters",
                                onClick = { showChapterSheet = true },
                                active = filter != ChapterFilter.ALL ||
                                    sort != ChapterSort.SOURCE
                            )
                        }
                    }
                }

                if (visibleChapters.isEmpty()) {
                    item(key = "chapters-empty") {
                        when {
                            preparing -> Row(verticalAlignment = Alignment.CenterVertically) {
                                AFSpinner(size = 16.dp)
                                Spacer(Modifier.width(10.dp))
                                AFText(
                                    text = "Looking for ${media.unitLabel.lowercase()}s…",
                                    style = AFTheme.type.bodySmall,
                                    color = AFTheme.colors.textLo
                                )
                            }

                            episodes.isEmpty() -> GlassEmpty(
                                title = "No chapters yet",
                                body = "The source did not return a chapter list for this " +
                                    "title. Refreshing asks it again.",
                                actionLabel = "Try again",
                                onAction = vm::refreshNow
                            )

                            else -> GlassEmpty(
                                title = "Nothing matches",
                                body = "No chapters match this filter. There are " +
                                    "${episodes.size} in total.",
                                actionLabel = "Show all",
                                onAction = { filter = ChapterFilter.ALL }
                            )
                        }
                    }
                } else {
                    itemsIndexed(visibleChapters, key = { _, chapter -> chapter.id }) { _, chapter ->
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
                        text = media.title,
                        style = AFTheme.type.heading,
                        color = AFTheme.colors.textHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                if (!collapsed) Spacer(Modifier.weight(1f))
                AFIconButton(
                    icon = AFIcons.More,
                    contentDescription = "More actions",
                    onClick = { showOverflow = true }
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

    if (showOverflow) {
        GlassSheet(onDismiss = { showOverflow = false }) {
            AFText(
                text = "This title",
                style = AFTheme.type.title,
                color = AFTheme.colors.textHi,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            SheetRow(
                title = "Refresh",
                subtitle = "Ask the source for its chapter list again",
                leading = {
                    AFIcon(AFIcons.Refresh, null, tint = AFTheme.colors.textMid, size = 20.dp)
                },
                onClick = {
                    showOverflow = false
                    vm.refreshNow()
                }
            )
            if (unread > 0) {
                SheetRow(
                    title = "Mark all as read",
                    subtitle = "$unread unread " +
                        if (unread == 1) "chapter" else "chapters",
                    leading = {
                        AFIcon(AFIcons.Check, null, tint = AFTheme.colors.textMid, size = 20.dp)
                    },
                    onClick = {
                        showOverflow = false
                        vm.markAllRead()
                    }
                )
            }
            SheetRow(
                title = "Available sources",
                subtitle = "Every installed source that knows this title",
                leading = {
                    AFIcon(AFIcons.Globe, null, tint = AFTheme.colors.textMid, size = 20.dp)
                },
                onClick = {
                    showOverflow = false
                    showSources = true
                }
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showChapterSheet) {
        ChapterListSheet(
            sort = sort,
            filter = filter,
            total = episodes.size,
            unread = unread,
            downloaded = episodes.count { it.id in downloadedIds },
            onSort = { sort = it },
            onFilter = { filter = it },
            onDismiss = { showChapterSheet = false }
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
        GlassDialog(
            title = "Remove downloaded file?",
            body = "\"${row.unitTitle}\" is deleted from this device. You can download it " +
                "again while ${media.title}'s provider is available.",
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

private fun sectionFor(media: MediaEntity): Section = when (media.kind.category()) {
    ContentCategory.MOVIES -> Section.MOVIES
    ContentCategory.BOOKS -> Section.BOOKS
    ContentCategory.MANGA -> Section.MANGA
}

@Composable
private fun ReadingHero(media: MediaEntity, sourceName: String) {
    val colors = AFTheme.colors
    val status = media.status?.takeIf { it.isNotBlank() }
    val meta = listOfNotNull(
        status,
        media.year?.toString(),
        sourceName.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    GlassPanel {
        Row {
            Box(
                Modifier
                    .width(104.dp)
                    .height(156.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.bg2)
            ) {
                PosterArtwork(media.title, media.coverUrl, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                AFText(
                    text = media.title,
                    style = AFTheme.type.title,
                    color = colors.textHi,
                    onGlass = true,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                val author = media.author?.takeIf { it.isNotBlank() }
                if (author != null) {
                    Spacer(Modifier.height(6.dp))
                    AFText(
                        text = "By $author",
                        style = AFTheme.type.caption,
                        color = colors.textMid,
                        onGlass = true,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val artist = media.artist?.takeIf {
                    it.isNotBlank() && it != media.author
                }
                if (artist != null) {
                    Spacer(Modifier.height(2.dp))
                    AFText(
                        text = "Art by $artist",
                        style = AFTheme.type.caption,
                        color = colors.textLo,
                        onGlass = true,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    AFText(
                        text = meta,
                        style = AFTheme.type.caption,
                        color = colors.textLo,
                        onGlass = true,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (media.totalUnits > 0) {
                    Spacer(Modifier.height(4.dp))
                    AFText(
                        text = "${media.totalUnits} " + media.unitLabel.lowercase() +
                            if (media.totalUnits == 1) "" else "s",
                        style = AFTheme.type.numeric,
                        color = colors.textLo,
                        onGlass = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingSourcePanel(
    state: DetailViewModel.SourceState,
    currentName: String,
    onOpen: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = AFTheme.colors
    val confirmed = state.options.count { it.available }
    val current = state.options.firstOrNull { it.isPrimary }
        ?: state.options.firstOrNull { it.isPreferred }
        ?: state.options.firstOrNull { it.available }
    val summary = when {
        state.loading -> "Checking your other installed sources…"
        confirmed > 1 -> "$confirmed sources confirmed this title"
        confirmed == 1 -> "Only one source confirmed this title"
        state.error != null -> "Source check failed"
        else -> "No provider confirmed this title right now"
    }
    GlassPanel(header = "Source") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AFText(
                    text = current?.sourceName
                        ?: currentName.takeIf { it.isNotBlank() }
                        ?: "No source selected",
                    style = AFTheme.type.body,
                    color = colors.textHi,
                    onGlass = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AFText(
                    text = summary,
                    style = AFTheme.type.caption,
                    color = if (state.error != null) colors.failed else colors.textLo,
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
            AFTextButton("Retry", onRetry, color = colors.failed)
        }
    }
}

@Composable
private fun ReadingSourceSheet(
    state: DetailViewModel.SourceState,
    category: ContentCategory,
    onUse: (SourceOption) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassSheet(onDismiss = onDismiss) {
        AFText(
            text = "Available sources",
            style = AFTheme.type.title,
            color = AFTheme.colors.textHi
        )
        Spacer(Modifier.height(4.dp))
        AFText(
            text = when {
                state.loading -> "Checking your other installed sources…"
                state.options.count { it.available } > 1 ->
                    "${state.options.count { it.available }} sources confirmed this title"
                state.error != null -> "Source check failed"
                else -> "No other provider confirmed this title right now"
            },
            style = AFTheme.type.caption,
            color = AFTheme.colors.textLo
        )
        if (state.loading || state.switching) {
            Spacer(Modifier.height(10.dp))
            GlassProgress(progress = 0.4f, height = 3.dp)
        }
        Spacer(Modifier.height(8.dp))
        state.options.forEach { option ->
            SourceOptionRow(
                option = option,
                category = category,
                enabled = !state.switching,
                onUse = { onUse(option) }
            )
        }
        if (state.options.isEmpty() && !state.loading) {
            Spacer(Modifier.height(8.dp))
            GlassButton(label = "Find sources", onClick = onRetry, icon = AFIcons.Search)
        }
        if (state.error != null) {
            Spacer(Modifier.height(4.dp))
            AFTextButton("Retry", onRetry, color = AFTheme.colors.failed)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingChapterRow(
    chapter: EpisodeEntity,
    kind: MediaKind,
    progress: app.allfun.data.db.ProgressEntity?,
    download: DownloadEntity?,
    canDownload: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onRemoveDownload: (DownloadEntity) -> Unit
) {
    val colors = AFTheme.colors
    val haptics = rememberHaptics()
    val read = chapter.read
    val readPercent = progress?.fraction
        ?.takeIf { it > 0f }
        ?.let { (it * 100).toInt() }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Open ${chapterLabel(chapter, kind)}",
                    onLongClickLabel = "Chapter actions",
                    onClick = {
                        haptics.tick()
                        onClick()
                    },
                    onLongClick = {
                        haptics.medium()
                        onLongClick()
                    }
                )
                .semantics { stateDescription = if (read) "Read" else "Unread" }
                .padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
                if (chapter.number > 0) {
                    AFText(
                        text = formatNumber(chapter.number),
                        style = AFTheme.type.numeric,
                        color = if (read) colors.textLo else colors.accent
                    )
                } else {
                    AFIcon(
                        icon = if (kind.isVideo) AFIcons.Play else AFIcons.Book,
                        contentDescription = null,
                        tint = if (read) colors.textLo else colors.accent,
                        size = 18.dp
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!read) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    AFText(
                        text = chapterLabel(chapter, kind),
                        style = AFTheme.type.body,
                        color = if (read) colors.textMid else colors.textHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val subtitle = chapterSubtitle(chapter, download?.status)
                AFText(
                    text = if (readPercent != null) "$subtitle · $readPercent%" else subtitle,
                    style = AFTheme.type.caption,
                    color = if (read) colors.textLo else colors.textMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val fraction = progress?.fraction?.coerceIn(0f, 1f) ?: 0f
                if (fraction > 0f) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(88.dp)) {
                        GlassProgress(progress = fraction, height = 3.dp)
                    }
                }
            }
            if (canDownload) {
                Spacer(Modifier.width(4.dp))
                ChapterDownloadAction(
                    chapter = chapter,
                    download = download,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onRetryDownload = onRetryDownload,
                    onRemoveDownload = onRemoveDownload
                )
            }
        }
        AFDivider(startInset = 52.dp)
    }
}

@Composable
private fun ChapterDownloadAction(
    chapter: EpisodeEntity,
    download: DownloadEntity?,
    onDownload: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onRemoveDownload: (DownloadEntity) -> Unit
) {
    val colors = AFTheme.colors
    when (download?.status) {
        DownloadStatus.COMPLETED -> AFIconButton(
            icon = AFIcons.Check,
            contentDescription = "Remove downloaded ${chapter.title}",
            onClick = { onRemoveDownload(download) },
            tint = colors.ok,
            visualSize = 40.dp
        )
        DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.WAITING -> {
            val fraction = (download.progress.takeIf { it >= 0 } ?: 0) / 100f
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Cancel download of ${chapter.title}"
                    ) { onCancelDownload(download.key) },
                contentAlignment = Alignment.Center
            ) {
                DownloadRing(
                    progress = fraction,
                    color = colors.accent,
                    track = colors.hairline
                )
                AFIcon(
                    icon = AFIcons.Close,
                    contentDescription = null,
                    tint = colors.textMid,
                    size = 11.dp
                )
            }
        }
        DownloadStatus.FAILED -> AFIconButton(
            icon = AFIcons.Refresh,
            contentDescription = "Retry download of ${chapter.title}",
            onClick = { onRetryDownload(download.key) },
            tint = colors.failed,
            visualSize = 40.dp
        )
        DownloadStatus.CANCELED, null -> AFIconButton(
            icon = AFIcons.Download,
            contentDescription = "Download ${chapter.title}",
            onClick = onDownload,
            tint = colors.textMid,
            visualSize = 40.dp
        )
    }
}

@Composable
private fun DownloadRing(progress: Float, color: Color, track: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke)
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterListSheet(
    sort: ChapterSort,
    filter: ChapterFilter,
    total: Int,
    unread: Int,
    downloaded: Int,
    onSort: (ChapterSort) -> Unit,
    onFilter: (ChapterFilter) -> Unit,
    onDismiss: () -> Unit
) {
    GlassSheet(onDismiss = onDismiss) {
        AFText(
            text = "Chapter list",
            style = AFTheme.type.title,
            color = AFTheme.colors.textHi
        )
        Spacer(Modifier.height(4.dp))
        AFText(
            text = "$total total · $unread unread · $downloaded offline",
            style = AFTheme.type.caption,
            color = AFTheme.colors.textLo
        )
        Spacer(Modifier.height(12.dp))
        AFOverline("Sort")
        Spacer(Modifier.height(4.dp))
        ChapterSort.entries.forEach { option ->
            SheetRow(
                title = option.label,
                selected = sort == option,
                onClick = { onSort(option) }
            )
        }
        Spacer(Modifier.height(12.dp))
        AFOverline("Filter")
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChapterFilter.entries.forEach { option ->
                val count = when (option) {
                    ChapterFilter.ALL -> total
                    ChapterFilter.UNREAD -> unread
                    ChapterFilter.DOWNLOADED -> downloaded
                }
                GlassChip(
                    label = option.label,
                    selected = filter == option,
                    badge = count,
                    onClick = { onFilter(option) }
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        GlassButton(
            label = "Done",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ChapterActionSheet(
    chapter: EpisodeEntity,
    kind: MediaKind,
    progress: app.allfun.data.db.ProgressEntity?,
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
    val colors = AFTheme.colors
    GlassSheet(onDismiss = onDismiss) {
        AFText(
            text = chapterLabel(chapter, kind),
            style = AFTheme.type.title,
            color = colors.textHi,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val detail = listOfNotNull(
            chapter.pageCount?.let { "$it ${if (it == 1) "page" else "pages"}" },
            progress?.fraction?.takeIf { it > 0f }?.let { "${(it * 100).toInt()}% read" },
            download?.status?.let { downloadStatusLabel(it) }
        ).joinToString(" · ")
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            AFText(
                text = detail,
                style = AFTheme.type.caption,
                color = colors.textLo
            )
        }
        Spacer(Modifier.height(10.dp))
        SheetRow(
            title = "Read now",
            leading = {
                AFIcon(
                    icon = if (kind.isVideo) AFIcons.Play else AFIcons.Book,
                    contentDescription = null,
                    tint = colors.textMid,
                    size = 20.dp
                )
            },
            onClick = onRead
        )
        SheetRow(
            title = if (chapter.read) "Mark as unread" else "Mark as read",
            leading = {
                AFIcon(AFIcons.Check, null, tint = colors.textMid, size = 20.dp)
            },
            onClick = onToggleRead
        )
        if (canDownload) {
            when (download?.status) {
                DownloadStatus.COMPLETED -> SheetRow(
                    title = "Delete downloaded file",
                    leading = {
                        AFIcon(AFIcons.Trash, null, tint = colors.failed, size = 20.dp)
                    },
                    onClick = { onRemoveDownload(download) }
                )
                DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.WAITING ->
                    SheetRow(
                        title = "Cancel download",
                        leading = {
                            AFIcon(AFIcons.Close, null, tint = colors.textMid, size = 20.dp)
                        },
                        onClick = { onCancelDownload(download.key) }
                    )
                DownloadStatus.FAILED -> {
                    SheetRow(
                        title = "Retry download",
                        leading = {
                            AFIcon(AFIcons.Refresh, null, tint = colors.textMid, size = 20.dp)
                        },
                        onClick = { onRetryDownload(download.key) }
                    )
                    SheetRow(
                        title = "Remove failed download",
                        leading = {
                            AFIcon(AFIcons.Trash, null, tint = colors.failed, size = 20.dp)
                        },
                        onClick = { onRemoveDownload(download) }
                    )
                }
                DownloadStatus.CANCELED, null -> SheetRow(
                    title = "Download",
                    leading = {
                        AFIcon(AFIcons.Download, null, tint = colors.textMid, size = 20.dp)
                    },
                    onClick = onDownload
                )
            }
        }
        Spacer(Modifier.height(6.dp))
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

private fun downloadStatusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.COMPLETED -> "Downloaded"
    DownloadStatus.RUNNING -> "Downloading"
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.WAITING -> "Waiting"
    DownloadStatus.FAILED -> "Download failed"
    DownloadStatus.CANCELED -> "Download cancelled"
}
