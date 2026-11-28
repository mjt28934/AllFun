package app.allfun.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.ContentItem
import app.allfun.data.content.ProviderStatus
import app.allfun.data.content.category
import app.allfun.data.content.refId
import app.allfun.data.db.LibraryRow
import app.allfun.ui.components.AllFunButton
import app.allfun.ui.components.AllFunButtonVariant
import app.allfun.ui.components.AllFunChip
import app.allfun.ui.components.AllFunSheet
import app.allfun.ui.components.AllFunTabs
import app.allfun.ui.components.AppTopBar
import app.allfun.ui.components.ContentCard
import app.allfun.ui.components.EmptyIllustration
import app.allfun.ui.components.EmptyIllustrationArt
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.ErrorState
import app.allfun.ui.components.GridSkeleton
import app.allfun.ui.components.LoadingState
import app.allfun.ui.components.MediaCard
import app.allfun.ui.components.NoticeCard
import app.allfun.ui.components.Overline
import app.allfun.ui.components.ProviderStatusLine
import app.allfun.ui.components.QuickAddButton
import app.allfun.ui.components.SearchField
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.StatusPill
import app.allfun.ui.components.TopBarAction
import app.allfun.ui.components.accent
import app.allfun.ui.components.icon
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.components.staggeredReveal
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.AllFunType
import app.allfun.ui.theme.LocalReduceMotion
import app.allfun.ui.theme.Motion

/** Favorites/watchlist preview rows; the Library owns the full list. */
private const val LIBRARY_PREVIEW = 6

/**
 * Narrowest poster the grids accept. Browse, search results and their loading skeletons
 * all use it so the column count never changes between those three states.
 */
private val GRID_MIN_CELL = 108.dp

