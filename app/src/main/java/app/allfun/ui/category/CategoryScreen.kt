package app.allfun.ui.category

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.ContentItem
import app.allfun.data.content.ProviderStatus
import app.allfun.data.content.category
import app.allfun.data.db.ContinueRow
import app.allfun.data.db.LibraryRow
import app.allfun.ui.components.ContentCard
import app.allfun.ui.components.ContentReveal
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.ErrorState
import app.allfun.ui.components.GridSkeleton
import app.allfun.ui.components.IconBadge
import app.allfun.ui.components.LoadingState
import app.allfun.ui.components.MediaCard
import app.allfun.ui.components.NoticeCard
import app.allfun.ui.components.ProviderStatusLine
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.StatusPill
import app.allfun.ui.components.accent
import app.allfun.ui.components.icon
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.theme.LocalReduceMotion
import app.allfun.ui.theme.Motion

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
    onOpenLibrary: () -> Unit,
    onOpenExtensions: () -> Unit
) {
    val vm = rememberViewModel(key = category.name) { CategoryViewModel(category, container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val continueRows by vm.continueRows.collectAsStateWithLifecycle()
    val libraryRows by vm.libraryRows.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
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

    val continueForCategory = continueRows.filter { it.media.kind.category() == category }
    val libraryForCategory = libraryRows.filter { it.media.kind.category() == category }
    val favorites = libraryForCategory.filter { it.media.favorite }
    val recentlyAdded = libraryForCategory.sortedByDescending { it.media.addedAt }
    val history = libraryForCategory
        .filter { it.media.lastOpenedAt != null }
        .sortedByDescending { it.media.lastOpenedAt }
    val visibleItems = state.sorted(state.filtered(state.allItems))
    val searchResults = state.sorted(state.filtered(state.searchResults))
    val statuses = state.feed?.statuses.orEmpty()
    val issues = statuses.filter { !it.available || it.message != null }

    val phase = when {
        state.query.isNotBlank() -> CategoryPhase.SEARCH
        state.loading && state.feed == null -> CategoryPhase.LOADING
        state.feed?.sources?.isEmpty() == true &&
            continueForCategory.isEmpty() && libraryForCategory.isEmpty() -> CategoryPhase.NO_SOURCES
        state.error != null && state.allItems.isEmpty() -> CategoryPhase.ERROR
        state.browseAll -> CategoryPhase.BROWSE
        else -> CategoryPhase.SECTIONS
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            CategoryHeader(
                category = category,
                state = state,
                sourceCount = state.feed?.sources.orEmpty().size,
                issueCount = issues.size,
                searchOpen = searchOpen,
                onOpenSearch = {
                    searchOpen = true
                },
                onOpenFilters = vm::openFilters,
                onRefresh = vm::refresh,
                onOpenExtensions = onOpenExtensions
            )

            if (searchOpen || state.query.isNotEmpty()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .focusRequester(searchFocus),
                    placeholder = { Text("Search ${category.label.lowercase()}") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (state.query.isEmpty()) {
                                    searchOpen = false
                                } else {
                                    vm.clearSearch()
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close search")
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
                )
                LaunchedEffect(searchOpen) {
                    if (searchOpen) searchFocus.requestFocus()
                }
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

            if (state.opening || state.refreshing) {
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
                    CategoryPhase.SEARCH -> SearchGrid(
                        state = state,
                        category = category,
                        items = searchResults,
                        onOpen = vm::open,
                        onRetry = { vm.setQuery(state.query) },
                        onClear = vm::clearSearch,
                        onManageSources = onOpenExtensions
                    )

                    CategoryPhase.LOADING -> LoadingGrid(category)

                    CategoryPhase.NO_SOURCES -> EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = "No ${category.shortLabel.lowercase()} sources yet",
                        message = "Install a source or add-on and its titles will appear here " +
                            "automatically. Sources never become separate tabs.",
                        actionLabel = "Manage sources",
                        onAction = onOpenExtensions
                    )

                    CategoryPhase.ERROR -> ErrorState(
                        error = state.error!!,
                        onRetry = vm::refresh,
                        secondary = "Manage sources" to onOpenExtensions
                    )

                    CategoryPhase.BROWSE -> BrowseAllGrid(
                        category = category,
                        state = state,
                        items = visibleItems,
                        onOpen = vm::open,
                        onLoadMore = vm::loadMore,
                        onClearFilters = vm::clearFilters,
                        onBack = { vm.setBrowseAll(false) }
                    )

                    CategoryPhase.SECTIONS -> CategorySections(
                        category = category,
                        state = state,
                        continueRows = continueForCategory,
                        favorites = favorites,
                        recentlyAdded = recentlyAdded,
                        history = if (category.isReading) history else emptyList(),
                        visibleItems = visibleItems,
                        unmatched = issues,
                        statusExpanded = statusExpanded,
                        onToggleStatuses = { statusExpanded = !statusExpanded },
                        onOpenLibraryMedia = onOpenMedia,
                        onOpenLibrary = onOpenLibrary,
                        onOpen = vm::open,
                        onSeeAll = { vm.setBrowseAll(true) },
                        onRetry = vm::refresh,
                        onManageSources = onOpenExtensions
                    )
                }
            }
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

private enum class CategoryPhase { SEARCH, LOADING, NO_SOURCES, ERROR, BROWSE, SECTIONS }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryHeader(
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    sourceCount: Int,
    issueCount: Int,
    searchOpen: Boolean,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onRefresh: () -> Unit,
    onOpenExtensions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)
    ) {
        // The category name owns the first row; the marketplace entry sits at its end so
        // every category screen opens extensions the same way.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = category.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenExtensions) {
                Icon(
                    Icons.Outlined.Extension,
                    contentDescription = "Extensions for ${category.label}"
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = category.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )
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
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                text = when (sourceCount) {
                    0 -> "No source"
                    1 -> "1 source"
                    else -> "$sourceCount sources"
                },
                tint = if (sourceCount == 0) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(6.dp))
            if (issueCount > 0) {
                StatusPill(
                    text = if (issueCount == 1) "1 issue" else "$issueCount issues",
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(6.dp))
            }
            if (state.hasActiveFilters) {
                StatusPill(text = "Filtered", tint = category.accent())
            }
        }
    }
}

