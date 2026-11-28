package app.allfun.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.settings.LibrarySort
import app.allfun.data.settings.LibraryView
import app.allfun.ui.components.AllFunButton
import app.allfun.ui.components.AllFunButtonVariant
import app.allfun.ui.components.AllFunChip
import app.allfun.ui.components.AllFunSheet
import app.allfun.ui.components.AppTopBar
import app.allfun.ui.components.EmptyIllustration
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.MediaCard
import app.allfun.ui.components.MediaListRow
import app.allfun.ui.components.SearchField
import app.allfun.ui.components.TopBarAction
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.components.staggeredReveal
import app.allfun.ui.formatBytes
import app.allfun.ui.theme.AllFun
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
        AppTopBar(
            title = "Library",
            meta = buildString {
                append("${state.all.size} ")
                append(if (state.all.size == 1) "title" else "titles")
                if (state.items.size != state.all.size) {
                    append(" · ${state.items.size} shown")
                }
                append(" · ${state.sort.label}")
            },
            actions = {
                TopBarAction(
                    icon = if (state.view == LibraryView.GRID) Icons.Outlined.ViewAgenda
                    else Icons.Outlined.GridView,
                    contentDescription = if (state.view == LibraryView.GRID) {
                        "Switch to list layout"
                    } else {
                        "Switch to grid layout"
                    },
                    onClick = {
                        vm.setView(
                            if (state.view == LibraryView.GRID) LibraryView.LIST
                            else LibraryView.GRID
                        )
                    }
                )
                Box {
                    TopBarAction(
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = "Sort library, ${state.sort.label}",
                        onClick = { sortMenu = true }
                    )
                    DropdownMenu(
                        expanded = sortMenu,
                        onDismissRequest = { sortMenu = false }
                    ) {
                        LibrarySort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        color = if (option == state.sort) {
                                            AllFun.colors.accent
                                        } else {
                                            AllFun.colors.textPrimary
                                        }
                                    )
                                },
                                onClick = {
                                    sortMenu = false
                                    vm.setSort(option)
                                },
                                leadingIcon = {
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
                                            AllFun.colors.accent
                                        } else {
                                            AllFun.colors.textSecondary
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        )

        SearchField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = "Search title, author or genre",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // Content groups: everything, films/series, books and manga. Extensions, providers
        // and downloads never become their own tab.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibraryFilter.entries) { option ->
                val count = state.counts[option] ?: 0
                AllFunChip(
                    label = if (count > 0) "${option.label} · $count" else option.label,
                    selected = state.filter == option,
                    onClick = { vm.setFilter(option) }
                )
            }
        }

        Box(Modifier.weight(1f)) {
            if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.CollectionsBookmark,
                    title = when {
                        state.all.isEmpty() -> "Your shelf is empty"
                        state.query.isNotBlank() -> "No matches"
                        else -> "No ${state.filter.label.lowercase()} yet"
                    },
                    message = when {
                        state.all.isEmpty() ->
                            "Saved and downloaded titles appear here."
                        state.query.isNotBlank() ->
                            "Nothing here matches \u201C${state.query}\u201D. Try a shorter word."
                        else -> "Try another category, or add something new."
                    },
                    actionLabel = "Add a source",
                    onAction = onBrowse,
                    illustration = EmptyIllustration.SHELF
                )
            } else if (state.view == LibraryView.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(state.grid.minWidth.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
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
        AllFunSheet(
            title = "Remove downloaded files?",
            onDismiss = { pendingRemove = null }
        ) {
            Text(
                text = "The offline files for \"$title\" are deleted. The title stays in your " +
                    "library and can be opened again while its provider is available.",
                style = MaterialTheme.typography.bodyLarge,
                color = AllFun.colors.textSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AllFunButton(
                    text = "Cancel",
                    onClick = { pendingRemove = null },
                    variant = AllFunButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                AllFunButton(
                    text = "Remove",
                    onClick = {
                        vm.removeDownloads(id)
                        pendingRemove = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
