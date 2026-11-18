package app.allfun.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.ui.components.ContentCard
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.ErrorState
import app.allfun.ui.components.ProviderStatusLine
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.rememberViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    container: AppContainer,
    initialCategory: ContentCategory?,
    onBack: () -> Unit,
    onOpenMedia: (String) -> Unit,
    onManageSources: () -> Unit
) {
    val vm = rememberViewModel {
        GlobalSearchViewModel(container, initialCategory)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val visible = state.visibleResults()
    val failedSources = state.statuses.filter { !it.available }

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
                    fontSize = 26.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Movies, shows, books, novels, manga, manhwa…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
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
                    items(state.availableSources) { (sourceId, sourceName) ->
                        FilterChip(
                            selected = state.sourceFilter == sourceId,
                            onClick = {
                                vm.setSource(
                                    if (state.sourceFilter == sourceId) null else sourceId
                                )
                            },
                            label = { Text(sourceName) }
                        )
                    }
                }
            }

            if (state.searching || state.opening) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Box(Modifier.weight(1f)) {
                when {
                    state.query.isBlank() -> EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "Search everything",
                        message = "Every compatible installed source is searched at once. " +
                            "Results are grouped into Movies & Shows, Books & Novels and " +
                            "Manhwa & Manga, deduplicated, and always show the provider that " +
                            "returned them.",
                        actionLabel = if (state.statuses.isEmpty()) "Manage sources" else null,
                        onAction = if (state.statuses.isEmpty()) onManageSources else null
                    )

                    state.error != null -> ErrorState(
                        error = state.error!!,
                        onRetry = vm::retry,
                        secondary = "Manage sources" to onManageSources
                    )

                    state.searching -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.width(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Searching your installed sources",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    state.searched && state.total == 0 -> EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "No results",
                        message = "No installed source returned \"${state.query}\". " +
                            "Search only reports real responses; nothing is simulated.",
                        actionLabel = "Manage sources",
                        onAction = onManageSources
                    )

                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                        item {
                            Text(
                                text = "${state.total} ${if (state.total == 1) "result" else "results"} " +
                                    "for \"${state.query}\"",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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

                        if (failedSources.isNotEmpty()) {
                            item {
                                Column(Modifier.padding(top = 8.dp)) {
                                    SectionHeader(
                                        title = "Source issues",
                                        subtitle = "Other sources still returned their results"
                                    )
                                    failedSources.forEach { ProviderStatusLine(it) }
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
        }
    }
}