/**
 * The single category browse experience. Movies, books and manga share the same cards,
 * search field, filters, sorting, loading/error handling and source status reporting; the
 * only per-category differences are metadata labels and accent colour.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val snackbar = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }
    var statusExpanded by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
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

    val libraryForCategory = libraryRows.filter { it.media.kind.category() == category }
    val favorites = libraryForCategory.filter { it.media.favorite }
    val watchlist = libraryForCategory.sortedByDescending { it.media.addedAt }
    // Sorting and filtering thousands of accumulated cards on every frame is what made
    // scrolling after several "Load more" taps stutter. Keep the result until the state
    // (feed, filters, sort, page) actually changes.
    val visibleItems = remember(state) {
        state.sorted(state.filtered(state.allItems))
    }
    val searchResults = remember(state) {
        state.sorted(state.filtered(state.searchResults))
    }
    val statuses = state.feed?.statuses.orEmpty()
    val issues = statuses.filter { !it.available || it.message != null }

    val phase = when {
        state.loading && state.feed == null -> CategoryPhase.LOADING
        state.feed?.sources?.isEmpty() == true &&
            libraryForCategory.isEmpty() -> CategoryPhase.NO_SOURCES
        state.error != null && state.allItems.isEmpty() -> CategoryPhase.ERROR
        else -> CategoryPhase.SECTIONS
    }

    val searchVisible = searchOpen || state.query.isNotEmpty()
    val reduceMotion = LocalReduceMotion.current
    val searchBlur by animateDpAsState(
        targetValue = if (searchVisible) 18.dp else 0.dp,
        animationSpec = tween(if (reduceMotion) 0 else Motion.MEDIUM),
        label = "searchBlur"
    )
    // The hero lives inside the grid so it scrolls with the titles instead of eating a
    // fixed band of every screen. The top bar watches the grid to swap its small label for
    // the category name once the hero has left.
    val gridState = rememberLazyGridState()
    val heroCollapsed by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 88
        }
    }

    BackHandler(enabled = searchVisible) {
        if (state.query.isNotEmpty()) vm.clearSearch() else searchOpen = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(searchBlur)
            ) {
            CategoryTopBar(
                container = container,
                snackbar = snackbar,
                category = category,
                state = state,
                collapsed = heroCollapsed,
                onOpenSearch = {
                    searchOpen = true
                },
                onOpenFilters = vm::openFilters
            )

            if (phase == CategoryPhase.SECTIONS) {
                CategorySortBar(
                    sort = state.sort,
                    onSort = vm::selectSort
                )
            }

            AnimatedVisibility(visible = state.hasActiveFilters) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filterSummary(state),
                        style = MaterialTheme.typography.labelSmall,
                        color = category.accent(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    AllFunButton(
                        text = "Clear",
                        onClick = vm::clearFilters,
                        variant = AllFunButtonVariant.TEXT
                    )
                }
            }

            AnimatedVisibility(visible = state.refreshing) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(CircleShape)
                )
            }

            AnimatedPhase(
                phase = phase,
                modifier = Modifier.weight(1f)
            ) {
                when (it) {
                    CategoryPhase.LOADING -> LoadingGrid(category)

                    CategoryPhase.NO_SOURCES -> EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = when (category) {
                            ContentCategory.BOOKS -> "No book sources"
                            ContentCategory.MANGA -> "No manga sources"
                            ContentCategory.MOVIES -> "No video sources"
                        },
                        message = when (category) {
                            ContentCategory.BOOKS -> "Install a novel plugin to browse and read here."
                            ContentCategory.MANGA -> "Install a manga extension to browse and read here."
                            ContentCategory.MOVIES -> "Add a Stremio addon or Jellyfin server to browse here."
                        },
                        actionLabel = "Add a source",
                        onAction = onOpenExtensions,
                        illustration = EmptyIllustration.PLUG
                    )

                    CategoryPhase.ERROR -> ErrorState(
                        error = state.error!!,
                        onRetry = vm::refresh,
                        secondary = "Manage extensions" to onOpenExtensions
                    )

                    CategoryPhase.SECTIONS -> CategorySections(
                        category = category,
                        state = state,
                        favorites = favorites,
                        watchlist = watchlist,
                        visibleItems = visibleItems,
                        downloadedMedia = downloadedMedia,
                        unmatched = issues,
                        statusExpanded = statusExpanded,
                        onToggleStatuses = { statusExpanded = !statusExpanded },
                        onOpenLibraryMedia = onOpenMedia,
                        onOpenLibrary = onOpenLibrary,
                        onOpen = vm::open,
                        onLoadMore = vm::loadMore,
                        onClearFilters = vm::clearFilters,
                        onRetry = vm::refresh,
                        onManageExtensions = onOpenExtensions,
                        gridState = gridState
                    )
                }
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
        }
    }

    if (state.filtersOpen) {
        AllFunSheet(title = "Sort & filter", onDismiss = vm::closeFilters) {
            FilterSheet(
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
}

private enum class CategoryPhase { LOADING, NO_SOURCES, ERROR, SECTIONS }

/**
 * Fade/slide between the category's phases. When motion is reduced the content simply
 * swaps, so "remove animations" really removes them.
 */
@Composable
private fun AnimatedPhase(
    phase: CategoryPhase,
    modifier: Modifier = Modifier,
    content: @Composable (CategoryPhase) -> Unit
) {
    if (LocalReduceMotion.current) {
        Box(modifier) { content(phase) }
        return
    }
    AnimatedContent(
        targetState = phase,
        modifier = modifier,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(Motion.MEDIUM, easing = Motion.EaseOut)) +
                    slideInVertically(tween(Motion.MEDIUM, easing = Motion.EaseOut)) { it / 28 },
                initialContentExit = fadeOut(tween(Motion.FAST)) +
                    slideOutVertically(tween(Motion.FAST)) { -it / 40 }
            )
        },
        label = "categoryPhase"
    ) { target ->
        Box(Modifier.fillMaxSize()) { content(target) }
    }
}

