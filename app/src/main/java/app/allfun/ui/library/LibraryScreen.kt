package app.allfun.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.db.LibraryRow
import app.allfun.data.settings.LibrarySort
import app.allfun.data.settings.LibraryView
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.MediaCard
import app.allfun.ui.components.MediaListRow
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.theme.LocalReduceMotion

@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpenMedia: (String) -> Unit,
    onBrowse: () -> Unit
) {
    val vm = rememberViewModel { LibraryViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    var sortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "Library", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = buildString {
                        append("${state.all.size} ")
                        append(if (state.all.size == 1) "title" else "titles")
                        if (state.items.size != state.all.size) {
                            append(" · ${state.items.size} shown")
                        }
                        append(" · ${state.sort.label}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = {
                    vm.setView(
                        if (state.view == LibraryView.GRID) LibraryView.LIST else LibraryView.GRID
                    )
                }
            ) {
                Icon(
                    imageVector = if (state.view == LibraryView.GRID) Icons.Outlined.ViewAgenda
                    else Icons.Outlined.GridView,
                    contentDescription = if (state.view == LibraryView.GRID) {
                        "Switch to list layout"
                    } else {
                        "Switch to grid layout"
                    }
                )
            }
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = "Sort library, ${state.sort.label}"
                    )
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    LibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    color = if (option == state.sort) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            onClick = {
                                sortMenu = false
                                vm.setSort(option)
                            },
                            leadingIcon = {
                                if (option == state.sort) {
                                    Icon(
                                        Icons.Outlined.CollectionsBookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Spacer(Modifier.width(24.dp))
                                }
                            }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search title, author or genre") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = CircleShape
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibraryFilter.entries.size) { index ->
                val option = LibraryFilter.entries[index]
                val count = state.counts[option] ?: 0
                FilterChip(
                    selected = state.filter == option,
                    onClick = { vm.setFilter(option) },
                    label = { Text("${option.label} · $count") }
                )
            }
            item {
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = { vm.toggleFavoritesOnly() },
                    label = { Text("Favorites") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (state.favoritesOnly) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = null
                        )
                    }
                )
            }
        }

        Box(Modifier.weight(1f)) {
            if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.CollectionsBookmark,
                    title = when {
                        state.all.isEmpty() -> "Nothing here yet"
                        state.favoritesOnly && state.query.isBlank() -> "No favorites yet"
                        state.query.isNotBlank() -> "No matches"
                        else -> "No ${state.filter.label.lowercase()} yet"
                    },
                    message = when {
                        state.all.isEmpty() ->
                            "Everything you add — a manga chapter, an imported EPUB, a Jellyfin " +
                                "show — lands on this shelf."
                        state.favoritesOnly ->
                            "Open any title and tap the heart to pin it here."
                        state.query.isNotBlank() ->
                            "Nothing matches \"${state.query}\". Try a shorter search."
                        else -> "Try another category, or add something new."
                    },
                    actionLabel = "Browse sources",
                    onAction = onBrowse
                )
            } else if (state.view == LibraryView.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(132.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(state.items, key = { it.media.id }) { row ->
                        MediaCard(
                            media = row.media,
                            progress = row.fraction,
                            onClick = { onOpenMedia(row.media.id) },
                            fillWidth = true,
                            modifier = if (reduceMotion) Modifier else Modifier.animateItem()
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.items, key = { it.media.id }) { row ->
                        MediaListRow(
                            media = row.media,
                            progress = row.fraction,
                            onClick = { onOpenMedia(row.media.id) },
                            onToggleFavorite = { vm.toggleFavorite(row.media.id) },
                            modifier = if (reduceMotion) Modifier else Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}
