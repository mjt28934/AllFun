package app.allfun.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.ContentItem
import app.allfun.data.content.ProviderStatus
import app.allfun.data.content.category
import app.allfun.data.content.format
import app.allfun.data.content.refId
import app.allfun.data.db.LibraryRow
import app.allfun.ui.components.GlassQuickAdd
import app.allfun.ui.components.QuickAddViewModel
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFOverline
import app.allfun.ui.glass.AFSpinner
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.BottomScrim
import app.allfun.ui.glass.EndOfList
import app.allfun.ui.glass.GlassChip
import app.allfun.ui.glass.GlassEmpty
import app.allfun.ui.glass.GlassPanel
import app.allfun.ui.glass.GlassPrimaryAction
import app.allfun.ui.glass.GlassProgress
import app.allfun.ui.glass.GlassSheet
import app.allfun.ui.glass.GlassSurface
import app.allfun.ui.glass.GlassTextField
import app.allfun.ui.glass.GlassTier
import app.allfun.ui.glass.GlassToastHost
import app.allfun.ui.glass.GlassTopCapsule
import app.allfun.ui.glass.LocalGlassNavState
import app.allfun.ui.glass.PosterBadge
import app.allfun.ui.glass.PosterCard
import app.allfun.ui.glass.ScreenHeader
import app.allfun.ui.glass.Section
import app.allfun.ui.glass.SheetRow
import app.allfun.ui.glass.SkeletonPosterGrid
import app.allfun.ui.glass.TopScrim
import app.allfun.ui.glass.WorldBackdrop
import app.allfun.ui.glass.rememberGlassToastState
import app.allfun.ui.theme.LocalReduceMotion
import kotlin.math.abs

/** Favorites/watchlist preview rows; the Library owns the full list. */
private const val LIBRARY_PREVIEW = 6

/** Exactly three posters on a phone; tablets get a fourth column. */
private val GRID_MIN_CELL = 108.dp

/** Space reserved at the bottom for the floating nav. */
private val NAV_RESERVE = 140.dp