/**
 * Search as a floating section rather than a route: the category keeps running behind a
 * blurred, dimmed backdrop while the field and results animate in over it. Dismissing it
 * returns the reader to the same scroll position instead of rebuilding the screen.
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
    val focus = LocalFocusManager.current
    val enter = if (reduceMotion) {
        fadeIn(tween(0))
    } else {
        fadeIn(tween(Motion.MEDIUM, easing = Motion.EaseOut)) +
            slideInVertically(tween(Motion.SLOW, easing = Motion.EaseOut)) { -it / 9 } +
            scaleIn(tween(Motion.SLOW, easing = Motion.EaseOut), initialScale = 0.96f)
    }
    val exit = fadeOut(tween(if (reduceMotion) 0 else Motion.FAST)) +
        slideOutVertically(tween(if (reduceMotion) 0 else Motion.MEDIUM)) { -it / 14 }

    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AllFun.colors.surface1.copy(alpha = 0.82f))
                .navigationBarsPadding()
        ) {
            SearchField(
                value = state.query,
                onValueChange = onQuery,
                placeholder = "Search ${category.label.lowercase()}",
                onBack = onClose,
                focusRequester = searchFocus,
                onSearch = { focus.clearFocus() },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (state.query.isBlank()) {
                SearchHint(category, onOpenGlobalSearch)
            } else {
                SearchGrid(
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

    LaunchedEffect(visible) {
        if (visible) searchFocus.requestFocus()
    }
}

@Composable
private fun SearchHint(category: ContentCategory, onOpenGlobalSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 64.dp),
        horizontalAlignment = Alignment.Start
    ) {
        EmptyIllustrationArt(
            kind = EmptyIllustration.LOUPE,
            modifier = Modifier.size(width = 120.dp, height = 80.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Search ${category.label.lowercase()}",
            style = MaterialTheme.typography.headlineSmall,
            color = AllFun.colors.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Every installed source is asked at once. Results appear here without " +
                "leaving the category.",
            style = MaterialTheme.typography.bodyLarge,
            color = AllFun.colors.textSecondary,
            modifier = Modifier.fillMaxWidth(0.85f)
        )
        Spacer(Modifier.height(12.dp))
        AllFunButton(
            text = "Search every category",
            onClick = onOpenGlobalSearch,
            variant = AllFunButtonVariant.TEXT
        )
    }
}

/**
 * A segmented control rather than loose chips: the four orders are one object, the
 * selection travels between them, and every label stays visible without scrolling.
 */
@Composable
private fun CategorySortBar(
    sort: CategorySort,
    onSort: (CategorySort) -> Unit
) {
    val options = CategorySort.entries
    AllFunTabs(
        labels = options.map { it.shortLabel },
        selectedIndex = options.indexOf(sort).coerceAtLeast(0),
        onSelect = { index -> onSort(options[index]) },
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

/**
 * The fixed strip above the grid. It holds the actions and a label that changes once the
 * hero scrolls away, so the category name is always somewhere on screen without a second
 * permanent title band.
 */
@Composable
private fun CategoryTopBar(
    container: AppContainer,
    snackbar: SnackbarHostState,
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    collapsed: Boolean,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit
) {
    AppTopBar(
        title = category.shortLabel,
        meta = buildString {
            val count = state.feed?.sources?.size ?: 0
            append(count)
            append(if (count == 1) " source" else " sources")
        },
        collapsed = collapsed,
        actions = {
            TopBarAction(
                icon = Icons.Outlined.Search,
                contentDescription = "Search ${category.label}",
                onClick = onOpenSearch
            )
            TopBarAction(
                icon = Icons.Outlined.Tune,
                contentDescription = "Filter ${category.label}",
                onClick = onOpenFilters,
                tint = if (state.hasActiveFilters) AllFun.colors.accent
                else AllFun.colors.textSecondary
            )
            QuickAddButton(container = container, snackbar = snackbar)
        }
    )
}

/** The refresh arrow turns while a request is actually running, then settles upright. */
@Composable
private fun RefreshAction(refreshing: Boolean, onClick: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(refreshing, reduceMotion) {
        if (refreshing && !reduceMotion) {
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(900, easing = androidx.compose.animation.core.LinearEasing)
                )
            )
        } else {
            rotation.snapTo(0f)
        }
    }
    IconButton(
        onClick = onClick,
        enabled = !refreshing,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = "Refresh category",
            modifier = Modifier
                .size(21.dp)
                .graphicsLayer { rotationZ = rotation.value }
        )
    }
}

