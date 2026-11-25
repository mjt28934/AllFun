package app.allfun.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.db.LibraryRow
import app.allfun.data.settings.LibrarySort
import app.allfun.data.settings.LibraryView
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.formatBytes
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFOverline
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.BottomScrim
import app.allfun.ui.glass.GlassChip
import app.allfun.ui.glass.GlassDialog
import app.allfun.ui.glass.GlassEmpty
import app.allfun.ui.glass.GlassProgress
import app.allfun.ui.glass.GlassSheet
import app.allfun.ui.glass.GlassSurface
import app.allfun.ui.glass.GlassTextField
import app.allfun.ui.glass.GlassTier
import app.allfun.ui.glass.GlassTopCapsule
import app.allfun.ui.glass.LocalGlassNavState
import app.allfun.ui.glass.PosterArtwork
import app.allfun.ui.glass.PosterBadge
import app.allfun.ui.glass.PosterCard
import app.allfun.ui.glass.ScreenHeader
import app.allfun.ui.glass.Section
import app.allfun.ui.glass.SheetRow
import app.allfun.ui.glass.TopScrim
import app.allfun.ui.glass.WorldBackdrop
import java.text.DateFormat
import java.util.Date

private val NAV_RESERVE = 140.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpenMedia: (String) -> Unit,
    onBrowse: () -> Unit
) {
    val vm = rememberViewModel { LibraryViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navState = LocalGlassNavState.current
    var sortOpen by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> navState.onScroll(index, offset) }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> navState.onScroll(index, offset) }
    }
    DisposableEffect(Unit) {
        onDispose { navState.show() }
    }

    WorldBackdrop(section = Section.LIBRARY) {
        Box(Modifier.fillMaxSize()) {
            if (state.view == LibraryView.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = state.grid.minWidth.dp),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 24.dp,
                        bottom = NAV_RESERVE
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "library-header") {
                        LibraryHeader(
                            state = state,
                            onQuery = vm::setQuery,
                            onFilter = vm::setFilter,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 60.dp, bottom = 8.dp)
                        )
                    }
                    if (state.items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "library-empty") {
                            LibraryEmpty(state = state, onBrowse = onBrowse)
                        }
                    } else {
                        itemsIndexed(
                            state.items,
                            key = { _, row -> row.media.id }
                        ) { _, row ->
                            val download = state.downloads[row.media.id]
                            PosterCard(
                                title = row.media.title,
                                imageUrl = row.media.coverUrl,
                                subtitle = librarySubtitle(row),
                                progress = row.fraction,
                                badges = buildList {
                                    if (download != null && download.completed > 0) {
                                        add(
                                            PosterBadge(
                                                icon = AFIcons.Download,
                                                contentDescription = "Downloaded",
                                                tone = AFTheme.colors.ok
                                            )
                                        )
                                    }
                                },
                                onClick = { onOpenMedia(row.media.id) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 24.dp,
                        bottom = NAV_RESERVE
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "library-header") {
                        LibraryHeader(
                            state = state,
                            onQuery = vm::setQuery,
                            onFilter = vm::setFilter,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 60.dp, bottom = 8.dp)
                        )
                    }
                    if (state.items.isEmpty()) {
                        item(key = "library-empty") {
                            LibraryEmpty(state = state, onBrowse = onBrowse)
                        }
                    } else {
                        itemsIndexed(
                            state.items,
                            key = { _, row -> row.media.id }
                        ) { _, row ->
                            LibraryListRow(
                                row = row,
                                download = state.downloads[row.media.id],
                                onClick = { onOpenMedia(row.media.id) },
                                onToggleFavorite = { vm.toggleFavorite(row.media.id) },
                                onRemoveDownload = state.downloads[row.media.id]
                                    ?.takeIf { it.completed > 0 }
                                    ?.let { { pendingRemove = row.media.id } }
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
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                AFText(
                    text = "Library",
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                AFIconButton(
                    icon = if (state.view == LibraryView.GRID) AFIcons.List else AFIcons.Grid,
                    contentDescription = if (state.view == LibraryView.GRID) {
                        "Switch to list layout"
                    } else {
                        "Switch to grid layout"
                    },
                    onClick = {
                        vm.setView(
                            if (state.view == LibraryView.GRID) LibraryView.LIST
                            else LibraryView.GRID
                        )
                    }
                )
                AFIconButton(AFIcons.Sort, "Sort library", { sortOpen = true })
            }
        }
    }

    if (sortOpen) {
        GlassSheet(onDismiss = { sortOpen = false }) {
            AFText("Sort library", style = AFTheme.type.title, color = AFTheme.colors.textHi)
            Spacer(Modifier.height(10.dp))
            LibrarySort.entries.forEach { option ->
                SheetRow(
                    title = option.label,
                    subtitle = when (option) {
                        LibrarySort.RECENTLY_UPDATED -> "Recently changed titles first"
                        LibrarySort.RECENTLY_ADDED -> "Newest saves first"
                        LibrarySort.RECENTLY_OPENED -> "Pick up where you left off"
                        LibrarySort.TITLE -> "Alphabetical by title"
                        LibrarySort.AUTHOR -> "Alphabetical by author"
                    },
                    selected = state.sort == option,
                    onClick = {
                        vm.setSort(option)
                        sortOpen = false
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    if (pendingRemove != null) {
        val id = pendingRemove!!
        val title = state.all.firstOrNull { it.media.id == id }?.media?.title.orEmpty()
        GlassDialog(
            title = "Remove downloaded files?",
            body = "The offline files for \"$title\" are deleted. The title stays in your " +
                "library and can be opened again while its provider is available.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                vm.removeDownloads(id)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryHeader(
    state: LibraryUiState,
    onQuery: (String) -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        AFOverline("Your shelf")
        Spacer(Modifier.height(8.dp))
        ScreenHeader(
            title = "Library",
            subtitle = buildString {
                append("${state.all.size} ")
                append(if (state.all.size == 1) "title" else "titles")
                if (state.items.size != state.all.size) append(" · ${state.items.size} shown")
                append(" · ${state.sort.label}")
            }
        )
        Spacer(Modifier.height(16.dp))
        GlassTextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = "Search title, author or genre",
            leading = AFIcons.Search,
            trailing = {
                if (state.query.isNotEmpty()) {
                    AFIconButton(
                        icon = AFIcons.Close,
                        contentDescription = "Clear search",
                        onClick = { onQuery("") },
                        visualSize = 32.dp
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryFilter.entries.forEach { option ->
                val count = state.counts[option] ?: 0
                GlassChip(
                    label = option.label,
                    selected = state.filter == option,
                    badge = if (count > 0) count else null,
                    onClick = { onFilter(option) }
                )
            }
        }
    }
}

@Composable
private fun LibraryEmpty(state: LibraryUiState, onBrowse: () -> Unit) {
    GlassEmpty(
        title = when {
            state.all.isEmpty() -> "Nothing here yet"
            state.query.isNotBlank() -> "No matches"
            else -> "No ${state.filter.label.lowercase()} yet"
        },
        body = when {
            state.all.isEmpty() ->
                "Titles you save, read or download land on this shelf. Install an extension " +
                    "to start browsing."
            state.query.isNotBlank() ->
                "Nothing matches \"${state.query}\". Try a shorter search."
            else -> "Try another category, or add something new."
        },
        actionLabel = "Browse sources",
        onAction = onBrowse
    )
}

@Composable
private fun LibraryListRow(
    row: LibraryRow,
    download: LibraryDownload?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveDownload: (() -> Unit)?
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassTier.CLEAR,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        onClickLabel = "Open ${row.media.title}"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(52.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(7.dp))
            ) {
                PosterArtwork(row.media.title, row.media.coverUrl, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AFText(
                    text = row.media.title,
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = librarySubtitle(row)
                if (subtitle != null) {
                    AFText(
                        text = subtitle,
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (download != null && download.hasAny) {
                    Spacer(Modifier.height(3.dp))
                    AFText(
                        text = download.detail(),
                        style = AFTheme.type.caption,
                        color = if (download.failed > 0) AFTheme.colors.failed
                        else AFTheme.colors.textLo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (row.fraction != null) {
                    Spacer(Modifier.height(8.dp))
                    GlassProgress(progress = row.fraction ?: 0f, height = 3.dp)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AFIconButton(
                    icon = if (row.media.favorite) AFIcons.HeartFilled else AFIcons.Heart,
                    contentDescription = if (row.media.favorite) {
                        "Remove from favorites"
                    } else {
                        "Add to favorites"
                    },
                    onClick = onToggleFavorite,
                    tint = if (row.media.favorite) AFTheme.colors.accent else AFTheme.colors.textMid
                )
                if (onRemoveDownload != null) {
                    AFIconButton(
                        icon = AFIcons.Trash,
                        contentDescription = "Remove downloaded files",
                        onClick = onRemoveDownload,
                        tint = AFTheme.colors.textLo,
                        visualSize = 40.dp
                    )
                }
            }
        }
    }
}

private fun librarySubtitle(row: LibraryRow): String? = listOfNotNull(
    row.media.year?.toString(),
    row.media.author?.takeIf { it.isNotBlank() }
).joinToString(" · ").ifBlank { null }

/** "Pick up where you left off" lives on the cards as progress, not a second sideways row. */
private fun LibraryDownload.badge(): String? = when {
    active > 0 && waitingForWifi -> "Waiting for Wi-Fi"
    active > 0 -> progress?.let { "Downloading $it%" } ?: "Downloading"
    completed > 0 -> "Downloaded"
    failed > 0 -> "Download failed"
    else -> null
}

private fun LibraryDownload.detail(): String = buildList {
    if (completed > 0) {
        add("Downloaded $completed ${if (completed == 1) "file" else "files"}")
        if (bytes > 0) add(formatBytes(bytes))
        completedAt?.let { add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) }
    }
    if (active > 0) {
        add(if (waitingForWifi) "Waiting for Wi-Fi" else progress?.let { "Downloading $it%" } ?: "Downloading")
    }
    if (failed > 0) add("$failed failed")
}.joinToString(" · ")
