package app.allfun.ui.extensions

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.extension.RepoExtension
import app.allfun.data.settings.RepositoryConfig
import app.allfun.data.settings.RepositoryKind
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.IconBadge
import app.allfun.ui.components.staggeredReveal
import app.allfun.ui.components.LoadingState
import app.allfun.ui.components.NoticeCard
import app.allfun.ui.components.StatusPill
import app.allfun.ui.components.rememberViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

private const val TAB_INSTALLED = 0
private const val TAB_DISCOVER = 1
private const val TAB_UPDATES = 2
private const val TAB_REPOSITORIES = 3
private const val TAB_DISABLED = 4

private val tabTitles = listOf("Installed", "Discover", "Updates", "Repositories", "Disabled")

/** Category scope of the marketplace, seeded by the screen the user opened it from. */
private enum class ExtensionScope(val label: String) {
    ALL("All Categories"),
    VIDEO("Movies & TV"),
    BOOK("Books & Novels"),
    MANGA("Manhwa & Manga")
}

private fun ExtensionKind.inScope(scope: ExtensionScope): Boolean = when (scope) {
    ExtensionScope.ALL -> true
    ExtensionScope.VIDEO -> this == ExtensionKind.STREMIO || this == ExtensionKind.JELLYFIN
    ExtensionScope.BOOK -> this == ExtensionKind.NOVEL
    ExtensionScope.MANGA -> this == ExtensionKind.MANGA
}

private fun ExtensionScope.matches(kind: RepositoryKind): Boolean = when (this) {
    ExtensionScope.ALL -> true
    ExtensionScope.VIDEO -> false
    ExtensionScope.BOOK -> kind == RepositoryKind.NOVEL
    ExtensionScope.MANGA -> kind == RepositoryKind.MANGA
}

