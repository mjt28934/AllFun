package app.allfun.ui.category

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.ContentItem
import app.allfun.data.content.category
import app.allfun.data.db.LibraryRow
import app.allfun.ui.components.ContentCard
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.ErrorState
import app.allfun.ui.components.LoadingState
import app.allfun.ui.components.MediaCard
import app.allfun.ui.components.NoticeCard
import app.allfun.ui.components.ProviderStatusLine
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.accent
import app.allfun.ui.components.icon
import app.allfun.ui.components.rememberViewModel

/**
 * The single category browse experience. Movies, books and manga share the same cards,
 * search field, filters, loading/error handling and source status reporting; the only
 * per-category differences are metadata labels and accent colour.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryScreen(
    container: AppContainer,
    category: ContentCategory,
    onOpenMedia: (String) -> Unit,
    onManageSources: () -> Unit
) {
    val vm = rememberViewModel(key = category.name) { CategoryViewModel(category, container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val continueRows by vm.continueRows.collectAsStateWithLifecycle()
    val libraryRows by vm.libraryRows.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var statusExpanded by rememberSaveable { mutableStateOf(false) }

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
    val visibleItems = state.filtered(state.allItems)
    val unmatched = state.feed?.statuses.orEmpty().filter { !it.available || it.message != null }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = category.label,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = category.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                IconButton(onClick = { vm.openFilters() }) {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = "Filter ${category.label}",
                        tint = if (state.hasActiveFilters) category.accent()
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = vm::refresh, enabled = !state.refreshing) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh category")
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search ${category.label.lowercase()}") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = vm::clearSearch) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            if (state.feed?.sources.orEmpty().size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.sourceFilter == null,
                            onClick = { vm.selectSource(null) },
                            label = { Text("All sources") }
                        )
                    }
                    items(state.feed?.sources.orEmpty()) { source ->
                        FilterChip(
                            selected = state.sourceFilter == source.id,
                            onClick = {
                                vm.selectSource(
                                    if (state.sourceFilter == source.id) null else source.id
                                )
                            },
                            label = { Text(source.name, maxLines = 1) }
                        )
                    }
                }
            }

            if (state.formatOptions.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.formatFilter == null,
                            onClick = { vm.selectFormat(null) },
                            label = { Text("All formats") }
                        )
                    }
                    items(state.formatOptions) { format ->
                        FilterChip(
                            selected = state.formatFilter == format,
                            onClick = {
                                vm.selectFormat(
                                    if (state.formatFilter == format) null else format
                                )
                            },
                            label = { Text(format) }
                        )
                    }
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
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = vm::clearFilters) { Text("Clear") }
                }
            }

            if (state.opening || state.refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Box(Modifier.weight(1f)) {
                when {
                    state.query.isNotBlank() -> SearchGrid(
                        state = state,
                        category = category,
                        onOpen = vm::open,
                        onRetry = { vm.setQuery(state.query) },
                        onClear = vm::clearSearch,
                        onManageSources = onManageSources
                    )

                    state.loading -> LoadingState("Loading ${category.label.lowercase()} from your sources")

                    state.feed != null && state.feed?.sources?.isEmpty() == true &&
                        continueForCategory.isEmpty() && libraryForCategory.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = "No ${category.shortLabel.lowercase()} sources yet",
                        message = "Install a source or add-on and its titles will appear here " +
                            "automatically. Sources never become separate tabs.",
                        actionLabel = "Manage sources",
                        onAction = onManageSources
                    )

                    state.error != null && state.allItems.isEmpty() -> ErrorState(
                        error = state.error!!,
                        onRetry = vm::refresh,
                        secondary = "Manage sources" to onManageSources
                    )

                    state.browseAll -> BrowseAllGrid(
                        category = category,
                        state = state,
                        items = visibleItems,
                        onOpen = vm::open,
                        onLoadMore = vm::loadMore,
                        onBack = { vm.setBrowseAll(false) }
                    )

                    else -> CategorySections(
                        category = category,
                        state = state,
                        continueRows = continueForCategory,
                        favorites = favorites,
                        recentlyAdded = recentlyAdded,
                        history = if (category.isReading) history else emptyList(),
                        visibleItems = visibleItems,
                        unmatched = unmatched,
                        statusExpanded = statusExpanded,
                        onToggleStatuses = { statusExpanded = !statusExpanded },
                        onOpenLibraryMedia = onOpenMedia,
                        onOpen = vm::open,
                        onSeeAll = { vm.setBrowseAll(true) },
                        onRetry = vm::refresh,
                        onManageSources = onManageSources
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
                onClear = vm::clearFilters,
                onDone = vm::closeFilters
            )
        }
    }
}

@Composable
private fun CategorySections(
    category: ContentCategory,
    state: CategoryViewModel.UiState,
    continueRows: List<app.allfun.data.db.ContinueRow>,
    favorites: List<LibraryRow>,
    recentlyAdded: List<LibraryRow>,
    history: List<LibraryRow>,
    visibleItems: List<ContentItem>,
    unmatched: List<app.allfun.data.content.ProviderStatus>,
    statusExpanded: Boolean,
    onToggleStatuses: () -> Unit,
    onOpenLibraryMedia: (String) -> Unit,
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
            item {
                SectionHeader(
                    title = if (category.isVideo) "Continue watching" else "Continue reading",
                    actionLabel = "Library",
                    onAction = { onOpenLibraryMedia(continueRows.first().media.id) }
                )
            }
            item {
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

        if (category.isVideo) {
            if (recentlyAdded.isNotEmpty()) {
                item {
                    SectionHeader(title = "Watchlist", subtitle = "Saved movies and shows")
                }
                item {
                    LibraryRowCarousel(recentlyAdded, onOpenLibraryMedia)
                }
            }
        } else if (favorites.isNotEmpty()) {
            item {
                SectionHeader(title = "Favorites", subtitle = "Pinned across every source")
            }
            item {
                LibraryRowCarousel(favorites, onOpenLibraryMedia)
            }
        }

        if (history.isNotEmpty()) {
            item {
                SectionHeader(title = "Reading history", actionLabel = "Library")
            }
            item {
                LibraryRowCarousel(history, onOpenLibraryMedia)
            }
        }

        state.feed?.shelves.orEmpty().forEach { shelf ->
            val shelfItems = state.filtered(shelf.items)
            if (shelfItems.isEmpty()) return@forEach
            item("shelf-${shelf.id}") {
                SectionHeader(title = shelf.title)
            }
            item("shelf-row-${shelf.id}") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shelfItems, key = { it.primary.sourceId + "|" + it.primary.key }) { item ->
                        ContentCard(
                            item = item,
                            details = state.detailsFor(item),
                            onClick = { onOpen(item) }
                        )
                    }
                }
            }
        }

        if (visibleItems.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "All from your sources",
                    actionLabel = "See all",
                    onAction = onSeeAll
                )
            }
            item {
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
            item {
                SectionHeader(title = "Recently added", subtitle = "From your library")
            }
            item {
                LibraryRowCarousel(recentlyAdded.take(20), onOpenLibraryMedia)
            }
        }

        if (state.feed?.items.isNullOrEmpty() && visibleItems.isEmpty()) {
            item {
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
            item {
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
                        unmatched.forEach { ProviderStatusLine(it) }
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
    onOpen: (ContentItem) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onManageSources: () -> Unit
) {
    when {
        state.searching -> LoadingState("Searching every ${category.shortLabel.lowercase()} source")
        state.searchError != null -> ErrorState(
            error = state.searchError!!,
            onRetry = onRetry,
            secondary = "Manage sources" to onManageSources
        )
        state.searchResults.isEmpty() -> EmptyState(
            icon = Icons.Outlined.Search,
            title = "No results",
            message = "No installed ${category.shortLabel.lowercase()} source returned " +
                "\"${state.query}\". Search only shows real responses; nothing is invented.",
            actionLabel = "Clear search",
            onAction = onClear
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchSummary(
                    count = state.searchResults.size,
                    query = state.query,
                    statuses = state.searchStatuses
                )
            }
            items(
                state.searchResults,
                key = { it.primary.sourceId + "|" + it.primary.key }
            ) { item ->
                ContentCard(
                    item = item,
                    details = state.detailsFor(item),
                    onClick = { onOpen(item) },
                    width = 120
                )
            }
        }
    }
}

@Composable
private fun SearchSummary(
    count: Int,
    query: String,
    statuses: List<app.allfun.data.content.ProviderStatus>
) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            text = "$count ${if (count == 1) "result" else "results"} for \"$query\"",
            style = MaterialTheme.typography.labelLarge
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
                text = "${items.size} titles",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text("Back to shelves") }
        }
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FilterList,
                title = "No titles match these filters",
                message = "Clear a filter or refresh the category to see the provider results again."
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(items, key = { it.primary.sourceId + "|" + it.primary.key }) { item ->
                ContentCard(
                    item = item,
                    details = state.detailsFor(item),
                    onClick = { onOpen(item) },
                    width = 120
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
                            CircularProgressIndicator()
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
                text = "Filters",
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
