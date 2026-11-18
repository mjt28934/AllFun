package app.allfun.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.db.ContinueRow
import app.allfun.data.db.LibraryRow
import app.allfun.data.settings.HomeSection
import app.allfun.ui.components.CategoryCard
import app.allfun.ui.components.ContentReveal
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.IconBadge
import app.allfun.ui.components.MediaCard
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.StatusPill
import app.allfun.ui.components.rememberViewModel

/**
 * Unified home. It shows one search entry, continue rows that span every provider, the
 * three category destinations and shelves built only from the user's real library.
 * There are no per-source tabs or per-extension shelves.
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
    val sections = state.homeSections
    val firstRun = state.libraryCount == 0 && state.enabledSources == 0

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
                HomeHeader(
                    libraryCount = state.libraryCount,
                    onSearch = onSearch,
                    onOpenLibrary = onOpenLibrary
                )
            }

            item {
                SearchEntry(onClick = onSearch)
            }

            if (firstRun) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = "Start your shelf",
                        message = "Connect a Stremio addon, Jellyfin server, manga extension, " +
                            "novel plugin, EPUB or video folder. Everything then appears in " +
                            "Movies & Shows, Books & Novels or Manhwa & Manga automatically.",
                        actionLabel = "Manage sources",
                        onAction = onManageSources,
                        secondaryActionLabel = "Search everything",
                        onSecondaryAction = onSearch
                    )
                }
            }

            if (HomeSection.CONTINUE_WATCHING in sections && watching.isNotEmpty()) {
                item("continue-watching-header") {
                    SectionHeader(
                        title = "Continue watching",
                        subtitle = "Pick up exactly where you stopped",
                        actionLabel = "Movies & Shows",
                        onAction = { onOpenCategory(ContentCategory.MOVIES) }
                    )
                }
                item("continue-watching-row") {
                    ContentReveal {
                        ContinueShelf(rows = watching, onOpen = onOpenMedia)
                    }
                }
            }

            if (HomeSection.CONTINUE_READING in sections && reading.isNotEmpty()) {
                item("continue-reading-header") {
                    SectionHeader(
                        title = "Continue reading",
                        subtitle = "Chapters and pages you left open",
                        actionLabel = "Library",
                        onAction = onOpenLibrary
                    )
                }
                item("continue-reading-row") {
                    ContentReveal {
                        ContinueShelf(rows = reading, onOpen = onOpenMedia)
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Your categories",
                    subtitle = if (state.enabledSources == 0) {
                        "Saved titles stay readable without a source"
                    } else {
                        "Everything installed feeds these three shelves"
                    }
                )
            }

            items(ContentCategory.entries) { category ->
                CategoryCard(
                    category = category,
                    count = state.categoryCounts[category] ?: 0,
                    sourceCount = state.sourcesByCategory[category] ?: 0,
                    issueCount = state.unavailableByCategory[category] ?: 0,
                    onClick = { onOpenCategory(category) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
            }

            if (HomeSection.FAVORITES in sections && state.favorites.isNotEmpty()) {
                item("favorites-header") {
                    SectionHeader(
                        title = "Favorites",
                        subtitle = "Pinned from every category",
                        actionLabel = "Library",
                        onAction = onOpenLibrary
                    )
                }
                item("favorites-row") {
                    ContentReveal {
                        LibraryShelf(rows = state.favorites, onOpen = onOpenMedia)
                    }
                }
            }

            if (HomeSection.RECENTLY_UPDATED in sections && state.recentlyUpdated.isNotEmpty()) {
                item("updated-header") {
                    SectionHeader(
                        title = "Recently updated",
                        subtitle = "New chapters, episodes or progress",
                        actionLabel = "Library",
                        onAction = onOpenLibrary
                    )
                }
                item("updated-row") {
                    ContentReveal {
                        LibraryShelf(rows = state.recentlyUpdated, onOpen = onOpenMedia)
                    }
                }
            }

            if (HomeSection.RECENTLY_OPENED in sections && state.recentlyOpened.isNotEmpty()) {
                item("opened-header") {
                    SectionHeader(
                        title = "Recently opened",
                        actionLabel = "Library",
                        onAction = onOpenLibrary
                    )
                }
                item("opened-row") {
                    ContentReveal {
                        LibraryShelf(rows = state.recentlyOpened, onOpen = onOpenMedia)
                    }
                }
            }

            if (HomeSection.RECENTLY_ADDED in sections && state.recentlyAdded.isNotEmpty()) {
                item("added-header") {
                    SectionHeader(
                        title = "Recently added",
                        actionLabel = "Library",
                        onAction = onOpenLibrary
                    )
                }
                item("added-row") {
                    ContentReveal {
                        LibraryShelf(
                            rows = state.recentlyAdded,
                            onOpen = onOpenMedia,
                            metaFor = { it.media.genreList.firstOrNull() }
                        )
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
private fun ContinueShelf(
    rows: List<ContinueRow>,
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
private fun LibraryShelf(
    rows: List<LibraryRow>,
    onOpen: (String) -> Unit,
    metaFor: (LibraryRow) -> String? = { null }
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows, key = { it.media.id }) { row ->
            MediaCard(
                media = row.media,
                progress = row.fraction,
                metaOverride = metaFor(row),
                onClick = { onOpen(row.media.id) }
            )
        }
    }
}

@Composable
private fun HomeHeader(
    libraryCount: Int,
    onSearch: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "AllFun",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .padding(bottom = 7.dp)
                        .size(7.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
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
            BadgedBox(
                badge = {
                    if (libraryCount > 0) {
                        Badge {
                            Text(
                                text = if (libraryCount > 99) "99+" else libraryCount.toString()
                            )
                        }
                    }
                }
            ) {
                Icon(Icons.Outlined.CollectionsBookmark, contentDescription = "Library")
            }
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = Icons.Outlined.Search,
                tint = MaterialTheme.colorScheme.primary,
                size = 34
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Search movies, books and manga",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Outlined.Extension,
                    tint = MaterialTheme.colorScheme.secondary,
                    size = 40
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (enabled == 1) "1 source behind the scenes"
                        else "$enabled sources behind the scenes",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Sources contribute titles to the categories above. " +
                            "They never become tabs.",
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
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (unavailable == 1) {
                            "1 source needs attention. Everything else keeps working."
                        } else {
                            "$unavailable sources need attention. Everything else keeps working."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onManageSources,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage sources & extensions")
            }
        }
    }
}