/** Skeletons are only shown while a request is genuinely running. */
@Composable
private fun LoadingGrid(category: ContentCategory) {
    Column(Modifier.fillMaxSize()) {
        LoadingState("Reading ${category.label.lowercase()} from your sources")
        GridSkeleton(count = 9)
    }
}

@Composable
private fun CategorySections(
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    continueRows: List<ContinueRow>,
    favorites: List<LibraryRow>,
    recentlyAdded: List<LibraryRow>,
    history: List<LibraryRow>,
    visibleItems: List<ContentItem>,
    unmatched: List<ProviderStatus>,
    statusExpanded: Boolean,
    onToggleStatuses: () -> Unit,
    onOpenLibraryMedia: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpen: (ContentItem) -> Unit,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit,
    onManageSources: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        if (state.feed?.sources.isNullOrEmpty()) {
            item {
                Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    NoticeCard(
                        text = "No ${category.shortLabel.lowercase()} source is connected. " +
                            "Your saved titles stay available below; connect a source to " +
                            "browse and search the whole category."
                    )
                    TextButton(
                        onClick = onManageSources,
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("Add a ${category.shortLabel.lowercase()} source") }
                }
            }
        }

        if (continueRows.isNotEmpty()) {
            item("continue-header") {
                SectionHeader(
                    title = if (category.isVideo) "Continue watching" else "Continue reading",
                    subtitle = "Across every source",
                    actionLabel = "Library",
                    onAction = onOpenLibrary
                )
            }
            item("continue-row") {
                ContentReveal {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(continueRows, key = { it.media.id }) { row ->
                            MediaCard(
                                media = row.media,
                                progress = row.fraction,
                                onClick = { onOpenLibraryMedia(row.media.id) }
                            )
                        }
                    }
                }
            }
        }

        if (category.isVideo) {
            if (recentlyAdded.isNotEmpty()) {
                item("watchlist-header") {
                    SectionHeader(title = "Watchlist", subtitle = "Saved movies and shows")
                }
                item("watchlist-row") {
                    LibraryRowCarousel(recentlyAdded, onOpenLibraryMedia)
                }
            }
        } else if (favorites.isNotEmpty()) {
            item("favorites-header") {
                SectionHeader(title = "Favorites", subtitle = "Pinned across every source")
            }
            item("favorites-row") {
                LibraryRowCarousel(favorites, onOpenLibraryMedia)
            }
        }

        if (history.isNotEmpty()) {
            item("history-header") {
                SectionHeader(
                    title = "Reading history",
                    actionLabel = "Library",
                    onAction = onOpenLibrary
                )
            }
            item("history-row") {
                LibraryRowCarousel(history, onOpenLibraryMedia)
            }
        }

        state.feed?.shelves.orEmpty().forEach { shelf ->
            val shelfItems = state.sorted(state.filtered(shelf.items))
            if (shelfItems.isEmpty()) return@forEach
            item("shelf-${shelf.id}") {
                SectionHeader(title = shelf.title)
            }
            item("shelf-row-${shelf.id}") {
                ContentReveal {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            shelfItems,
                            key = { it.primary.sourceId + "|" + it.primary.key }
                        ) { item ->
                            ContentCard(
                                item = item,
                                details = state.detailsFor(item),
                                onClick = { onOpen(item) }
                            )
                        }
                    }
                }
            }
        }

        if (visibleItems.isNotEmpty()) {
            item("all-header") {
                SectionHeader(
                    title = "All from your sources",
                    subtitle = "${visibleItems.size} titles · ${state.sort.label}",
                    actionLabel = "See all",
                    onAction = onSeeAll
                )
            }
            item("all-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        visibleItems.take(20),
                        key = { it.primary.sourceId + "|" + it.primary.key }
                    ) { item ->
                        ContentCard(
                            item = item,
                            details = state.detailsFor(item),
                            onClick = { onOpen(item) }
                        )
                    }
                }
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item("added-header") {
                SectionHeader(title = "Recently added", subtitle = "From your library")
            }
            item("added-row") {
                LibraryRowCarousel(recentlyAdded.take(20), onOpenLibraryMedia)
            }
        }

        if (state.feed?.items.isNullOrEmpty() && visibleItems.isEmpty()) {
            item("category-empty") {
                EmptyState(
                    icon = Icons.Outlined.Extension,
                    title = "Nothing to show yet",
                    message = if (unmatched.any { !it.available }) {
                        "Your sources could not be reached. The status below shows which one " +
                            "failed; the rest of the app keeps working."
                    } else {
                        "Your sources returned no titles for this category. Try refreshing " +
                            "or adding another source."
                    },
                    actionLabel = "Refresh",
                    onAction = onRetry
                )
            }
        }

        if (unmatched.isNotEmpty()) {
            item("issues") {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    TextButton(onClick = onToggleStatuses) {
                        Text(if (statusExpanded) "Hide" else "Details")
                    }
                }
                if (statusExpanded) {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        unmatched.forEach { ProviderStatusLine(it, onRetry) }
                        TextButton(
                            onClick = onManageSources,
                            modifier = Modifier.padding(start = 8.dp)
                        ) { Text("Manage sources") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRowCarousel(
    rows: List<LibraryRow>,
    onOpen: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows, key = { it.media.id }) { row ->
            MediaCard(
                media = row.media,
                progress = row.fraction,
                onClick = { onOpen(row.media.id) }
            )
        }
    }
}

@Composable
private fun SearchGrid(
    state: CategoryViewModel.UiState,
    category: ContentCategory,
    items: List<ContentItem>,
    onOpen: (ContentItem) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onManageSources: () -> Unit
) {
    when {
        state.searching -> LoadingGrid(category)
        state.searchError != null -> ErrorState(
            error = state.searchError!!,
            onRetry = onRetry,
            secondary = "Manage sources" to onManageSources
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
            columns = GridCells.Adaptive(116.dp),
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
                    fillWidth = true
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
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            text = "$count ${if (count == 1) "result" else "results"} for \"$query\"",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics {
                contentDescription = "$count results for $query"
            }
        )
        statuses.filter { !it.available }.take(3).forEach { status ->
            Text(
                text = "${status.sourceName}: ${status.message.orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun BrowseAllGrid(
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    items: List<ContentItem>,
    onOpen: (ContentItem) -> Unit,
    onLoadMore: () -> Unit,
    onClearFilters: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${items.size} titles · ${state.sort.label}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text("Back to shelves") }
        }
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FilterList,
                title = "No titles match these filters",
                message = "Clear a filter or refresh the category to see the provider results again.",
                actionLabel = "Clear filters",
                onAction = onClearFilters
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(116.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(items, key = { it.primary.sourceId + "|" + it.primary.key }) { item ->
                ContentCard(
                    item = item,
                    details = state.detailsFor(item),
                    onClick = { onOpen(item) },
                    fillWidth = true
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth()) {
                    state.loadMoreError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else if (state.canLoadMore) {
                            Button(onClick = onLoadMore) { Text("Load more") }
                        }
                    }
                    Text(
                        text = "Titles come from your installed ${category.shortLabel.lowercase()} sources.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