/**
 * The single category browse experience. Movies, books and manga share the same poster
 * grid, search field, filters and source reporting; the only per-category differences
 * are the world hue and the labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryScreen(
    container: AppContainer,
    category: ContentCategory,
    onOpenMedia: (String) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenGlobalSearch: () -> Unit
) {
    val vm = rememberViewModel(key = category.name) { CategoryViewModel(category, container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val libraryRows by vm.libraryRows.collectAsStateWithLifecycle()
    val downloadedMedia by vm.downloadedMedia.collectAsStateWithLifecycle()
    val toast = rememberGlassToastState()
    val quickAdd = rememberViewModel(key = "quick-add") { QuickAddViewModel(container) }
    val searchFocus = remember { FocusRequester() }
    var statusExpanded by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    val navState = LocalGlassNavState.current
    val section = category.section()

    LaunchedEffect(Unit) {
        vm.messages.collect { toast.show(it) }
    }
    LaunchedEffect(Unit) {
        quickAdd.messages.collect { toast.show(it) }
    }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        navState.show()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        vm.openMediaId.collect { id ->
            if (id != null) {
                onOpenMedia(id)
                vm.clearOpen()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { navState.show() }
    }

    val libraryForCategory = libraryRows.filter { it.media.kind.category() == category }
    val favorites = libraryForCategory.filter { it.media.favorite }
    val watchlist = libraryForCategory.sortedByDescending { it.media.addedAt }
    val visibleItems = remember(state) { state.sorted(state.filtered(state.allItems)) }
    val searchResults = remember(state) { state.sorted(state.filtered(state.searchResults)) }
    val statuses = state.feed?.statuses.orEmpty()
    val issues = statuses.filter { !it.available || it.message != null }

    val phase = when {
        state.loading && state.feed == null -> CategoryPhase.LOADING
        state.feed?.sources?.isEmpty() == true && libraryForCategory.isEmpty() ->
            CategoryPhase.NO_SOURCES
        state.error != null && state.allItems.isEmpty() -> CategoryPhase.ERROR
        else -> CategoryPhase.SECTIONS
    }

    val searchVisible = searchOpen || state.query.isNotEmpty()
    val gridState = rememberLazyGridState()
    val collapsed by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 56
        }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> navState.onScroll(index, offset) }
    }
    // Infinite scroll replaces the old "Load more" button. The view model de-duplicates
    // and refuses to run two pages at once, so a fast fling cannot double-fetch.
    LaunchedEffect(gridState, phase) {
        if (phase != CategoryPhase.SECTIONS) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 5) vm.loadMore()
        }
    }
    LaunchedEffect(searchVisible) {
        if (searchVisible) navState.hide() else navState.show()
    }

    BackHandler(enabled = searchVisible) {
        if (state.query.isNotEmpty()) vm.clearSearch() else searchOpen = false
    }

    SectionScopeWorld(section = section) {
        Box(Modifier.fillMaxSize()) {
            CategoryGrid(
                category = category,
                state = state,
                phase = phase,
                visibleItems = visibleItems,
                favorites = favorites,
                watchlist = watchlist,
                downloadedMedia = downloadedMedia,
                issues = issues,
                gridState = gridState,
                statusTop = statusTop,
                onOpen = vm::open,
                onOpenLibraryMedia = onOpenMedia,
                onOpenLibrary = onOpenLibrary,
                onOpenExtensions = onOpenExtensions,
                onRetry = vm::refresh,
                onClearFilters = vm::clearFilters,
                onOpenSort = { sortOpen = true },
                onOpenFilters = vm::openFilters,
                onToggleStatuses = { statusExpanded = !statusExpanded },
                statusExpanded = statusExpanded,
                onSearch = { searchOpen = true },
                onMore = { moreOpen = true }
            )

            TopScrim(Modifier.align(Alignment.TopCenter))
            BottomScrim(Modifier.align(Alignment.BottomCenter))

            CategoryTopCapsule(
                category = category,
                state = state,
                collapsed = collapsed,
                onSearch = { searchOpen = true },
                onOpenFilters = vm::openFilters,
                onRefresh = vm::refresh,
                onMore = { moreOpen = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            GlassToastHost(
                state = toast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 108.dp)
            )
        }
    }

    CategorySearchOverlay(
        visible = searchVisible,
        category = category,
        state = state,
        items = searchResults,
        downloadedMedia = downloadedMedia,
        searchFocus = searchFocus,
        onQuery = vm::setQuery,
        onOpen = vm::open,
        onRetry = { vm.setQuery(state.query) },
        onManageExtensions = onOpenExtensions,
        onOpenGlobalSearch = {
            searchOpen = false
            vm.clearSearch()
            onOpenGlobalSearch()
        },
        onClose = {
            searchOpen = false
            vm.clearSearch()
        }
    )

    if (sortOpen) {
        GlassSheet(onDismiss = { sortOpen = false }) {
            AFText("Sort by", style = AFTheme.type.title, color = AFTheme.colors.textHi)
            Spacer(Modifier.height(10.dp))
            CategorySort.entries.forEach { sort ->
                SheetRow(
                    title = sort.label,
                    subtitle = when (sort) {
                        CategorySort.POPULAR -> "What your sources surface first"
                        CategorySort.LATEST -> "Newest releases first"
                        CategorySort.TITLE_ASC -> "Alphabetical across every source"
                        CategorySort.SOURCE -> "Grouped by the source that returned it"
                    },
                    selected = state.sort == sort,
                    onClick = {
                        vm.selectSort(sort)
                        sortOpen = false
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    if (moreOpen) {
        GlassSheet(onDismiss = { moreOpen = false }) {
            AFText("More", style = AFTheme.type.title, color = AFTheme.colors.textHi)
            Spacer(Modifier.height(10.dp))
            SheetRow(
                title = "Add a source",
                subtitle = "Addon, Jellyfin, manga extension or novel plugin",
                leading = { AFIcon(AFIcons.Plus, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = {
                    moreOpen = false
                    addOpen = true
                }
            )
            SheetRow(
                title = "Extensions",
                subtitle = "Installed sources, repositories and updates",
                leading = { AFIcon(AFIcons.Stack, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = {
                    moreOpen = false
                    onOpenExtensions()
                }
            )
            SheetRow(
                title = "Search every category",
                subtitle = "One query across movies, books and manga",
                leading = { AFIcon(AFIcons.Search, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = {
                    moreOpen = false
                    onOpenGlobalSearch()
                }
            )
            SheetRow(
                title = "Library",
                subtitle = "Saved titles, favorites and reading progress",
                leading = { AFIcon(AFIcons.Bookmark, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = {
                    moreOpen = false
                    onOpenLibrary()
                }
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    if (addOpen) {
        GlassQuickAdd(vm = quickAdd, onDismiss = { addOpen = false })
    }

    if (state.filtersOpen) {
        CategoryFilterSheet(
            category = category,
            state = state,
            onSource = vm::selectSource,
            onFormat = vm::selectFormat,
            onGenre = vm::selectGenre,
            onYear = vm::selectYear,
            onAuthor = vm::selectAuthor,
            onSort = vm::selectSort,
            onClear = vm::clearFilters,
            onDone = vm::closeFilters
        )
    }
}

@Composable
private fun SectionScopeWorld(section: Section, content: @Composable () -> Unit) {
    WorldBackdrop(section = section, content = { content() })
}

private fun ContentCategory.section(): Section = when (this) {    ContentCategory.MOVIES -> Section.MOVIES
    ContentCategory.BOOKS -> Section.BOOKS
    ContentCategory.MANGA -> Section.MANGA
}

private enum class CategoryPhase { LOADING, NO_SOURCES, ERROR, SECTIONS }

@Composable
private fun CategoryTopCapsule(
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    collapsed: Boolean,
    onSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalReduceMotion.current
    GlassTopCapsule(modifier = modifier) {
        AnimatedContent(
            targetState = collapsed,
            transitionSpec = {
                fadeIn(tween(if (reduceMotion) 0 else 160)) togetherWith
                    fadeOut(tween(if (reduceMotion) 0 else 120))
            },
            label = "capsuleTitle",
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) { isCollapsed ->
            if (isCollapsed) {
                AFText(
                    text = category.shortLabel,
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(AFTheme.colors.accent, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    AFText(
                        text = "BROWSE",
                        style = AFTheme.type.overline,
                        color = AFTheme.colors.accent,
                        maxLines = 1
                    )
                }
            }
        }
        AFIconButton(AFIcons.Search, "Search ${category.label}", onSearch)
        Box {
            AFIconButton(
                icon = AFIcons.Filter,
                contentDescription = "Filter ${category.label}",
                onClick = onOpenFilters,
                active = state.hasActiveFilters
            )
            if (state.hasActiveFilters) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 9.dp, end = 9.dp)
                        .size(6.dp)
                        .background(AFTheme.colors.accent, CircleShape)
                )
            }
        }
        if (state.refreshing) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                AFSpinner(size = 18.dp)
            }
        } else {
            AFIconButton(AFIcons.Refresh, "Refresh category", onRefresh)
        }
        AFIconButton(AFIcons.More, "More actions", onMore)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    phase: CategoryPhase,
    visibleItems: List<ContentItem>,
    favorites: List<LibraryRow>,
    watchlist: List<LibraryRow>,
    downloadedMedia: Set<String>,
    issues: List<ProviderStatus>,
    gridState: LazyGridState,
    statusTop: androidx.compose.ui.unit.Dp,
    onOpen: (ContentItem) -> Unit,
    onOpenLibraryMedia: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenExtensions: () -> Unit,
    onRetry: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenFilters: () -> Unit,
    onToggleStatuses: () -> Unit,
    statusExpanded: Boolean,
    onSearch: () -> Unit,
    onMore: () -> Unit
) {
    val libraryRows = if (category.isVideo) watchlist else favorites
    val sourceCount = state.feed?.sources.orEmpty().size

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
        state = gridState,
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = statusTop + 78.dp,
            bottom = NAV_RESERVE
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Column(Modifier.padding(bottom = 4.dp)) {
                AFOverline("${category.shortLabel} Â· Browse")
                Spacer(Modifier.height(8.dp))
                ScreenHeader(
                    title = category.label,
                    subtitle = when {
                        sourceCount == 0 -> "No source connected"
                        phase == CategoryPhase.LOADING -> "Reading your sourcesâ€¦"
                        else -> "${visibleItems.size} titles Â· ${sourceCount} " +
                            (if (sourceCount == 1) "source" else "sources") +
                            if (state.hasActiveFilters) " Â· filtered" else ""
                    }
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SortCapsule(sort = state.sort, onClick = onOpenSort)
                    GlassChip(
                        label = "Filters",
                        selected = state.hasActiveFilters,
                        badge = if (state.hasActiveFilters) filterCount(state) else null,
                        icon = AFIcons.Filter,
                        onClick = onOpenFilters
                    )
                    if (state.hasActiveFilters) {
                        AFTextButton("Clear", onClick = onClearFilters, color = AFTheme.colors.textLo)
                    }
                }
                if (state.hasActiveFilters) {
                    Spacer(Modifier.height(6.dp))
                    AFText(
                        text = filterSummary(state),
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.accent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        when (phase) {
            CategoryPhase.LOADING -> item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                Column {
                    SkeletonPosterGrid(columns = 3, rows = 3)
                    Spacer(Modifier.height(24.dp))
                    AFText(
                        text = "Reading ${category.shortLabel.lowercase()} from your sources",
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            CategoryPhase.NO_SOURCES -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                GlassEmpty(
                    title = "No ${category.shortLabel.lowercase()} source yet",
                    body = "Install a source or add-on and its titles appear here automatically. " +
                        "Sources never become separate tabs.",
                    actionLabel = "Manage extensions",
                    onAction = onOpenExtensions
                )
            }

            CategoryPhase.ERROR -> item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                GlassEmpty(
                    title = "Your sources could not be read",
                    body = state.error?.message.orEmpty(),
                    actionLabel = "Try again",
                    onAction = onRetry
                )
            }

            CategoryPhase.SECTIONS -> {
                if (state.feed?.sources.isNullOrEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "notice") {
                        GlassPanel(header = "Sources") {
                            AFText(
                                text = "No ${category.shortLabel.lowercase()} source is connected. " +
                                    "Your saved titles stay available below.",
                                style = AFTheme.type.bodySmall,
                                color = AFTheme.colors.textMid
                            )
                            AFTextButton("Manage extensions", onOpenExtensions)
                        }
                    }
                }

                if (libraryRows.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "library-header") {
                        val data = if (category.isVideo) watchlist else favorites
                        SectionRow(
                            title = if (category.isVideo) "Watchlist" else "Favorites",
                            count = data.size,
                            action = "Library",
                            onAction = onOpenLibrary
                        )
                    }
                    items(
                        libraryRows.take(LIBRARY_PREVIEW),
                        key = { "library:" + it.media.id }
                    ) { row ->
                        PosterCard(
                            title = row.media.title,
                            imageUrl = row.media.coverUrl,
                            subtitle = listOfNotNull(
                                row.media.year?.toString(),
                                row.media.author
                            ).firstOrNull(),
                            progress = row.fraction,
                            onClick = { onOpenLibraryMedia(row.media.id) }
                        )
                    }
                }

                if (visibleItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty-grid") {
                        GlassEmpty(
                            title = if (state.allItems.isEmpty()) {
                                "Nothing to show yet"
                            } else {
                                "No titles match these filters"
                            },
                            body = if (state.allItems.isEmpty()) {
                                if (issues.any { !it.available }) {
                                    "Your sources could not be reached. The status below shows " +
                                        "which one failed; the rest of the app keeps working."
                                } else {
                                    "Your sources returned no titles for this category. " +
                                        "Try refreshing or adding another source."
                                }
                            } else {
                                "Clear a filter or refresh to see the provider results again."
                            },
                            actionLabel = if (state.allItems.isEmpty()) "Refresh" else "Clear filters",
                            onAction = if (state.allItems.isEmpty()) onRetry else onClearFilters
                        )
                    }
                } else {
                    itemsIndexed(
                        visibleItems,
                        key = { _, item -> item.primary.sourceId + "|" + item.primary.key }
                    ) { index, item ->
                        val details = state.detailsFor(item)
                        val format = item.format(details)
                        val downloaded = refId(item.primary) in downloadedMedia
                        PosterCard(
                            title = item.title,
                            imageUrl = item.stub.coverUrl,
                            subtitle = listOfNotNull(
                                format,
                                item.sourceLabel.takeIf { format == null },
                                "+${item.alternateCount}".takeIf { item.alternateCount > 0 }
                            ).joinToString(" Â· ").ifBlank { null },
                            badges = buildList {
                                if (downloaded) {
                                    add(
                                        PosterBadge(
                                            icon = AFIcons.Download,
                                            contentDescription = "Available offline",
                                            tone = AFTheme.colors.ok
                                        )
                                    )
                                }
                            },
                            onClick = { onOpen(item) }
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                    Box(Modifier.fillMaxWidth()) {
                        when {
                            state.loadingMore -> Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AFSpinner(size = 22.dp)
                            }
                            !state.canLoadMore && visibleItems.isNotEmpty() -> EndOfList(
                                "Everything your sources returned"
                            )
                        }
                    }
                }

                if (issues.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "issues") {
                        SourceIssuesPanel(
                            issues = issues,
                            expanded = statusExpanded,
                            onToggle = onToggleStatuses,
                            onRetry = onRetry,
                            onManageExtensions = onOpenExtensions
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortCapsule(sort: CategorySort, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.height(36.dp),
        tier = GlassTier.CLEAR,
        shape = CircleShape,
        onClick = onClick,
        onClickLabel = "Sort by ${sort.label}"
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            AFIcon(AFIcons.Sort, null, tint = AFTheme.colors.textLo, size = 15.dp)
            AFText(
                text = sort.label,
                style = AFTheme.type.label,
                color = AFTheme.colors.textHi,
                maxLines = 1
            )
            AFIcon(AFIcons.ChevronDown, null, tint = AFTheme.colors.textLo, size = 14.dp)
        }
    }
}

@Composable
private fun SectionRow(
    title: String,
    count: Int,
    action: String,
    onAction: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AFText(
            text = title,
            style = AFTheme.type.heading,
            color = AFTheme.colors.textHi,
            modifier = Modifier.weight(1f)
        )
        AFText(
            text = count.toString(),
            style = AFTheme.type.numeric,
            color = AFTheme.colors.textLo
        )
        Spacer(Modifier.width(8.dp))
        AFTextButton(action, onAction)
    }
}

/**
 * Search stays inside the category so the grid behind keeps its scroll position. The
 * panel is one frosted sheet over the world; results are the same posters as the grid.
 */
