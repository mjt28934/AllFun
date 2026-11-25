package app.allfun.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.settings.LibrarySort
import app.allfun.data.settings.LibraryView
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.MediaCard
import app.allfun.ui.components.MediaListRow
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.components.staggeredReveal
import app.allfun.ui.formatBytes
import app.allfun.ui.theme.LocalReduceMotion
import java.text.DateFormat
import java.util.Date

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
    var pendingRemove by remember { mutableStateOf<String?>(null) }

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
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CollectionsBookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
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
                                // Each order gets its own glyph so the menu is readable
                                // without reading the whole list; the selected row is the
                                // only one tinted with the accent.
                                Icon(
                                    imageVector = when (option) {
                                        LibrarySort.RECENTLY_UPDATED -> Icons.Outlined.Update
                                        LibrarySort.RECENTLY_ADDED -> Icons.Outlined.NewReleases
                                        LibrarySort.RECENTLY_OPENED -> Icons.Outlined.History
                                        LibrarySort.TITLE -> Icons.Outlined.SortByAlpha
                                        LibrarySort.AUTHOR -> Icons.Outlined.Person
                                    },
                                    contentDescription = null,
                                    tint = if (option == state.sort) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
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

        // Content groups: everything, films/series, books and manga. Extensions, providers
        // and downloads never become their own tab.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibraryFilter.entries) { option ->
                val count = state.counts[option] ?: 0
                FilterChip(
                    selected = state.filter == option,
                    onClick = { vm.setFilter(option) },
                    label = { Text(if (count > 0) "${option.label} · $count" else option.label) }
                )
            }
        }

        Box(Modifier.weight(1f)) {
            if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.CollectionsBookmark,
                    title = when {
                        state.all.isEmpty() -> "Nothing here yet"
                        state.query.isNotBlank() -> "No matches"
                        else -> "No ${state.filter.label.lowercase()} yet"
                    },
                    message = when {
                        state.all.isEmpty() ->
                            "Titles you save, read or download land on this shelf. Install an " +
                                "extension to start browsing."
                        state.query.isNotBlank() ->
                            "Nothing matches \"${state.query}\". Try a shorter search."
                        else -> "Try another category, or add something new."
                    },
                    actionLabel = "Browse sources",
                    onAction = onBrowse
                )
            } else if (state.view == LibraryView.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(state.grid.minWidth.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(state.items, key = { _, row -> row.media.id }) { index, row ->
                        MediaCard(
                            media = row.media,
                            progress = row.fraction,
                            onClick = { onOpenMedia(row.media.id) },
                            fillWidth = true,
                            downloadLabel = state.downloads[row.media.id]?.badge(),
                            caption = state.downloads[row.media.id]?.detail(),
                            modifier = Modifier
                                .staggeredReveal(index)
                                .then(if (reduceMotion) Modifier else Modifier.animateItem())
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(state.items, key = { _, row -> row.media.id }) { index, row ->
                        val download = state.downloads[row.media.id]
                        MediaListRow(
                            media = row.media,
                            progress = row.fraction,
                            onClick = { onOpenMedia(row.media.id) },
                            onToggleFavorite = { vm.toggleFavorite(row.media.id) },
                            downloadLabel = download?.detail(),
                            onRemoveDownload = download
                                ?.takeIf { it.completed > 0 }
                                ?.let { { pendingRemove = row.media.id } },
                            modifier = Modifier
                                .staggeredReveal(index)
                                .then(if (reduceMotion) Modifier else Modifier.animateItem())
                        )
                    }
                }
            }
        }
    }

    if (pendingRemove != null) {
        val id = pendingRemove!!
        val title = state.all.firstOrNull { it.media.id == id }?.media?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove downloaded files?") },
            text = {
                Text(
                    "The offline files for \"$title\" are deleted. The title stays in your " +
                        "library and can be opened again while its provider is available."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeDownloads(id)
                        pendingRemove = null
                    }
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * "Pick up where you left off" moved into the shelf itself: every card already carries
 * its progress bar, so the Library no longer opens with a second, sideways row.
 */
private fun LibraryDownload.badge(): String? = when {
    active > 0 && waitingForWifi -> "Waiting for Wi-Fi"
    active > 0 -> progress?.let { "Downloading $it%" } ?: "Downloading"
    completed > 0 -> "Downloaded"
    failed > 0 -> "Download failed"
    else -> null
}

private fun LibraryDownload.detail(): String = buildList {
    if (completed > 0) {
        add("Downloaded $completed ${if (completed == 1) "file" else "files"}")
        if (bytes > 0) add(formatBytes(bytes))
        completedAt?.let { add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) }
    }
    if (active > 0) {
        add(if (waitingForWifi) "Waiting for Wi-Fi" else progress?.let { "Downloading $it%" } ?: "Downloading")
    }
    if (failed > 0) add("$failed failed")
}.joinToString(" · ")
