package app.allfun.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import app.allfun.data.content.refId
import app.allfun.data.db.LibraryRow
import app.allfun.ui.components.ContentCard
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.ErrorState
import app.allfun.ui.components.GridSkeleton
import app.allfun.ui.components.LoadingState
import app.allfun.ui.components.MediaCard
import app.allfun.ui.components.NoticeCard
import app.allfun.ui.components.ProviderStatusLine
import app.allfun.ui.components.QuickAddButton
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.StatusPill
import app.allfun.ui.components.accent
import app.allfun.ui.components.icon
import app.allfun.ui.components.rememberViewModel
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
            CategoryHeader(
                container = container,
                snackbar = snackbar,
                category = category,
                state = state,
                sourceCount = state.feed?.sources.orEmpty().size,
                issueCount = issues.size,
                titleCount = visibleItems.size,
                searchOpen = searchVisible,
                onOpenSearch = {
                    searchOpen = true
                },
                onOpenFilters = vm::openFilters,
                onRefresh = vm::refresh,
                onOpenExtensions = onOpenExtensions
            )

            if (phase == CategoryPhase.SECTIONS) {
                CategorySortBar(
                    sort = state.sort,
                    onSort = vm::selectSort
                )
            }

            if (state.hasActiveFilters) {
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
                    TextButton(onClick = vm::clearFilters) { Text("Clear") }
                }
            }

            if (state.refreshing) {
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
                        title = "No ${category.shortLabel.lowercase()} sources yet",
                        message = "Install a source or add-on and its titles will appear here " +
                            "automatically. Sources never become separate tabs.",
                        actionLabel = "Manage extensions",
                        onAction = onOpenExtensions
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
                        onManageExtensions = onOpenExtensions
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
        ModalBottomSheet(onDismissRequest = vm::closeFilters) {
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
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Close search"
                    )
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocus),
                    placeholder = { Text("Search ${category.label.lowercase()}") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQuery("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = category.accent(),
                        unfocusedBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
                )
            }

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
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(category.accent().copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = category.accent(),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Search ${category.label.lowercase()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Every installed source is asked at once. Results appear here without " +
                "leaving the category.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onOpenGlobalSearch) {
            Text("Search every category instead")
        }
    }
}

/**
 * The two orders readers actually ask for (Popular / Latest) plus the two structural ones,
 * one tap away from the grid. Short labels keep all four on one line at phone widths.
 */