@Composable
private fun CategorySearchOverlay(
    visible: Boolean,
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    items: List<ContentItem>,
    downloadedMedia: Set<String>,
    searchFocus: FocusRequester,
    onQuery: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onRetry: () -> Unit,
    onManageExtensions: () -> Unit,
    onOpenGlobalSearch: () -> Unit,
    onClose: () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(if (reduceMotion) 0 else 180)) +
            slideInVertically(tween(if (reduceMotion) 0 else 240)) { -it / 12 },
        exit = fadeOut(tween(if (reduceMotion) 0 else 140)) +
            slideOutVertically(tween(if (reduceMotion) 0 else 200)) { -it / 12 }
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            tier = GlassTier.FROSTED,
            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AFIconButton(AFIcons.Back, "Close search", onClose)
                    GlassTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        placeholder = "Search ${category.label.lowercase()}",
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
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocus),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                    )
                }

                if (state.query.isBlank()) {
                    SearchHint(category, onOpenGlobalSearch)
                } else {
                    SearchResults(
                        state = state,
                        category = category,
                        items = items,
                        downloadedMedia = downloadedMedia,
                        onOpen = onOpen,
                        onRetry = onRetry,
                        onClear = { onQuery("") },
                        onManageExtensions = onManageExtensions
                    )
                }
            }
        }
    }

    LaunchedEffect(visible) {
        if (visible) searchFocus.requestFocus()
    }
}