private fun ExtensionScope.defaultKindFilter(): String? = when (this) {
    ExtensionScope.BOOK -> RepositoryKind.NOVEL.name
    ExtensionScope.MANGA -> RepositoryKind.MANGA.name
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    container: AppContainer,
    initialCategory: ContentCategory? = null,
    onBack: () -> Unit
) {
    val vm = rememberViewModel { ExtensionsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }

    var tab by rememberSaveable {
        mutableIntStateOf(if (initialCategory == null) TAB_INSTALLED else TAB_DISCOVER)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var scope by rememberSaveable {
        mutableStateOf(
            when (initialCategory) {
                ContentCategory.MOVIES -> ExtensionScope.VIDEO
                ContentCategory.BOOKS -> ExtensionScope.BOOK
                ContentCategory.MANGA -> ExtensionScope.MANGA
                null -> ExtensionScope.ALL
            }
        )
    }
    var detailInstalledKey by rememberSaveable { mutableStateOf<String?>(null) }
    var detailEntryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemoveKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddRepository by remember { mutableStateOf(false) }
    var showAddStremio by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.repositoryAddTicket) {
        if (state.repositoryAddTicket > 0) showAddRepository = false
    }
    LaunchedEffect(state.stremioAddTicket) {
        if (state.stremioAddTicket > 0) showAddStremio = false
    }
    LaunchedEffect(Unit) {
        // Opened from a category toolbar: the search is ready to type into immediately.
        if (initialCategory != null) searchFocus.requestFocus()
    }

    val scoped = if (scope == ExtensionScope.ALL) state
    else state.copy(installed = state.installed.filter { it.kind.inScope(scope) })

    val detailInstalled = state.installed.firstOrNull { it.key == detailInstalledKey }
    val detailEntry = state.discover.firstOrNull { it.key == detailEntryKey }
    val pendingRemove = state.installed.firstOrNull { it.key == pendingRemoveKey }

    val summary = buildString {
        append(state.installed.size)
        append(if (state.installed.size == 1) " extension installed" else " extensions installed")
        if (state.updates.isNotEmpty()) append(" · ${state.updates.size} updates")
        if (state.failedRepositoryCount > 0) {
            append(" · ${state.failedRepositoryCount} repositories unavailable")
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
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconBadge(
                    icon = Icons.Outlined.Extension,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 38
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Extensions",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = vm::refresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh installed extensions and repositories"
                    )
                }
            }

            val busyLabel = state.busyLabel
            if (busyLabel != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = busyLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val count = when (index) {
                        TAB_INSTALLED -> scoped.installed.size
                        TAB_UPDATES -> scoped.updates.size
                        TAB_DISABLED -> scoped.disabled.size
                        else -> 0
                    }
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = {
                            Text(
                                text = if (count > 0) "$title ($count)" else title,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            LazyRow(
                state = rememberLazyListState(
                    // Opened from a category: start the strip at that category instead of
                    // hiding the selected chip off the right edge.
                    initialFirstVisibleItemIndex = if (initialCategory != null) scope.ordinal else 0
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ExtensionScope.entries) { option ->
                    FilterChip(
                        selected = scope == option,
                        onClick = {
                            scope = option
                            kindFilter = null
                        },
                        label = { Text(option.label) }
                    )
                }
            }

            if (tab == TAB_INSTALLED || tab == TAB_DISCOVER || tab == TAB_DISABLED) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .focusRequester(searchFocus),
                    placeholder = {
                        Text(
                            when (tab) {
                                TAB_INSTALLED -> "Search installed extensions"
                                TAB_DISABLED -> "Search disabled extensions"
                                else -> "Search extensions and developers"
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
            if (tab == TAB_DISCOVER) {
                // Category scope already narrows the index; the All/Manga/Novels chips were
                // redundant here and pushed the list down.
            }

            Box(Modifier.weight(1f)) {
                when (tab) {
                    TAB_INSTALLED -> InstalledTab(
                        state = scoped,
                        query = query,
                        onOpen = { detailInstalledKey = it.key },
                        onToggle = { item, enabled -> vm.setEnabled(item, enabled) },
                        onDiscover = { tab = TAB_DISCOVER }
                    )

                    TAB_UPDATES -> UpdatesTab(
                        state = scoped,
                        onUpdate = { item -> item.update?.let(vm::install) },
                        onUpdateAll = vm::installAllUpdates,
                        onCheck = vm::refresh,
                        onOpen = { detailInstalledKey = it.key },
                        onRepositories = { tab = TAB_REPOSITORIES }
                    )

                    TAB_DISCOVER -> DiscoverTab(
                        state = state,
                        query = query,
                        kindFilter = kindFilter ?: scope.defaultKindFilter(),
                        scope = scope,
                        onRetryRepository = vm::retryRepository,
                        onOpen = { detailEntryKey = it.key },
                        onInstall = vm::install,
                        onOpenInstalled = { detailInstalledKey = it.key },
                        onAddStremio = {
                            vm.clearStremioError()
                            showAddStremio = true
                        },
                        onInstallFeatured = vm::installFeaturedAddon,
                        onManageRepositories = { tab = TAB_REPOSITORIES }
                    )

                    TAB_REPOSITORIES -> RepositoriesTab(
                        state = state,
                        onRetry = vm::retryRepository,
                        onRemove = vm::removeRepository,
                        onAdd = {
                            vm.clearRepositoryError()
                            showAddRepository = true
                        }
                    )

                    TAB_DISABLED -> DisabledTab(
                        state = scoped,
                        query = query,
                        onOpen = { detailInstalledKey = it.key },
                        onEnable = { vm.setEnabled(it, true) },
                        onUninstall = { pendingRemoveKey = it.key }
                    )
                }
            }
        }
    }

    if (showAddRepository) {
        AddRepositoryDialog(
            adding = state.addingRepository,
            error = state.addRepositoryError,
            onConfirm = vm::addRepository,
            onDismiss = {
                showAddRepository = false
                vm.clearRepositoryError()
            }
        )
    }

    if (showAddStremio) {
        AddStremioDialog(
            adding = state.addingStremio,
            error = state.stremioError,
            onConfirm = vm::addStremio,
            onDismiss = {
                showAddStremio = false
                vm.clearStremioError()
            }
        )
    }

    if (detailInstalled != null || detailEntry != null) {
        ExtensionDetailsDialog(
            installed = detailInstalled,
            entry = detailEntry,
            busy = state.busyKey != null,
            onInstall = {
                detailEntry?.let(vm::install)
            },
            onToggle = { enabled ->
                detailInstalled?.let { vm.setEnabled(it, enabled) }
            },
            onUninstall = {
                pendingRemoveKey = detailInstalled?.key
                detailInstalledKey = null
                detailEntryKey = null
            },
            onDismiss = {
                detailInstalledKey = null
                detailEntryKey = null
            }
        )
    }

    if (pendingRemove != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoveKey = null },
            title = { Text("Remove ${pendingRemove.name}?") },
            text = {
                Text(
                    when (pendingRemove.kind) {
                        ExtensionKind.MANGA, ExtensionKind.NOVEL ->
                            "The extension file is deleted. Titles already in your library stay on " +
                                "the shelf, but their chapters and pages cannot load until you " +
                                "install the extension again."
                        ExtensionKind.STREMIO ->
                            "The addon is removed from Browse. Library entries from it stay, but " +
                                "streams cannot load until you add it again."
                        ExtensionKind.JELLYFIN ->
                            "The server connection and its stored token are removed. Library " +
                                "entries stay, but playback stops working until you reconnect."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.uninstall(pendingRemove)
                        pendingRemoveKey = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveKey = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InstalledTab(
    state: ExtensionsViewModel.UiState,
    query: String,
    onOpen: (InstalledExtension) -> Unit,
    onToggle: (InstalledExtension, Boolean) -> Unit,
    onDiscover: () -> Unit
) {
    if (state.installed.isEmpty() && state.loadingInstalled) {
        LoadingState("Reading installed extensions")
        return
    }
    if (state.installed.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Extension,
            title = "No extensions installed",
            message = "Discover manga extensions and novel plugins from real repositories, or " +
                "connect a Stremio addon or Jellyfin server. Installed files stay in AllFun's " +
                "private storage.",
            actionLabel = "Discover extensions",
            onAction = onDiscover
        )
        return
    }
    val needle = query.trim()
    val visible = state.installed.filter { item ->
        needle.isBlank() ||
            item.name.contains(needle, ignoreCase = true) ||
            item.developer?.contains(needle, ignoreCase = true) == true ||
            item.kind.label.contains(needle, ignoreCase = true)
    }
    if (visible.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Search,
            title = "No installed extension matches",
            message = "Nothing installed matches \"$needle\". Try a shorter search."
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
        ExtensionKind.entries.forEach { kind ->
            val group = visible.filter { it.kind == kind }.sortedBy { it.name.lowercase() }
            if (group.isNotEmpty()) {
                item(key = "header-${kind.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = kind.plural,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            text = group.size.toString(),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                itemsIndexed(group, key = { _, item -> item.key }) { index, item ->
                    ExtensionRow(
                        name = item.name,
                        subtitle = item.subtitle(),
                        iconUrl = item.iconUrl,
                        nsfw = item.nsfw,
                        status = item.statusText(),
                        statusColor = when {
                            item.broken -> MaterialTheme.colorScheme.error
                            item.update != null -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        enabled = item.enabled,
                        onToggle = { enabled -> onToggle(item, enabled) },
                        showSwitch = item.canToggle,
                        onClick = { onOpen(item) },
                        modifier = Modifier
                            .staggeredReveal(index)
                            .animateItem()
                    )
                }
            }
        }
        item(key = "signature-note") {
            Column {
                NoticeCard(
                    text = "Manga extensions downloaded from a repository are checked " +
                        "against that repository's signing key before their code is " +
                        "loaded. Uninstalling an extension never deletes your library " +
                        "entries."
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun UpdatesTab(
    state: ExtensionsViewModel.UiState,
    onUpdate: (InstalledExtension) -> Unit,
    onUpdateAll: () -> Unit,
    onCheck: () -> Unit,
    onOpen: (InstalledExtension) -> Unit,
    onRepositories: () -> Unit
) {
    val updates = state.updates
    if (updates.isEmpty()) {
        EmptyState(
            icon = if (state.loadedRepositoryCount == 0) Icons.Outlined.Extension
            else Icons.Outlined.CheckCircle,
            title = if (state.loadedRepositoryCount == 0) "No repositories loaded"
            else "Everything is up to date",
            message = if (state.loadedRepositoryCount == 0) {
                "Add a repository in the Repositories tab so AllFun can compare installed " +
                    "extension versions with what is published."
            } else {
                "AllFun compared ${state.installed.size} installed extensions with " +
                    "${state.loadedRepositoryCount} repositories. Nothing newer was published."
            },
            actionLabel = if (state.loadedRepositoryCount == 0) "Repositories" else "Check again",
            onAction = if (state.loadedRepositoryCount == 0) onRepositories else onCheck
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${updates.size} ${if (updates.size == 1) "update" else "updates"} available",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onUpdateAll, enabled = state.busyKey == null) {
                    Text("Update all")
                }
            }
        }
        itemsIndexed(updates, key = { _, item -> item.key }) { index, item ->
            val entry = item.update ?: return@itemsIndexed
            ExtensionRow(
                name = item.name,
                subtitle = listOfNotNull(
                    item.kind.label,
                    "v${item.version ?: "?"} → v${entry.version ?: "?"}",
                    item.developer
                ).joinToString(" · "),
                iconUrl = item.iconUrl ?: entry.iconUrl,
                nsfw = item.nsfw,
                status = "New version published by ${entry.repoUrl.hostLabel()}",
                statusColor = MaterialTheme.colorScheme.secondary,
                onClick = { onOpen(item) },
                action = {
                    if (state.busyKey == entry.key) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Button(onClick = { onUpdate(item) }, enabled = state.busyKey == null) {
                            Text("Update")
                        }
                    }
                },
                modifier = Modifier
                    .staggeredReveal(index)
                    .animateItem()
            )
        }
    }
}

@Composable
private fun DiscoverTab(
    state: ExtensionsViewModel.UiState,
    query: String,
    kindFilter: String?,
    scope: ExtensionScope,
    onRetryRepository: (RepositoryConfig) -> Unit,
    onOpen: (RepoExtension) -> Unit,
    onInstall: (RepoExtension) -> Unit,
    onOpenInstalled: (InstalledExtension) -> Unit,
    onAddStremio: () -> Unit,
    onInstallFeatured: (String, String) -> Unit,
    onManageRepositories: () -> Unit
) {
    val filterKind = kindFilter?.let { name ->
        RepositoryKind.entries.firstOrNull { it.name == name }
    }
    val visible = remember(state.discover, query, filterKind, scope) {
        state.discover.filter { entry ->
            scope.matches(entry.kind) &&
                (filterKind == null || entry.kind == filterKind) &&
                (
                    query.isBlank() ||
                        entry.name.contains(query, ignoreCase = true) ||
                        entry.developer?.contains(query, ignoreCase = true) == true ||
                        entry.language?.contains(query, ignoreCase = true) == true ||
                        entry.description?.contains(query, ignoreCase = true) == true
                    )
        }.sortedBy { it.name.lowercase() }
    }
    val failed = state.repos.filter { it.error != null }

    if (scope == ExtensionScope.VIDEO) {
        // Repository indexes carry manga and novel extensions only. Video comes from
        // Stremio addons and Jellyfin servers, so Discover installs addons in place.
        VideoDiscover(
            state = state,
            onAddStremio = onAddStremio,
            onInstallFeatured = onInstallFeatured,
            onOpenInstalled = onOpenInstalled
        )
        return
    }

    if (visible.isEmpty() && state.repos.any { it.loading }) {
        LoadingState("Reading repository indexes")
        return
    }
    if (visible.isEmpty() && state.repos.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Extension,
            title = "No repositories configured",
            message = "Add the address of a Mihon/Komikku manga repository or an LNReader " +
                "plugin index to install extensions without leaving AllFun.",
            actionLabel = "Repositories",
            onAction = onManageRepositories
        )
        return
    }

    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
        if (failed.isNotEmpty()) {
            item(key = "repo-errors") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    failed.forEach { repo ->
                        RepositoryErrorRow(repo) { onRetryRepository(repo.config) }
                    }
                }
            }
        }
        if (scope == ExtensionScope.ALL) {
            item(key = "video-addons") {
                StremioInstallCard(
                    installedCount = state.installed.count { it.kind == ExtensionKind.STREMIO },
                    onAdd = onAddStremio
                )
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = if (query.isNotBlank()) "No matches" else "Nothing in this filter",
                    message = if (query.isNotBlank()) {
                        "No repository extension matches \"$query\". Try a shorter search or " +
                            "another filter."
                    } else {
                        "The loaded repositories contain no extensions of this kind."
                    }
                )
            }
        } else {
            item(key = "summary") {
                Text(
                    text = "${visible.size} extensions · ${state.loadedRepositoryCount} " +
                        "${if (state.loadedRepositoryCount == 1) "repository" else "repositories"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            items(visible, key = { it.key }) { entry ->
                val installed = state.installed.firstOrNull { it.key == entry.key }
                val busy = state.busyKey == entry.key
                ExtensionRow(
                    name = entry.name,
                    subtitle = listOfNotNull(
                        entry.developer,
                        entry.version?.takeIf { it.isNotBlank() }?.let { "v$it" },
                        languageLabel(entry.language),
                        entry.sourceCount.takeIf { it > 0 }
                            ?.let { "$it ${if (it == 1) "source" else "sources"}" },
                        entry.repoUrl.hostLabel()
                    ).joinToString(" · "),
                    iconUrl = entry.iconUrl,
                    nsfw = entry.nsfw,
                    status = when {
                        installed?.update != null ->
                            "Update available: v${installed.version ?: "?"} → v${entry.version ?: "?"}"
                        installed != null -> "Installed · v${installed.version ?: "unknown"}"
                        else -> null
                    },
                    statusColor = if (installed?.update != null) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = { onOpen(entry) },
                    action = {
                        when {
                            busy -> CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            installed == null -> TextButton(onClick = { onInstall(entry) }) {
                                Text("Install")
                            }
                            installed.update != null -> Button(onClick = { onInstall(entry) }) {
                                Text("Update")
                            }
                            else -> Text(
                                text = "Installed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * A browsable list of addons that really answer today, so installing one does not
 * require finding and pasting a manifest URL. Cinemeta comes first because catalogue
 * addons often have no `meta` resource, and without it their detail pages cannot list
 * episodes.
 */
private enum class AddonRole(val label: String) {
    METADATA("Metadata"),
    STREAMS("Streams"),
    SUBTITLES("Subtitles")
}

private data class FeaturedAddon(
    val name: String,
    val description: String,
    val url: String,
    val role: AddonRole
) {
    val baseUrl: String get() = url.removeSuffix("/manifest.json").trimEnd('/')
}

private val featuredAddons = listOf(
    FeaturedAddon(
        name = "Cinemeta",
        description = "Metadata for films and series. Install it first: catalogue addons " +
            "rely on it to list episodes and runtime.",
        url = "https://v3-cinemeta.strem.io/manifest.json",
        role = AddonRole.METADATA
    ),
    FeaturedAddon(
        name = "Torrentio",
        description = "Streams from public torrent indexes, with quality and seed labels.",
        url = "https://torrentio.strem.fun/manifest.json",
        role = AddonRole.STREAMS
    ),
    FeaturedAddon(
        name = "Comet",
        description = "ElfHosted's stream addon, ranked by quality and availability.",
        url = "https://comet.elfhosted.com/manifest.json",
        role = AddonRole.STREAMS
    ),
    FeaturedAddon(
        name = "MediaFusion",
        description = "ElfHosted's catalogues, metadata and streams in one addon.",
        url = "https://mediafusion.elfhosted.com/manifest.json",
        role = AddonRole.STREAMS
    ),
    FeaturedAddon(
        name = "OpenSubtitles v3",
        description = "Subtitle tracks for films and episodes in many languages.",
        url = "https://opensubtitles-v3.strem.io/manifest.json",
        role = AddonRole.SUBTITLES
    ),
    FeaturedAddon(
        name = "WatchHub",
        description = "Official streaming-service links for a title, where available.",
        url = "https://watchhub.strem.io/manifest.json",
        role = AddonRole.STREAMS
    )
)

@Composable
private fun VideoDiscover(
    state: ExtensionsViewModel.UiState,
    onAddStremio: () -> Unit,
    onInstallFeatured: (String, String) -> Unit,
    onOpenInstalled: (InstalledExtension) -> Unit
) {
    val addons = state.installed.filter { it.kind == ExtensionKind.STREMIO }
        .sortedBy { it.name.lowercase() }
    val servers = state.installed.filter { it.kind == ExtensionKind.JELLYFIN }
        .sortedBy { it.name.lowercase() }

    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
        item(key = "stremio-hero") {
            StremioInstallCard(installedCount = addons.size, onAdd = onAddStremio)
        }
        item(key = "directory-header") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 2.dp)) {
                Text(
                    text = "Addon directory",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "One tap installs the addon. Nothing is uploaded; AllFun only " +
                        "asks the addon for its manifest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(featuredAddons, key = { "featured:" + it.baseUrl }) { addon ->
            val installed = addons.any { it.location?.trimEnd('/') == addon.baseUrl }
            FeaturedAddonRow(
                addon = addon,
                installed = installed,
                installing = state.featuredInstalling == addon.baseUrl,
                enabled = !state.addingStremio,
                onInstall = { onInstallFeatured(addon.name, addon.url) }
            )
        }
        if (addons.isNotEmpty()) {
            item(key = "addons-header") { DiscoverGroupHeader("Installed addons", addons.size) }
            items(addons, key = { it.key }) { item ->
                ExtensionRow(
                    name = item.name,
                    subtitle = item.subtitle(),
                    iconUrl = item.iconUrl,
                    nsfw = item.nsfw,
                    status = item.statusText(),
                    statusColor = if (item.broken) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = { onOpenInstalled(item) }
                )
            }
        }
        if (servers.isNotEmpty()) {
            item(key = "servers-header") { DiscoverGroupHeader("Jellyfin servers", servers.size) }
            items(servers, key = { it.key }) { item ->
                ExtensionRow(
                    name = item.name,
                    subtitle = item.subtitle(),
                    iconUrl = item.iconUrl,
                    nsfw = item.nsfw,
                    status = item.statusText(),
                    statusColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onOpenInstalled(item) }
                )
            }
        }
        item(key = "video-note") {
            NoticeCard(
                text = "A Stremio addon supplies catalogs, metadata or streams; most are " +
                    "installed from a URL ending in /manifest.json. Jellyfin servers are " +
                    "connected with the + button on the Movies screen."
            )
        }
    }
}

@Composable
private fun StremioInstallCard(installedCount: Int, onAdd: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Outlined.Extension,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 40
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Stremio addons",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when (installedCount) {
                            0 -> "No addons installed"
                            1 -> "1 addon installed"
                            else -> "$installedCount addons installed"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Paste an addon URL to add its catalogs and streams. Only the addon " +
                    "itself is contacted; nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Install Stremio addon")
            }
        }
    }
}

@Composable
private fun FeaturedAddonRow(
    addon: FeaturedAddon,
    installed: Boolean,
    installing: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = when (addon.role) {
                    AddonRole.METADATA -> Icons.Outlined.Movie
                    AddonRole.STREAMS -> Icons.Outlined.PlayCircle
                    AddonRole.SUBTITLES -> Icons.Outlined.Extension
                },
                tint = MaterialTheme.colorScheme.primary,
                size = 36
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = addon.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = addon.role.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = addon.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            when {
                installing -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                installed -> Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Installed",
                    tint = MaterialTheme.colorScheme.primary
                )
                else -> Button(onClick = onInstall, enabled = enabled) {
                    Text("Install")
                }
            }
        }
    }
}

@Composable
private fun DiscoverGroupHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        StatusPill(text = count.toString(), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RepositoriesTab(
    state: ExtensionsViewModel.UiState,
    onRetry: (RepositoryConfig) -> Unit,
    onRemove: (RepositoryConfig) -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item {
            Text(
                text = "A repository is a JSON index. Manga repositories use the " +
                    "Mihon/Komikku index format; novel repositories use LNReader's " +
                    "plugins.min.json array. AllFun reads the index before saving the address, so " +
                    "a broken or unrelated URL is rejected immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        items(state.repos, key = { "${it.config.kind}:${it.config.url}" }) { repo ->
            RepositoryCard(
                repo = repo,
                onRetry = { onRetry(repo.config) },
                onRemove = if (repo.builtIn) null else ({ onRemove(repo.config) })
            )
        }
        item {
            Column {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add repository")
                }
                Spacer(Modifier.height(10.dp))
                NoticeCard(
                    text = "Signing keys are read from the manga repository's index. When a " +
                        "repository publishes one, AllFun refuses an APK whose certificate does " +
                        "not match before loading any of its code."
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DisabledTab(
    state: ExtensionsViewModel.UiState,
    query: String,
    onOpen: (InstalledExtension) -> Unit,
    onEnable: (InstalledExtension) -> Unit,
    onUninstall: (InstalledExtension) -> Unit
) {
    val disabled = state.disabled
    if (disabled.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Extension,
            title = "Nothing is disabled",
            message = "Disabled extensions keep their files and library entries but are hidden " +
                "from the categories and stop receiving updates until you enable them again."
        )
        return
    }
    val needle = query.trim()
    val visible = disabled.filter { item ->
        needle.isBlank() ||
            item.name.contains(needle, ignoreCase = true) ||
            item.developer?.contains(needle, ignoreCase = true) == true ||
            item.kind.label.contains(needle, ignoreCase = true)
    }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item {
            Text(
                text = "Disabled sources are hidden from the categories and search. Their " +
                    "library entries stay readable only if the content was already imported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        if (visible.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "No disabled extension matches",
                    message = "Nothing disabled matches \"$needle\". Try a shorter search."
                )
            }
        }
        items(visible.sortedBy { it.name.lowercase() }, key = { it.key }) { item ->
            ExtensionRow(
                name = item.name,
                subtitle = item.subtitle(),
                iconUrl = item.iconUrl,
                nsfw = item.nsfw,
                status = "Disabled",
                statusColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onOpen(item) },
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onEnable(item) }) { Text("Enable") }
                        IconButton(onClick = { onUninstall(item) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Remove ${item.name}"
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ExtensionRow(
    name: String,
    subtitle: String,
    iconUrl: String?,
    nsfw: Boolean,
    status: String?,
    statusColor: Color,
    onClick: () -> Unit,
    enabled: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    showSwitch: Boolean = false,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = "Open details for $name", onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtensionAvatar(iconUrl, name, Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (nsfw) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill(
                            text = "18+",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (status != null) {
                    Spacer(Modifier.height(5.dp))
                    StatusPill(text = status, tint = statusColor, maxLines = 2)
                }
            }
            when {
                action != null -> action()
                enabled != null && onToggle != null && showSwitch -> Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = name }
                )
                enabled != null -> Text(
                    text = if (enabled) "On" else "Off",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ExtensionAvatar(url: String?, name: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Initials(name)
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Initials(name) },
                error = { Initials(name) }
            )
        }
    }
}

@Composable
private fun Initials(name: String) {
    Text(
        text = name.take(1).uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun RepositoryCard(
    repo: RepositoryState,
    onRetry: () -> Unit,
    onRemove: (() -> Unit)?
) {
    var showTechnical by remember(repo.config) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(repo.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = repo.kindLabel + if (repo.builtIn) " · built in" else " · custom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Remove ${repo.name}"
                        )
                    }
                }
            }
            Text(
                text = repo.config.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp, top = 2.dp)
            )
            Spacer(Modifier.height(6.dp))
            when {
                repo.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Reading index",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                repo.error != null -> {
                    Text(
                        text = repo.error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Row {
                        TextButton(onClick = onRetry) { Text("Retry") }
                        TextButton(onClick = { showTechnical = !showTechnical }) {
                            Text(if (showTechnical) "Hide technical details" else "Technical details")
                        }
                    }
                    if (showTechnical && !repo.error.technical.isNullOrBlank()) {
                        Text(
                            text = repo.error.technical,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }

                else -> Text(
                    text = "${repo.extensionCount} " +
                        (if (repo.extensionCount == 1) "extension" else "extensions") + " available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun RepositoryErrorRow(repo: RepositoryState, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Could not read ${repo.name}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = repo.error?.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun AddRepositoryDialog(
    adding: Boolean,
    error: app.allfun.data.net.UserError?,
    onConfirm: (RepositoryKind, String) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(RepositoryKind.MANGA) }
    var url by remember { mutableStateOf("") }
    var showTechnical by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text("Add repository") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepositoryKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = {
                                Text(
                                    when (option) {
                                        RepositoryKind.MANGA -> "Manga"
                                        RepositoryKind.NOVEL -> "Novel"
                                    }
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Index URL") },
                    placeholder = {
                        Text(
                            if (kind == RepositoryKind.MANGA) {
                                "https://example.com/index.json"
                            } else {
                                "https://example.com/plugins.min.json"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (!error.technical.isNullOrBlank()) {
                        TextButton(onClick = { showTechnical = !showTechnical }) {
                            Text(if (showTechnical) "Hide technical details" else "Technical details")
                        }
                        if (showTechnical) {
                            Text(
                                text = error.technical,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(kind, url) },
                enabled = !adding && url.isNotBlank()
            ) {
                if (adding) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking")
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !adding) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStremioDialog(
    adding: Boolean,
    error: app.allfun.data.net.UserError?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var showTechnical by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text("Install Stremio addon") },
        text = {
            Column {
                Text(
                    text = "Enter the addon's manifest URL, for example a Stremio addon " +
                        "page's Install link, or the addon's base URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Addon URL") },
                    placeholder = { Text("https://addon.example.com/manifest.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (!error.technical.isNullOrBlank()) {
                        TextButton(onClick = { showTechnical = !showTechnical }) {
                            Text(if (showTechnical) "Hide technical details" else "Technical details")
                        }
                        if (showTechnical) {
                            Text(
                                text = error.technical,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) },
                enabled = !adding && url.isNotBlank()
            ) {
                if (adding) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking")
                } else {
                    Text("Install")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !adding) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionDetailsDialog(
    installed: InstalledExtension?,
    entry: RepoExtension?,
    busy: Boolean,
    onInstall: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val name = installed?.name ?: entry?.name ?: "Extension"
    val kindLabel = installed?.kind?.label
        ?: entry?.kind?.let {
            if (it == RepositoryKind.MANGA) ExtensionKind.MANGA.label else ExtensionKind.NOVEL.label
        }
        ?: "Extension"
    val version = installed?.version ?: entry?.version
    val developer = installed?.developer ?: entry?.developer
    val description = installed?.description ?: entry?.description
    val permissions = installed?.permissions.orEmpty()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExtensionAvatar(installed?.iconUrl ?: entry?.iconUrl, name, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = listOfNotNull(kindLabel, version?.let { "v$it" })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close details")
                    }
                }

                if (installed?.broken == true) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "The file for this extension is missing. Reinstall it, or remove " +
                            "the extension and its orphaned sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (developer != null) {
                    Spacer(Modifier.height(10.dp))
                    DetailLine("Developer", developer)
                }
                if (description != null) {
                    Spacer(Modifier.height(6.dp))
                    DetailLine("About", description)
                }
                if (entry?.site != null) {
                    Spacer(Modifier.height(6.dp))
                    DetailLine("Site", entry.site)
                }
                val repoUrl = installed?.repoUrl ?: entry?.repoUrl
                if (repoUrl != null) {
                    Spacer(Modifier.height(6.dp))
                    DetailLine("Repository", repoUrl)
                }
                if (entry != null && entry.sourceCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    DetailLine("Sources", "${entry.sourceCount}")
                }
                val location = installed?.location
                if (location != null && installed.kind != ExtensionKind.MANGA) {
                    Spacer(Modifier.height(6.dp))
                    DetailLine("Location", location)
                }
                installed?.let {
                    Spacer(Modifier.height(6.dp))
                    DetailLine("Status", if (it.enabled) "Enabled" else "Disabled")
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                if (permissions.isEmpty()) {
                    Text(
                        text = if (installed?.kind == ExtensionKind.MANGA) {
                            "This package declares no Android permissions."
                        } else if (installed == null && entry != null) {
                            "Permissions are read from the installed package. AllFun verifies " +
                                "the publisher's signing key before loading manga extension code."
                        } else {
                            "No permissions recorded for this source."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    permissions.forEach { permission ->
                        Text(
                            text = "• $permission",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (installed?.kind == ExtensionKind.MANGA) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Declared permissions are informational. Extension code runs " +
                            "inside AllFun with AllFun's own permissions; Android does not grant " +
                            "these declarations separately.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))
                if (entry != null) {
                    val availableUpdate = installed == null || installed.update != null
                    if (availableUpdate) {
                        Button(
                            onClick = onInstall,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (installed == null) {
                                    "Install ${entry.name}"
                                } else {
                                    "Update to v${entry.version ?: "new version"}"
                                }
                            )
                        }
                    } else {
                        Text(
                            text = "Installed from this repository · v${installed?.version ?: "unknown"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (installed != null) {
                    if (installed.canToggle) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onToggle(!installed.enabled) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (installed.enabled) "Disable" else "Enable")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onUninstall,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Uninstall")
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun InstalledExtension.subtitle(): String = listOfNotNull(
    kind.label,
    version?.takeIf { it.isNotBlank() }?.let { "v$it" },
    developer,
    repoUrl?.hostLabel(),
    sourceIds.size.takeIf { it > 1 }?.let { "$it sources" }
).joinToString(" · ")

private fun InstalledExtension.statusText(): String? = when {
    broken -> "File missing · reinstall or remove"
    update != null -> "Update available: v${version ?: "?"} → v${update.version ?: "?"}"
    !enabled -> "Disabled"
    else -> null
}

private fun String?.hostLabel(): String = this
    ?.let { runCatching { java.net.URI(it).host }.getOrNull() }
    ?.removePrefix("www.")
    ?.takeIf { it.isNotBlank() }
    ?: "repository"

/**
 * Repository indexes store a two-letter code ("en", "th"). Showing the language name
 * makes the Discover list readable without a hand-maintained table.
 */
private fun languageLabel(code: String?): String? {
    val raw = code?.trim().orEmpty()
    if (raw.isEmpty()) return null
    if (raw.length > 3) return raw
    val name = runCatching { java.util.Locale(raw).displayLanguage }.getOrNull()
    return name?.takeIf { it.isNotBlank() && !it.equals(raw, ignoreCase = true) }
        ?.replaceFirstChar { it.uppercase() }
        ?: raw
}
