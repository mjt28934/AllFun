package app.allfun.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.db.ContinueRow
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import app.allfun.data.settings.HomeSection
import app.allfun.data.settings.Settings
import app.allfun.ui.components.DiscoveryCard
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.ErrorState
import app.allfun.ui.components.LoadingState
import app.allfun.ui.components.MediaCard
import app.allfun.ui.components.NoticeCard
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.ThumbRow
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.relativeTime
import app.allfun.ui.theme.Cyan
import app.allfun.ui.theme.Violet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenMedia: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onBrowse: () -> Unit
) {
    val vm = rememberViewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val opening by vm.opening.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var customizeOpen by remember { mutableStateOf(false) }

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

    val sections = settings.homeSections.filter { it.hasData(state) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                    count = state.libraryCount,
                    onCustomize = { customizeOpen = true },
                    onRefresh = { vm.refresh() },
                    refreshing = state.shelvesLoading
                )
            }

            if (opening != null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }

            if (state.libraryCount == 0 && state.recommendations.values.all { it.isEmpty() }) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Explore,
                        title = "Your shelf is empty",
                        message = "Import an EPUB, point AllFun at a folder of videos, or connect " +
                            "Jellyfin, a Stremio addon or an extension. Everything then shares one " +
                            "library and one progress bar.",
                        actionLabel = "Find something to add",
                        onAction = onBrowse
                    )
                }
            }

            if (sections.isEmpty() && state.libraryCount > 0) {
                item {
                    NoticeCard(
                        text = "Every Home section is hidden. Use the tune button to bring one back.",
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            sections.forEach { section ->
                when (section) {
                    HomeSection.CONTINUE_READING -> {
                        item {
                            SectionHeader(
                                title = "Continue reading",
                                actionLabel = "Library",
                                onAction = onOpenLibrary
                            )
                        }
                        item {
                            ContinueRow(
                                rows = state.continueReading,
                                onOpen = { onOpenMedia(it.media.id) }
                            )
                        }
                    }

                    HomeSection.CONTINUE_WATCHING -> {
                        item {
                            SectionHeader(
                                title = "Continue watching",
                                actionLabel = "Library",
                                onAction = onOpenLibrary
                            )
                        }
                        item {
                            ContinueRow(
                                rows = state.continueWatching,
                                onOpen = { onOpenMedia(it.media.id) }
                            )
                        }
                    }

                    HomeSection.RECENTLY_ADDED -> {
                        item {
                            SectionHeader(
                                title = "Recently added",
                                actionLabel = "Library",
                                onAction = onOpenLibrary
                            )
                        }
                        item { ThumbRow(state.recentlyAdded) { onOpenMedia(it.id) } }
                    }

                    HomeSection.RECENTLY_OPENED -> {
                        item { SectionHeader(title = "Recently opened") }
                        item { ThumbRow(state.recentlyOpened) { onOpenMedia(it.id) } }
                    }

                    HomeSection.RECENTLY_UPDATED -> {
                        item { SectionHeader(title = "Recently updated") }
                        item { ThumbRow(state.recentlyUpdated) { onOpenMedia(it.id) } }
                    }

                    HomeSection.MANGA_FROM_SOURCES -> {
                        item {
                            SectionHeader(
                                title = "Manga from your sources",
                                actionLabel = "Browse",
                                onAction = onBrowse
                            )
                        }
                        item {
                            RecommendationRow(
                                items = state.recommendations[SourceType.MANGA].orEmpty(),
                                onOpen = vm::open
                            )
                        }
                    }

                    HomeSection.NOVELS_FROM_SOURCES -> {
                        item {
                            SectionHeader(
                                title = "Novels from your sources",
                                actionLabel = "Browse",
                                onAction = onBrowse
                            )
                        }
                        item {
                            RecommendationRow(
                                items = state.recommendations[SourceType.NOVEL].orEmpty(),
                                onOpen = vm::open
                            )
                        }
                    }

                    HomeSection.VIDEO_FROM_SOURCES -> {
                        item {
                            SectionHeader(
                                title = "Movies & TV from your sources",
                                actionLabel = "Browse",
                                onAction = onBrowse
                            )
                        }
                        item {
                            RecommendationRow(
                                items = state.recommendations[SourceType.VIDEO].orEmpty(),
                                onOpen = vm::open
                            )
                        }
                    }
                }
            }

            if (state.shelvesLoading && state.recommendations.values.all { it.isEmpty() }) {
                item { LoadingState("Checking your installed sources") }
            }

            val error = state.shelvesError
            if (error != null && state.recommendations.values.all { it.isEmpty() }) {
                item {
                    ErrorState(
                        error = error,
                        onRetry = { vm.refresh() },
                        retryLabel = "Retry sources"
                    )
                }
            }
        }
    }

    if (customizeOpen) {
        ModalBottomSheet(onDismissRequest = { customizeOpen = false }) {
            CustomizeHomeSheet(
                selected = settings.homeSections,
                onChange = { updated ->
                    scope.launch { container.settings.setHomeSections(updated) }
                }
            )
        }
    }
}