/**
 * The category's own heading: a serif title, one honest count line and two text actions.
 * It lives inside the grid, so it leaves the screen as the reader scrolls.
 */
@Composable
private fun CategoryHero(
    category: ContentCategory,
    titleCount: Int,
    sourceCount: Int,
    issueCount: Int,
    filtered: Boolean,
    onRefresh: () -> Unit,
    onManageExtensions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.displaySmall,
            color = AllFun.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = category.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = AllFun.colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildList {
                add(if (titleCount == 1) "1 title" else "$titleCount titles")
                add(
                    when (sourceCount) {
                        0 -> "no source"
                        1 -> "1 source"
                        else -> "$sourceCount sources"
                    }
                )
                if (issueCount > 0) {
                    add(if (issueCount == 1) "1 issue" else "$issueCount issues")
                }
                if (filtered) add("filtered")
            }.joinToString(" · "),
            style = AllFunType.mono,
            color = if (issueCount > 0) AllFun.colors.danger else AllFun.colors.textTertiary
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AllFunButton(
                text = "Refresh",
                onClick = onRefresh,
                variant = AllFunButtonVariant.TEXT
            )
            AllFunButton(
                text = "Sources",
                onClick = onManageExtensions,
                variant = AllFunButtonVariant.TEXT
            )
        }
    }
}

/** Skeletons are only shown while a request is genuinely running. */
@Composable
private fun LoadingGrid(category: ContentCategory, searching: Boolean = false) {
    Column(Modifier.fillMaxSize()) {
        LoadingState(
            if (searching) "Searching every ${category.label.lowercase()} source"
            else "Reading ${category.label.lowercase()} from your sources"
        )
        // Same adaptive width as CategorySections, so the placeholders do not jump from
        // two huge columns to the three real ones when results arrive.
        GridSkeleton(count = 9, minCellWidth = GRID_MIN_CELL)
    }
}

/**
 * One vertical grid for the whole category. Everything the providers returned — and the
 * user's own favorites/watchlist — is laid out top to bottom in the order chosen in
 * [CategorySortBar]; there are no sideways carousels left to swipe through.
 */