@Composable
private fun SearchHint(category: ContentCategory, onOpenGlobalSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AFOverline("Search")
        Spacer(Modifier.height(10.dp))
        AFText(
            text = "Search ${category.shortLabel.lowercase()}",
            style = AFTheme.type.displaySmall,
            color = AFTheme.colors.textHi,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        AFText(
            text = "Every installed source is asked at once. Results appear here without " +
                "leaving the category.",
            style = AFTheme.type.body,
            color = AFTheme.colors.textLo,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        AFTextButton("Search every category instead", onOpenGlobalSearch)
    }
}

@Composable
private fun SearchResults(
    state: CategoryViewModel.UiState,
    category: ContentCategory,
    items: List<ContentItem>,
    downloadedMedia: Set<String>,
    onOpen: (ContentItem) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onManageExtensions: () -> Unit
) {
    when {
        state.searching -> SkeletonPosterGrid(
            columns = 3,
            rows = 3,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        state.searchError != null -> GlassEmpty(
            title = "Search could not finish",
            body = state.searchError.message,
            actionLabel = "Try again",
            onAction = onRetry
        )

        items.isEmpty() -> GlassEmpty(
            title = "No results",
            body = "No installed ${category.shortLabel.lowercase()} source returned " +
                "\"${state.query}\". Nothing is invented.",
            actionLabel = "Clear search",
            onAction = onClear
        )

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchSummary(
                    count = items.size,
                    query = state.query,
                    statuses = state.searchStatuses,
                    onManageExtensions = onManageExtensions
                )
            }
            items(
                items,
                key = { it.primary.sourceId + "|" + it.primary.key }
            ) { item ->
                val format = item.format(state.detailsFor(item))
                PosterCard(
                    title = item.title,
                    imageUrl = item.stub.coverUrl,
                    subtitle = listOfNotNull(
                        format,
                        item.sourceLabel.takeIf { format == null }
                    ).joinToString(" Â· ").ifBlank { null },
                    progress = null,
                    badges = if (refId(item.primary) in downloadedMedia) {
                        listOf(
                            PosterBadge(
                                icon = AFIcons.Download,
                                contentDescription = "Available offline",
                                tone = AFTheme.colors.ok
                            )
                        )
                    } else {
                        emptyList()
                    },
                    onClick = { onOpen(item) }
                )
            }
        }
    }
}

