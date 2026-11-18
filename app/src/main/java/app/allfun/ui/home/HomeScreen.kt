package app.allfun.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.db.ContinueRow
import app.allfun.data.db.LibraryRow
import app.allfun.ui.components.CategoryCard
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.MediaCard
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.rememberViewModel

/**
 * Unified home. It shows one search entry, continue rows that span every provider, the
 * three category destinations and recently added titles. There are no per-source tabs
 * or per-extension shelves.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenMedia: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onSearch: () -> Unit,
    onOpenCategory: (ContentCategory) -> Unit,
    onManageSources: () -> Unit
) {
    val vm = rememberViewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    val watching = state.continueRows.filter { it.media.kind.isVideo }
    val reading = state.continueRows.filter { it.media.kind.isReading }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                HomeHeader(onSearch = onSearch, onOpenLibrary = onOpenLibrary)
            }

            item {
                SearchEntry(onClick = onSearch)
            }

            if (state.libraryCount == 0 && state.enabledSources == 0) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = "Add your first source",
                        message = "Connect a Stremio addon, Jellyfin server, manga extension, " +
                            "novel plugin, EPUB or video folder. Everything then appears in " +
                            "Movies & Shows, Books & Novels or Manhwa & Manga automatically.",
                        actionLabel = "Manage sources",
                        onAction = onManageSources
                    )
                }
            }

            if (watching.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Continue watching",
                        actionLabel = "Movies & Shows",
                        onAction = { onOpenCategory(ContentCategory.MOVIES) }
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(watching, key = { it.media.id }) { row ->
                            MediaCard(
                                media = row.media,
                                progress = row.fraction,
                                onClick = { onOpenMedia(row.media.id) }
                            )
                        }
                    }
                }
            }

            if (reading.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Continue reading",
                        actionLabel = "Library",
                        onAction = onOpenLibrary
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(reading, key = { it.media.id }) { row ->
                            MediaCard(
                                media = row.media,
                                progress = row.fraction,
                                onClick = { onOpenMedia(row.media.id) }
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Entertainment",
                    subtitle = "Your installed sources feed these three categories"
                )
            }

            items(ContentCategory.entries) { category ->
                CategoryCard(
                    category = category,
                    count = state.categoryCounts[category] ?: 0,
                    sourceCount = state.sourcesByCategory[category] ?: 0,
                    onClick = { onOpenCategory(category) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
            }

            if (state.recentlyAdded.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Recently added",
                        actionLabel = "Library",
                        onAction = onOpenLibrary
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.recentlyAdded, key = { it.media.id }) { row ->
                            MediaCard(
                                media = row.media,
                                progress = row.fraction,
                                metaOverride = row.media.genreList.firstOrNull(),
                                onClick = { onOpenMedia(row.media.id) }
                            )
                        }
                    }
                }
            }

            item {
                SourceSummary(
                    enabled = state.enabledSources,
                    unavailable = state.unavailableSources,
                    onManageSources = onManageSources
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(onSearch: () -> Unit, onOpenLibrary: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "AllFun",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "One library. Three categories.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, contentDescription = "Search everything")
        }
        IconButton(onClick = onOpenLibrary) {
            Icon(Icons.Outlined.CollectionsBookmark, contentDescription = "Library")
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Search movies, shows, books, manga and manhwa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceSummary(
    enabled: Int,
    unavailable: Int,
    onManageSources: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (enabled == 1) "1 source working behind the scenes"
                        else "$enabled sources working behind the scenes",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Extensions never add tabs. They contribute titles, metadata " +
                            "and streams to the categories above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (unavailable > 0) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (unavailable == 1) {
                            "1 source is unavailable. Its library entries and other sources keep working."
                        } else {
                            "$unavailable sources are unavailable. Other sources keep working."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onManageSources) {
                Text("Manage sources & extensions")
            }
        }
    }
}