@Composable
private fun CategorySections(
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    favorites: List<LibraryRow>,
    watchlist: List<LibraryRow>,
    visibleItems: List<ContentItem>,
    downloadedMedia: Set<String>,
    unmatched: List<ProviderStatus>,
    statusExpanded: Boolean,
    onToggleStatuses: () -> Unit,
    onOpenLibraryMedia: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpen: (ContentItem) -> Unit,
    onLoadMore: () -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onManageExtensions: () -> Unit,
    gridState: LazyGridState
) {
    val libraryRows = if (category.isVideo) watchlist else favorites
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
            CategoryHero(
                category = category,
                titleCount = visibleItems.size,
                sourceCount = state.feed?.sources.orEmpty().size,
                issueCount = unmatched.size,
                filtered = state.hasActiveFilters,
                onRefresh = onRetry,
                onManageExtensions = onManageExtensions
            )
        }

        if (state.feed?.sources.isNullOrEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    NoticeCard(
                        text = "No ${category.shortLabel.lowercase()} source is connected. " +
                            "Your saved titles stay available below; connect a source to " +
                            "browse and search the whole category."
                    )
                    AllFunButton(
                        text = "Manage extensions",
                        onClick = onManageExtensions,
                        variant = AllFunButtonVariant.TEXT,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        if (libraryRows.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "library-header") {
                val extra = (libraryRows.size - LIBRARY_PREVIEW).coerceAtLeast(0)
                SectionHeader(
                    title = if (category.isVideo) "Watchlist" else "Favorites",
                    subtitle = if (extra > 0) {
                        "$extra more in your library"
                    } else if (category.isVideo) {
                        "Saved movies and shows"
                    } else {
                        "Pinned across every source"
                    },
                    actionLabel = "Library",
                    onAction = onOpenLibrary
                )
            }
            items(libraryRows.take(LIBRARY_PREVIEW), key = { "library:" + it.media.id }) { row ->
                MediaCard(
                    media = row.media,
                    progress = row.fraction,
                    onClick = { onOpenLibraryMedia(row.media.id) },
                    fillWidth = true,
                    modifier = Modifier
                        .staggeredReveal(0)
                        .animateItem()
                )
            }
        }

        if (visibleItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = if (state.allItems.isEmpty()) {
                        Icons.Outlined.Extension
                    } else {
                        Icons.Outlined.FilterList
                    },
                    title = if (state.allItems.isEmpty()) {
                        "Nothing to show yet"
                    } else {
                        "No titles match these filters"
                    },
                    message = if (state.allItems.isEmpty()) {
                        if (unmatched.any { !it.available }) {
                            "Your sources could not be reached. The statuses below show " +
                                "which one failed; the rest of the app keeps working."
                        } else {
                            "Your sources returned no titles for this category. Try " +
                                "refreshing or adding another source."
                        }
                    } else {
                        "Clear a filter or refresh the category to see the provider " +
                            "results again."
                    },
                    actionLabel = if (state.allItems.isEmpty()) "Refresh" else "Clear filters",
                    onAction = if (state.allItems.isEmpty()) onRetry else onClearFilters,
                    illustration = if (state.allItems.isEmpty()) {
                        EmptyIllustration.PLUG
                    } else {
                        EmptyIllustration.SHELF
                    }
                )
            }
        } else {
            itemsIndexed(
                visibleItems,
                key = { _, item -> item.primary.sourceId + "|" + item.primary.key }
            ) { index, item ->
                ContentCard(
                    item = item,
                    details = state.detailsFor(item),
                    onClick = { onOpen(item) },
                    fillWidth = true,
                    downloadLabel = if (refId(item.primary) in downloadedMedia) "Offline" else null,
                    modifier = Modifier
                        .staggeredReveal(index)
                        .animateItem()
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
            CategoryGridFooter(category, state, onLoadMore)
        }

        if (unmatched.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "issues") {
                ProviderIssueSection(
                    unmatched = unmatched,
                    expanded = statusExpanded,
                    onToggle = onToggleStatuses,
                    onRetry = onRetry,
                    onManageExtensions = onManageExtensions
                )
            }
        }
    }
}

