package app.allfun.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.ui.components.ContentCard
import app.allfun.ui.components.ContentReveal
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.ErrorState
import app.allfun.ui.components.GridSkeleton
import app.allfun.ui.components.LoadingState
import app.allfun.ui.components.ProviderStatusLine
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.rememberViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GlobalSearchScreen(
    container: AppContainer,
    initialCategory: ContentCategory?,
    onBack: () -> Unit,
    onOpenMedia: (String) -> Unit,
    onManageExtensions: () -> Unit
) {
    val vm = rememberViewModel {
        GlobalSearchViewModel(container, initialCategory)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val visible = state.visibleResults()
    val failedSources = state.statuses.filter { !it.available }
    // Idle and results keep separate scroll positions: clearing the query must show the
    // recent searches from the top, not the offset the results list was left at.
    val idleScroll = rememberLazyListState()
    val resultsScroll = rememberLazyListState()

    LaunchedEffect(state.query) {
        if (state.query.isNotBlank() && resultsScroll.firstVisibleItemIndex != 0) {
            resultsScroll.scrollToItem(0)
        }
    }
    // History arrives one frame after the idle list is composed. LazyColumn anchors on the
    // first visible item, so prepending the history section above the hint would leave the
    // hint in place and push the recent searches out of view.
    LaunchedEffect(state.history) {
        if (idleScroll.firstVisibleItemIndex != 0) idleScroll.scrollToItem(0)
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.openMediaId.collect { id ->
            if (id != null) {
                onOpenMedia(id)
                vm.clearOpen()
            }
        }
    }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Movies, shows, books, novels, manga…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        vm.submit()
                        focus.clearFocus()
                    }
                )
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.categoryFilter == null,
                        onClick = { vm.setCategory(null) },
                        label = { Text("Everything") }
                    )
                }
                items(ContentCategory.entries) { category ->
                    FilterChip(
                        selected = state.categoryFilter == category,
                        onClick = { vm.setCategory(category) },
                        label = { Text(category.shortLabel) }
                    )
                }
            }

            if (state.availableSources.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.sourceFilter == null,
                            onClick = { vm.setSource(null) },
                            label = { Text("All sources") }
                        )
                    }
                    items(state.availableSources, key = { it.first }) { (sourceId, sourceName) ->
                        FilterChip(
                            selected = state.sourceFilter == sourceId,
                            onClick = {
                                vm.setSource(
                                    if (state.sourceFilter == sourceId) null else sourceId
                                )
                            },
                            label = { Text(sourceName, maxLines = 1) }
                        )
                    }
                }
            }

            if (state.searching) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(CircleShape)
                )
            }

            Box(Modifier.weight(1f)) {
                when {
                    state.query.isBlank() -> SearchIdle(
                        history = state.history,
                        listState = idleScroll,
                        hasSources = state.knownSources > 0 || state.statuses.isNotEmpty(),
                        onUseHistory = vm::useHistory,
                        onRemoveHistory = vm::removeHistory,
                        onClearHistory = vm::clearHistory,
                        onManageExtensions = onManageExtensions
                    )

                    state.error != null -> ErrorState(
                        error = state.error!!,
                        onRetry = vm::retry,
                        secondary = "Manage extensions" to onManageExtensions
                    )

                    state.searching -> Column(Modifier.fillMaxSize()) {
                        LoadingState("Searching your installed sources")
                        GridSkeleton(count = 9, minCellWidth = 108)
                    }

                    state.searched && state.total == 0 -> EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "No results",
                        message = "No installed source returned \"${state.query}\". " +
                            "Search only reports real responses; nothing is simulated.",
                        actionLabel = "Manage extensions",
                        onAction = onManageExtensions
                    )

                    else -> LazyColumn(
                        state = resultsScroll,
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        item {
                            Text(
                                text = "${state.total} ${if (state.total == 1) "result" else "results"} " +
                                    "for \"${state.query}\"",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }

                        ContentCategory.entries.forEach { category ->
                            val items = visible[category].orEmpty()
                            if (items.isEmpty()) return@forEach
                            item("header-${category.name}") {
                                SectionHeader(
                                    title = category.label,
                                    subtitle = "${items.size} " +
                                        (if (items.size == 1) "title" else "titles")
                                )
                            }
                            item("row-${category.name}") {
                                ContentReveal {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(
                                            items,
                                            key = { it.primary.sourceId + "|" + it.primary.key }
                                        ) { item ->
                                            ContentCard(
                                                item = item,
                                                onClick = { vm.open(item) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (failedSources.isNotEmpty()) {
                            item("source-issues") {
                                Column(Modifier.padding(top = 8.dp)) {
                                    SectionHeader(
                                        title = "Source issues",
                                        subtitle = "Other sources still returned their results"
                                    )
                                    failedSources.forEach { ProviderStatusLine(it) }
                                    TextButton(
                                        onClick = onManageExtensions,
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) { Text("Manage extensions") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The state before typing. Recent searches are real, stored locally, and removable; when
 * there are none the screen explains exactly what will be searched instead of showing
 * invented "trending" terms.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchIdle(
    history: List<String>,
    listState: LazyListState,
    hasSources: Boolean,
    onUseHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onManageExtensions: () -> Unit
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        if (history.isNotEmpty()) {
            item("history-header") {
                SectionHeader(
                    title = "Recent searches",
                    actionLabel = "Clear",
                    onAction = onClearHistory
                )
            }
            item("history-chips") {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { term ->
                        HistoryChip(
                            term = term,
                            onOpen = { onUseHistory(term) },
                            onRemove = { onRemoveHistory(term) }
                        )
                    }
                }
            }
        }

        item("search-hint") {
            Column(Modifier.padding(top = 10.dp)) {
                SectionHeader(
                    title = if (hasSources) "Search across everything" else "No source yet",
                    subtitle = if (hasSources) {
                        "Movies & Shows, Books & Novels and Manhwa & Manga are searched together"
                    } else {
                        "Connect a source to search its catalogue"
                    }
                )
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = if (hasSources) "Type a title to begin" else "Nothing to search yet",
                    message = if (hasSources) {
                        "Every compatible installed source is queried at once. Results are " +
                            "grouped into the three categories, deduplicated, and always show " +
                            "the provider that returned them. Nothing is simulated."
                    } else {
                        "Extensions, addons and servers contribute to the three categories. " +
                            "Install one and search will query it directly."
                    },
                    actionLabel = if (hasSources) null else "Manage extensions",
                    onAction = if (hasSources) null else onManageExtensions
                )
            }
        }
    }
}

@Composable
private fun HistoryChip(
    term: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClickLabel = "Search for $term", onClick = onOpen)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = term,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Remove $term from recent searches", onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}