private fun HomeSection.hasData(state: HomeState): Boolean = when (this) {
    HomeSection.CONTINUE_READING -> state.continueReading.isNotEmpty()
    HomeSection.CONTINUE_WATCHING -> state.continueWatching.isNotEmpty()
    HomeSection.RECENTLY_ADDED -> state.recentlyAdded.isNotEmpty()
    HomeSection.RECENTLY_OPENED -> state.recentlyOpened.isNotEmpty()
    HomeSection.RECENTLY_UPDATED -> state.recentlyUpdated.isNotEmpty()
    HomeSection.MANGA_FROM_SOURCES -> state.recommendations[SourceType.MANGA].orEmpty().isNotEmpty()
    HomeSection.NOVELS_FROM_SOURCES -> state.recommendations[SourceType.NOVEL].orEmpty().isNotEmpty()
    HomeSection.VIDEO_FROM_SOURCES -> state.recommendations[SourceType.VIDEO].orEmpty().isNotEmpty()
}

@Composable
private fun ContinueRow(rows: List<ContinueRow>, onOpen: (ContinueRow) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows, key = { it.media.id }) { row ->
            MediaCard(
                media = row.media,
                progress = row.fraction,
                metaOverride = relativeTime(row.progressUpdatedAt),
                onClick = { onOpen(row) }
            )
        }
    }
}

@Composable
private fun RecommendationRow(
    items: List<HomeRecommendation>,
    onOpen: (HomeRecommendation) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.sourceId + "|" + it.stub.key }) { recommendation ->
            DiscoveryCard(
                stub = recommendation.stub,
                sourceName = recommendation.sourceName,
                onClick = { onOpen(recommendation) }
            )
        }
    }
}

@Composable
private fun HomeHeader(
    count: Int,
    onCustomize: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Violet.copy(alpha = 0.24f), Color.Transparent)
                )
            )
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AllFun",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.padding(start = 10.dp))
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(Cyan.copy(alpha = 0.25f), Violet.copy(alpha = 0.25f))
                            ),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (count == 1) "1 title" else "$count titles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh source shelves")
                    }
                }
                IconButton(onClick = onCustomize) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Customize Home sections")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Manga, novels and video — one shelf, one progress bar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeHomeSheet(
    selected: List<HomeSection>,
    onChange: (List<HomeSection>) -> Unit
) {
    var draft by remember(selected) { mutableStateOf(selected) }
    val hidden = HomeSection.entries.filterNot { it in draft }

    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text = "Home sections",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        Text(
            text = "Only sections with real data are shown. Reorder or hide any of them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        draft.forEachIndexed { index, section ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = true,
                    onCheckedChange = {
                        val next = draft.toMutableList().also { it.remove(section) }
                        draft = next
                        onChange(next)
                    }
                )
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (index == 0) return@IconButton
                        val next = draft.toMutableList().also {
                            val item = it.removeAt(index)
                            it.add(index - 1, item)
                        }
                        draft = next
                        onChange(next)
                    },
                    enabled = index > 0
                ) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(
                    onClick = {
                        if (index == draft.lastIndex) return@IconButton
                        val next = draft.toMutableList().also {
                            val item = it.removeAt(index)
                            it.add(index + 1, item)
                        }
                        draft = next
                        onChange(next)
                    },
                    enabled = index < draft.lastIndex
                ) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move down")
                }
            }
        }

        if (hidden.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = "Hidden",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            hidden.forEach { section ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = draft + section
                            draft = next
                            onChange(next)
                        }
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(section.title, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        TextButton(
            onClick = {
                draft = HomeSection.entries
                onChange(HomeSection.entries)
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) { Text("Reset to default") }
    }
}
