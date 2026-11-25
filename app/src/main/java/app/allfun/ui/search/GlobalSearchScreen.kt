package app.allfun.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.format
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFSpinner
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.BottomScrim
import app.allfun.ui.glass.GlassChip
import app.allfun.ui.glass.GlassEmpty
import app.allfun.ui.glass.GlassPanel
import app.allfun.ui.glass.GlassSurface
import app.allfun.ui.glass.GlassTextField
import app.allfun.ui.glass.GlassTier
import app.allfun.ui.glass.GlassToastHost
import app.allfun.ui.glass.GlassTopCapsule
import app.allfun.ui.glass.PosterCard
import app.allfun.ui.glass.Section
import app.allfun.ui.glass.SkeletonPosterGrid
import app.allfun.ui.glass.TopScrim
import app.allfun.ui.glass.WorldBackdrop
import app.allfun.ui.glass.rememberGlassToastState
import app.allfun.ui.glass.rememberHaptics

private val NAV_RESERVE = 140.dp

/**
 * One query across every category. Results stay grouped by section and keep the provider
 * that returned them; nothing is merged away.
 */
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
    val toast = rememberGlassToastState()
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val visible = state.visibleResults()
    val failedSources = state.statuses.filter { !it.available }
    // Idle and results keep separate scroll positions: clearing the query must show the
    // recent searches from the top, not the offset the results list was left at.
    val idleScroll = rememberLazyListState()
    val resultsScroll = rememberLazyListState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(state.query) {
        if (state.query.isNotBlank() && resultsScroll.firstVisibleItemIndex != 0) {
            resultsScroll.scrollToItem(0)
        }
    }
    LaunchedEffect(state.history) {
        if (idleScroll.firstVisibleItemIndex != 0) idleScroll.scrollToItem(0)
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { toast.show(it) }
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

    WorldBackdrop(section = Section.SYSTEM) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 72.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassTextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        placeholder = "Movies, shows, books, novels, manga…",
                        leading = AFIcons.Search,
                        trailing = {
                            if (state.query.isNotEmpty()) {
                                AFIconButton(
                                    icon = AFIcons.Close,
                                    contentDescription = "Clear search",
                                    onClick = { vm.setQuery("") },
                                    visualSize = 32.dp
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                    )
                }

                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        GlassChip(
                            label = "Everything",
                            selected = state.categoryFilter == null,
                            onClick = { vm.setCategory(null) }
                        )
                    }
                    items(ContentCategory.entries) { category ->
                        GlassChip(
                            label = category.shortLabel,
                            selected = state.categoryFilter == category,
                            onClick = { vm.setCategory(category) }
                        )
                    }
                }

                if (state.availableSources.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            GlassChip(
                                label = "All sources",
                                selected = state.sourceFilter == null,
                                onClick = { vm.setSource(null) }
                            )
                        }
                        items(state.availableSources, key = { it.first }) { (sourceId, sourceName) ->
                            GlassChip(
                                label = sourceName,
                                selected = state.sourceFilter == sourceId,
                                onClick = {
                                    vm.setSource(
                                        if (state.sourceFilter == sourceId) null else sourceId
                                    )
                                }
                            )
                        }
                    }
                }

                if (state.searching) {
                    Row(
                        modifier = Modifier.padding(start = 22.dp, top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AFSpinner(size = 14.dp)
                        Spacer(Modifier.width(8.dp))
                        AFText(
                            text = "Searching ${state.knownSources} " +
                                if (state.knownSources == 1) "source" else "sources",
                            style = AFTheme.type.caption,
                            color = AFTheme.colors.textLo
                        )
                    }
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

                        state.error != null -> GlassEmpty(
                            title = "Search could not finish",
                            body = state.error?.message.orEmpty(),
                            actionLabel = "Try again",
                            onAction = vm::retry
                        )

                        state.searching -> Column(Modifier.fillMaxSize()) {
                            SkeletonPosterGrid(
                                columns = 3,
                                rows = 2,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                            AFText(
                                text = "Asking every installed source at once",
                                style = AFTheme.type.caption,
                                color = AFTheme.colors.textLo,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }

                        state.searched && state.total == 0 -> GlassEmpty(
                            title = "No results",
                            body = "No installed source returned \"${state.query}\". Search " +
                                "only reports real responses; nothing is simulated.",
                            actionLabel = "Manage extensions",
                            onAction = onManageExtensions
                        )

                        else -> LazyColumn(
                            state = resultsScroll,
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 8.dp,
                                bottom = NAV_RESERVE
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item(key = "summary") {
                                AFText(
                                    text = "${state.total} " +
                                        (if (state.total == 1) "result" else "results") +
                                        " for \"${state.query}\"",
                                    style = AFTheme.type.label,
                                    color = AFTheme.colors.textMid
                                )
                            }

                            ContentCategory.entries.forEach { category ->
                                val categoryItems = visible[category].orEmpty()
                                if (categoryItems.isEmpty()) return@forEach
                                item(key = "header-${category.name}") {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AFText(
                                            text = category.label,
                                            style = AFTheme.type.heading,
                                            color = AFTheme.colors.textHi,
                                            modifier = Modifier.weight(1f)
                                        )
                                        AFText(
                                            text = categoryItems.size.toString(),
                                            style = AFTheme.type.numeric,
                                            color = AFTheme.colors.textLo
                                        )
                                    }
                                }
                                item(key = "row-${category.name}") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(
                                            categoryItems,
                                            key = { it.primary.sourceId + "|" + it.primary.key }
                                        ) { item ->
                                            PosterCard(
                                                title = item.title,
                                                imageUrl = item.stub.coverUrl,
                                                modifier = Modifier.width(112.dp),
                                                subtitle = listOfNotNull(
                                                    item.format(null),
                                                    item.sourceLabel
                                                ).joinToString(" · ").ifBlank { null },
                                                onClick = { vm.open(item) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (failedSources.isNotEmpty()) {
                                item(key = "source-issues") {
                                    GlassPanel(header = "Source issues") {
                                        AFText(
                                            text = "${failedSources.size} source" +
                                                (if (failedSources.size == 1) "" else "s") +
                                                " could not be searched. Everything else below " +
                                                "is a real response.",
                                            style = AFTheme.type.bodySmall,
                                            color = AFTheme.colors.textMid,
                                            onGlass = true
                                        )
                                        failedSources.forEach { status ->
                                            Row(
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    Modifier
                                                        .width(6.dp)
                                                        .height(6.dp)
                                                        .padding(0.dp)
                                                ) {
                                                    AFIcon(
                                                        icon = AFIcons.Close,
                                                        contentDescription = null,
                                                        tint = AFTheme.colors.failed,
                                                        size = 14.dp
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Column(Modifier.weight(1f)) {
                                                    AFText(
                                                        text = status.sourceName,
                                                        style = AFTheme.type.label,
                                                        color = AFTheme.colors.textHi,
                                                        onGlass = true,
                                                        maxLines = 1
                                                    )
                                                    AFText(
                                                        text = status.message ?: status.state.label,
                                                        style = AFTheme.type.caption,
                                                        color = AFTheme.colors.textLo,
                                                        onGlass = true,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        AFTextButton(
                                            label = "Manage extensions",
                                            onClick = onManageExtensions
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TopScrim(Modifier.align(Alignment.TopCenter))
            BottomScrim(Modifier.align(Alignment.BottomCenter))

            GlassTopCapsule(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                onBack = {
                    focus.clearFocus()
                    onBack()
                }
            ) {
                AFText(
                    text = "Search",
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                if (state.knownSources > 0) {
                    AFText(
                        text = "${state.knownSources} " +
                            if (state.knownSources == 1) "source" else "sources",
                        style = AFTheme.type.numeric,
                        color = AFTheme.colors.textLo,
                        modifier = Modifier.padding(end = 14.dp)
                    )
                }
            }

            GlassToastHost(
                state = toast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 108.dp)
            )
        }
    }
}

/**
 * The state before typing. Recent searches are real, stored locally, and removable; when
 * there are none the screen explains exactly what will be searched instead of showing
 * invented "trending" terms.
 */
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
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = NAV_RESERVE
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (history.isNotEmpty()) {
            item("history-header") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AFText(
                        text = "Recent searches",
                        style = AFTheme.type.heading,
                        color = AFTheme.colors.textHi,
                        modifier = Modifier.weight(1f)
                    )
                    AFTextButton("Clear", onClearHistory)
                }
            }
            item("history-chips") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEach { term ->
                        HistoryRow(
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
                GlassEmpty(
                    title = if (hasSources) "Type a title to begin" else "Nothing to search yet",
                    body = if (hasSources) {
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
private fun HistoryRow(
    term: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val haptics = rememberHaptics()
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        tier = GlassTier.CLEAR,
        shape = CircleShape,
        onClick = {
            haptics.tick()
            onOpen()
        },
        onClickLabel = "Search for $term"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AFIcon(AFIcons.Clock, null, tint = AFTheme.colors.textLo, size = 16.dp)
            Spacer(Modifier.width(10.dp))
            AFText(
                text = term,
                style = AFTheme.type.label,
                color = AFTheme.colors.textHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 260.dp)
            )
            AFIconButton(
                icon = AFIcons.Close,
                contentDescription = "Remove $term from recent searches",
                onClick = {
                    haptics.tick()
                    onRemove()
                },
                visualSize = 40.dp
            )
        }
    }
}