@Composable
private fun CategorySortBar(
    sort: CategorySort,
    onSort: (CategorySort) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(CategorySort.entries) { option ->
            FilterChip(
                selected = sort == option,
                onClick = { onSort(option) },
                label = { Text(option.shortLabel) },
                leadingIcon = when (option) {
                    CategorySort.POPULAR -> {
                        {
                            Icon(
                                Icons.AutoMirrored.Outlined.TrendingUp,
                                contentDescription = null
                            )
                        }
                    }
                    CategorySort.LATEST -> {
                        { Icon(Icons.Outlined.Schedule, contentDescription = null) }
                    }
                    else -> null
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryHeader(
    container: AppContainer,
    snackbar: SnackbarHostState,
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    sourceCount: Int,
    issueCount: Int,
    titleCount: Int,
    searchOpen: Boolean,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onRefresh: () -> Unit,
    onOpenExtensions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)
    ) {
        // Title and tagline own the full width; the five catalogue actions share one row
        // below them. Previously search/filter/refresh sat beside the title and
        // extensions/add on the tagline, so the controls were split across two rows.
        Text(
            text = category.label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = category.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 4.dp, top = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenSearch) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "Search ${category.label}",
                    tint = if (searchOpen) category.accent()
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onOpenFilters) {
                Box {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = "Filter ${category.label}",
                        tint = if (state.hasActiveFilters) category.accent()
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (state.hasActiveFilters) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(category.accent())
                        )
                    }
                }
            }
            IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh category")
            }
            IconButton(onClick = onOpenExtensions) {
                Icon(
                    Icons.Outlined.Extension,
                    contentDescription = "Extensions for ${category.label}"
                )
            }
            // Quick add sits beside the Extensions entry: add-ons and plugins are installed
            // from here, while repositories and updates stay on the Extensions screen.
            QuickAddButton(container = container, snackbar = snackbar)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatusPill(
                text = if (titleCount == 1) "1 title" else "$titleCount titles",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusPill(
                text = when (sourceCount) {
                    0 -> "No source"
                    1 -> "1 source"
                    else -> "$sourceCount sources"
                },
                tint = if (sourceCount == 0) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.secondary
            )
            if (issueCount > 0) {
                StatusPill(
                    text = if (issueCount == 1) "1 issue" else "$issueCount issues",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            if (state.hasActiveFilters) {
                StatusPill(text = "Filtered", tint = category.accent())
            }
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
    onManageExtensions: () -> Unit
) {
    val libraryRows = if (category.isVideo) watchlist else favorites
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.feed?.sources.isNullOrEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    NoticeCard(
                        text = "No ${category.shortLabel.lowercase()} source is connected. " +
                            "Your saved titles stay available below; connect a source to " +
                            "browse and search the whole category."
                    )
                    TextButton(
                        onClick = onManageExtensions,
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("Manage extensions") }
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
                    fillWidth = true
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
                    onAction = if (state.allItems.isEmpty()) onRetry else onClearFilters
                )
            }
        } else {
            items(
                visibleItems,
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
                color = MaterialTheme.colorScheme.error
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
            state.canLoadMore -> OutlinedButton(
                onClick = onLoadMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) { Text("Load more titles") }
        }
        Text(
            text = "Titles come from your installed ${category.shortLabel.lowercase()} sources.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onToggle) {
                Text(if (expanded) "Hide" else "Details")
            }
        }
        if (expanded) {
            Column {
                unmatched.forEach { ProviderStatusLine(it, onRetry) }
                TextButton(
                    onClick = onManageExtensions,
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Manage extensions") }
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
                "\"${state.query}\". Search only shows real responses; nothing is invented.",
            actionLabel = "Clear search",
            onAction = onClear
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
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showIssues = !showIssues }) {
                    Text(if (showIssues) "Hide" else "Details")
                }
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
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Filters & sorting",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear) { Text("Clear all") }
        }
        Text(
            text = "Facets come from metadata your sources actually returned. " +
                "Titles without metadata are not guessed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        if (state.enriching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                text = "Reading metadata from your sources…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        FilterGroup("Sort by")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategorySort.entries.forEach { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = { onSort(sort) },
                    label = { Text(sort.label) }
                )
            }
        }

        if (state.formatOptions.isNotEmpty()) {
            FilterGroup("Format")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.formatFilter == null,
                    onClick = { onFormat(null) },
                    label = { Text("All") }
                )
                state.formatOptions.forEach { option ->
                    FilterChip(
                        selected = state.formatFilter == option,
                        onClick = {
                            onFormat(if (state.formatFilter == option) null else option)
                        },
                        label = { Text(option) }
                    )
                }
            }
        }

        if (state.feed?.sources.orEmpty().size > 1) {
            FilterGroup("Source")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sourceFilter == null,
                    onClick = { onSource(null) },
                    label = { Text("All") }
                )
                state.feed?.sources.orEmpty().forEach { source ->
                    FilterChip(
                        selected = state.sourceFilter == source.id,
                        onClick = {
                            onSource(if (state.sourceFilter == source.id) null else source.id)
                        },
                        label = { Text(source.name) }
                    )
                }
            }
        }

        if (state.genreOptions.isNotEmpty()) {
            FilterGroup("Genres")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.genreOptions.forEach { genre ->
                    FilterChip(
                        selected = state.genreFilter == genre,
                        onClick = {
                            onGenre(if (state.genreFilter == genre) null else genre)
                        },
                        label = { Text(genre) }
                    )
                }
            }
        }

        if (category == ContentCategory.MOVIES && state.yearOptions.isNotEmpty()) {
            FilterGroup("Release year")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.yearOptions.forEach { year ->
                    FilterChip(
                        selected = state.yearFilter == year,
                        onClick = { onYear(if (state.yearFilter == year) null else year) },
                        label = { Text(year.toString()) }
                    )
                }
            }
        }

        if (category == ContentCategory.BOOKS && state.authorOptions.isNotEmpty()) {
            FilterGroup("Authors")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.authorOptions.take(60).forEach { author ->
                    FilterChip(
                        selected = state.authorFilter == author,
                        onClick = {
                            onAuthor(if (state.authorFilter == author) null else author)
                        },
                        label = { Text(author, maxLines = 1) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Show results")
        }
    }
}

@Composable
private fun FilterGroup(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
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