@Composable
private fun CategoryGridFooter(
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    onLoadMore: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        state.loadMoreError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = AllFun.colors.danger
            )
        }
        when {
            state.loadingMore -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            state.canLoadMore -> AllFunButton(
                text = "Load more titles",
                onClick = onLoadMore,
                variant = AllFunButtonVariant.SECONDARY,
                icon = Icons.Outlined.ExpandMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
        Text(
            text = "Titles come from your installed ${category.shortLabel.lowercase()} sources.",
            style = MaterialTheme.typography.labelSmall,
            color = AllFun.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ProviderIssueSection(
    unmatched: List<ProviderStatus>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onManageExtensions: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = AllFun.colors.hairline.copy(alpha = 0.4f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${unmatched.size} source" +
                    (if (unmatched.size == 1) "" else "s") +
                    " reported an issue",
                style = MaterialTheme.typography.labelLarge,
                color = AllFun.colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            AllFunButton(
                text = if (expanded) "Hide" else "Details",
                onClick = onToggle,
                variant = AllFunButtonVariant.TEXT
            )
        }
        if (expanded) {
            Column {
                unmatched.forEach { ProviderStatusLine(it, onRetry) }
                AllFunButton(
                    text = "Manage extensions",
                    onClick = onManageExtensions,
                    variant = AllFunButtonVariant.TEXT,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchGrid(
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
        state.searching -> LoadingGrid(category, searching = true)
        state.searchError != null -> ErrorState(
            error = state.searchError,
            onRetry = onRetry,
            secondary = "Manage extensions" to onManageExtensions
        )
        items.isEmpty() -> EmptyState(
            icon = Icons.Outlined.Search,
            title = "No results",
            message = "No installed ${category.shortLabel.lowercase()} source returned " +
                "\u201C${state.query}\u201D. Search only shows real responses; nothing is invented.",
            actionLabel = "Clear search",
            onAction = onClear,
            illustration = EmptyIllustration.LOUPE
        )
        else -> LazyVerticalGrid(
            // Same minimum as the browse grid, so results keep the category's three
            // columns instead of ballooning into two oversized posters.
            columns = GridCells.Adaptive(GRID_MIN_CELL),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchSummary(
                    count = items.size,
                    query = state.query,
                    statuses = state.searchStatuses
                )
            }
            items(
                items,
                key = { it.primary.sourceId + "|" + it.primary.key }
            ) { item ->
                ContentCard(
                    item = item,
                    details = state.detailsFor(item),
                    onClick = { onOpen(item) },
                    fillWidth = true,
                    downloadLabel = if (refId(item.primary) in downloadedMedia) "Offline" else null
                )
            }
        }
    }
}

@Composable
private fun SearchSummary(
    count: Int,
    query: String,
    statuses: List<ProviderStatus>
) {
    var showIssues by remember { mutableStateOf(false) }
    val failed = statuses.filter { !it.available }
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            text = "$count ${if (count == 1) "result" else "results"} for \"$query\"",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics {
                contentDescription = "$count results for $query"
            }
        )
        if (failed.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${failed.size} source" +
                        (if (failed.size == 1) "" else "s") + " could not be searched",
                    style = MaterialTheme.typography.labelSmall,
                    color = AllFun.colors.danger,
                    modifier = Modifier.weight(1f)
                )
                AllFunButton(
                    text = if (showIssues) "Hide" else "Details",
                    onClick = { showIssues = !showIssues },
                    variant = AllFunButtonVariant.TEXT
                )
            }
            if (showIssues) {
                failed.forEach { ProviderStatusLine(it) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(0.9f)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            AllFunButton(
                text = "Clear all",
                onClick = onClear,
                variant = AllFunButtonVariant.TEXT
            )
        }
        Spacer(Modifier.height(10.dp))
        if (state.enriching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                text = "Reading metadata from your sources…",
                style = MaterialTheme.typography.bodySmall,
                color = AllFun.colors.textSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        FilterGroup("Sort")
        SortSegmentedControl(selected = state.sort, onSelect = onSort)

        if (state.formatOptions.isNotEmpty()) {
            FilterGroup("Format")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AllFunChip(
                    label = "All",
                    selected = state.formatFilter == null,
                    onClick = { onFormat(null) }
                )
                state.formatOptions.forEach { option ->
                    AllFunChip(
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
                AllFunChip(
                    label = "All",
                    selected = state.sourceFilter == null,
                    onClick = { onSource(null) }
                )
                state.feed?.sources.orEmpty().forEach { source ->
                    AllFunChip(
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
                    AllFunChip(
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
                    AllFunChip(
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
                    AllFunChip(
                        label = author,
                        selected = state.authorFilter == author,
                        onClick = {
                            onAuthor(if (state.authorFilter == author) null else author)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Filters depend on what your sources provide.",
            style = MaterialTheme.typography.bodySmall,
            color = AllFun.colors.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        AllFunButton(
            text = "Show ${state.filtered(state.allItems).size} results",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            fullWidth = true
        )
    }
}

@Composable
private fun SortSegmentedControl(
    selected: CategorySort,
    onSelect: (CategorySort) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AllFun.colors.surface2)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CategorySort.entries.forEach { option ->
            val active = selected == option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (active) Modifier.background(AllFun.colors.accentContainer)
                        else Modifier
                    )
                    .clickable(role = Role.Button, onClick = { onSelect(option) })
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) AllFun.colors.accent else AllFun.colors.textSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun FilterGroup(title: String) {
    Overline(
        text = title,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

private fun filterSummary(state: CategoryViewModel.UiState): String = buildList {
    state.formatFilter?.let { add(it) }
    state.genreFilter?.let { add(it) }
    state.yearFilter?.let { add(it.toString()) }
    state.authorFilter?.let { add("by $it") }
    state.sourceFilter?.let { id ->
        state.feed?.sources?.firstOrNull { it.id == id }?.name?.let { add(it) }
    }
}.joinToString(" · ")