@Composable
private fun SearchSummary(
    count: Int,
    query: String,
    statuses: List<ProviderStatus>,
    onManageExtensions: () -> Unit
) {
    var showIssues by remember { mutableStateOf(false) }
    val failed = statuses.filter { !it.available }
    Column(Modifier.padding(bottom = 4.dp)) {
        AFText(
            text = "$count ${if (count == 1) "result" else "results"} for \"$query\"",
            style = AFTheme.type.label,
            color = AFTheme.colors.textHi,
            maxLines = 2
        )
        if (failed.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AFText(
                    text = "${failed.size} source" +
                        (if (failed.size == 1) "" else "s") + " could not be searched",
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.failed,
                    modifier = Modifier.weight(1f)
                )
                AFTextButton(
                    label = if (showIssues) "Hide" else "Details",
                    onClick = { showIssues = !showIssues }
                )
            }
            if (showIssues) {
                failed.forEach { StatusRow(it) }
                AFTextButton("Manage extensions", onManageExtensions)
            }
        }
    }
}

@Composable
private fun SourceIssuesPanel(
    issues: List<ProviderStatus>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onManageExtensions: () -> Unit
) {
    GlassPanel(header = "Source status") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AFText(
                text = "${issues.size} source" + (if (issues.size == 1) "" else "s") +
                    " reported an issue",
                style = AFTheme.type.bodySmall,
                color = AFTheme.colors.textMid,
                modifier = Modifier.weight(1f)
            )
            AFTextButton(if (expanded) "Hide" else "Details", onToggle)
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                issues.forEach { StatusRow(it) }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AFTextButton("Retry", onRetry)
                    AFTextButton("Manage extensions", onManageExtensions)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(status: ProviderStatus) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(
                    if (status.available) AFTheme.colors.ok else AFTheme.colors.failed,
                    CircleShape
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            AFText(
                text = status.sourceName,
                style = AFTheme.type.label,
                color = AFTheme.colors.textHi,
                maxLines = 1
            )
            AFText(
                text = buildString {
                    append(status.state.label)
                    status.message?.takeIf { it.isNotBlank() }?.let {
                        append(" â€” ")
                        append(it)
                    }
                },
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilterSheet(
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    onSource: (String?) -> Unit,
    onFormat: (String?) -> Unit,
    onGenre: (String?) -> Unit,
    onYear: (Int?) -> Unit,
    onAuthor: (String?) -> Unit,
    onSort: (CategorySort) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    GlassSheet(onDismiss = onDone) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AFText(
                    text = "Filters",
                    style = AFTheme.type.title,
                    color = AFTheme.colors.textHi,
                    modifier = Modifier.weight(1f)
                )
                AFTextButton("Clear all", onClear, color = AFTheme.colors.failed)
            }
            AFText(
                text = "Facets come from metadata your sources actually returned.",
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo
            )
            if (state.enriching) {
                Spacer(Modifier.height(10.dp))
                GlassProgress(progress = 0.35f, height = 3.dp)
                Spacer(Modifier.height(6.dp))
                AFText(
                    text = "Reading metadata from your sourcesâ€¦",
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textLo
                )
            }

            FilterGroup("Sort by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategorySort.entries.forEach { sort ->
                    GlassChip(
                        label = sort.label,
                        selected = state.sort == sort,
                        onClick = { onSort(sort) }
                    )
                }
            }

            if (state.formatOptions.isNotEmpty()) {
                FilterGroup("Format")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(
                        label = "All",
                        selected = state.formatFilter == null,
                        onClick = { onFormat(null) }
                    )
                    state.formatOptions.forEach { option ->
                        GlassChip(
                            label = option,
                            selected = state.formatFilter == option,
                            onClick = {
                                onFormat(if (state.formatFilter == option) null else option)
                            }
                        )
                    }
                }
            }

            if (state.feed?.sources.orEmpty().size > 1) {
                FilterGroup("Source")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(
                        label = "All",
                        selected = state.sourceFilter == null,
                        onClick = { onSource(null) }
                    )
                    state.feed?.sources.orEmpty().forEach { source ->
                        GlassChip(
                            label = source.name,
                            selected = state.sourceFilter == source.id,
                            onClick = {
                                onSource(if (state.sourceFilter == source.id) null else source.id)
                            }
                        )
                    }
                }
            }

            if (state.genreOptions.isNotEmpty()) {
                FilterGroup("Genres")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.genreOptions.forEach { genre ->
                        GlassChip(
                            label = genre,
                            selected = state.genreFilter == genre,
                            onClick = {
                                onGenre(if (state.genreFilter == genre) null else genre)
                            }
                        )
                    }
                }
            }

            if (category == ContentCategory.MOVIES && state.yearOptions.isNotEmpty()) {
                FilterGroup("Release year")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.yearOptions.forEach { year ->
                        GlassChip(
                            label = year.toString(),
                            selected = state.yearFilter == year,
                            onClick = { onYear(if (state.yearFilter == year) null else year) }
                        )
                    }
                }
            }

            if (category == ContentCategory.BOOKS && state.authorOptions.isNotEmpty()) {
                FilterGroup("Authors")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.authorOptions.take(60).forEach { author ->
                        GlassChip(
                            label = author,
                            selected = state.authorFilter == author,
                            onClick = {
                                onAuthor(if (state.authorFilter == author) null else author)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            GlassPrimaryAction(
                label = "Show results",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FilterGroup(title: String) {
    AFOverline(
        text = title,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

private fun filterCount(state: CategoryViewModel.UiState): Int = listOfNotNull(
    state.formatFilter,
    state.genreFilter,
    state.yearFilter,
    state.authorFilter,
    state.sourceFilter
).size

private fun filterSummary(state: CategoryViewModel.UiState): String = buildList {
    state.formatFilter?.let { add(it) }
    state.genreFilter?.let { add(it) }
    state.yearFilter?.let { add(it.toString()) }
    state.authorFilter?.let { add("by $it") }
    state.sourceFilter?.let { id ->
        state.feed?.sources?.firstOrNull { it.id == id }?.name?.let { add(it) }
    }
}.joinToString(" Â· ")
