package app.allfun.ui.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.extension.RepoExtension
import app.allfun.data.net.UserError
import app.allfun.data.settings.RepositoryConfig
import app.allfun.data.settings.RepositoryKind
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFDivider
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFOverline
import app.allfun.ui.glass.AFSpinner
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.BottomScrim
import app.allfun.ui.glass.GlassButton
import app.allfun.ui.glass.GlassChip
import app.allfun.ui.glass.GlassDialog
import app.allfun.ui.glass.GlassEmpty
import app.allfun.ui.glass.GlassPanel
import app.allfun.ui.glass.GlassPrimaryAction
import app.allfun.ui.glass.GlassSegmented
import app.allfun.ui.glass.GlassSheet
import app.allfun.ui.glass.GlassSwitch
import app.allfun.ui.glass.GlassTextField
import app.allfun.ui.glass.GlassToastHost
import app.allfun.ui.glass.GlassTopCapsule
import app.allfun.ui.glass.Section
import app.allfun.ui.glass.SkeletonRows
import app.allfun.ui.glass.TopScrim
import app.allfun.ui.glass.WorldBackdrop
import app.allfun.ui.glass.rememberGlassToastState
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
    ALL("All categories"),
    VIDEO("Movies & TV"),
    BOOK("Books & novels"),
    MANGA("Manhwa & manga")
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

/** How a status line is toned: failures and updates read differently from plain notes. */
private enum class RowTone { NEUTRAL, ACCENT, FAILED }

@Composable
fun ExtensionsScreen(
    container: AppContainer,
    initialCategory: ContentCategory? = null,
    onBack: () -> Unit
) {
    val vm = rememberViewModel { ExtensionsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = rememberGlassToastState()
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
        vm.messages.collect { toast.show(it) }
    }
    LaunchedEffect(state.repositoryAddTicket) {
        if (state.repositoryAddTicket > 0) showAddRepository = false
    }
    LaunchedEffect(state.stremioAddTicket) {
        if (state.stremioAddTicket > 0) showAddStremio = false
    }
    LaunchedEffect(Unit) {
        // Opened from a category toolbar: the search is ready to type into immediately.
        if (initialCategory != null) runCatching { searchFocus.requestFocus() }
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

    WorldBackdrop(section = Section.SYSTEM) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 72.dp)
            ) {
                AFText(
                    text = summary,
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textLo,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(10.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabTitles.size) { index ->
                        val count = when (index) {
                            TAB_INSTALLED -> scoped.installed.size
                            TAB_UPDATES -> scoped.updates.size
                            TAB_DISABLED -> scoped.disabled.size
                            else -> 0
                        }
                        GlassChip(
                            label = tabTitles[index],
                            selected = tab == index,
                            badge = count.takeIf { it > 0 },
                            onClick = { tab = index }
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                LazyRow(
                    state = rememberLazyListState(
                        // Opened from a category: start the strip at that category instead
                        // of hiding the selected chip off the right edge.
                        initialFirstVisibleItemIndex = if (initialCategory != null) {
                            scope.ordinal
                        } else {
                            0
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ExtensionScope.entries) { option ->
                        GlassChip(
                            label = option.label,
                            selected = scope == option,
                            onClick = {
                                scope = option
                                kindFilter = null
                            }
                        )
                    }
                }

                if (tab == TAB_INSTALLED || tab == TAB_DISCOVER || tab == TAB_DISABLED) {
                    Spacer(Modifier.height(10.dp))
                    GlassTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = when (tab) {
                            TAB_INSTALLED -> "Search installed extensions"
                            TAB_DISABLED -> "Search disabled extensions"
                            else -> "Search extensions and developers"
                        },
                        leading = AFIcons.Search,
                        trailing = {
                            if (query.isNotEmpty()) {
                                AFIconButton(
                                    icon = AFIcons.Close,
                                    contentDescription = "Clear search",
                                    onClick = { query = "" },
                                    visualSize = 32.dp
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .focusRequester(searchFocus)
                    )
                }

                state.busyLabel?.let { busy ->
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AFSpinner(size = 14.dp)
                        Spacer(Modifier.width(8.dp))
                        AFText(
                            text = busy,
                            style = AFTheme.type.caption,
                            color = AFTheme.colors.textLo,
                            maxLines = 2
                        )
                    }
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

            TopScrim(Modifier.align(Alignment.TopCenter))
            BottomScrim(Modifier.align(Alignment.BottomCenter))

            GlassTopCapsule(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                onBack = onBack
            ) {
                AFText(
                    text = "Extensions",
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                if (tab == TAB_REPOSITORIES) {
                    AFIconButton(
                        icon = AFIcons.Plus,
                        contentDescription = "Add repository",
                        onClick = {
                            vm.clearRepositoryError()
                            showAddRepository = true
                        }
                    )
                }
                AFIconButton(
                    icon = AFIcons.Refresh,
                    contentDescription = "Refresh installed extensions and repositories",
                    onClick = vm::refresh
                )
            }

            GlassToastHost(
                state = toast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )
        }
    }

    if (showAddRepository) {
        AddRepositorySheet(
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
        AddStremioSheet(
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
        ExtensionDetailsSheet(
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
        GlassDialog(
            title = "Remove ${pendingRemove.name}?",
            body = when (pendingRemove.kind) {
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
            },
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                vm.uninstall(pendingRemove)
                pendingRemoveKey = null
            },
            onDismiss = { pendingRemoveKey = null }
        )
    }
}

// ---------------------------------------------------------------- installed

@Composable
private fun InstalledTab(
    state: ExtensionsViewModel.UiState,
    query: String,
    onOpen: (InstalledExtension) -> Unit,
    onToggle: (InstalledExtension, Boolean) -> Unit,
    onDiscover: () -> Unit
) {
    if (state.installed.isEmpty() && state.loadingInstalled) {
        SkeletonRows(
            count = 5,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        return
    }
    if (state.installed.isEmpty()) {
        GlassEmpty(
            title = "No extensions installed",
            body = "Discover manga extensions and novel plugins from real repositories, or " +
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
        GlassEmpty(
            title = "No installed extension matches",
            body = "Nothing installed matches \"$needle\". Try a shorter search."
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExtensionKind.entries.forEach { kind ->
            val group = visible.filter { it.kind == kind }.sortedBy { it.name.lowercase() }
            if (group.isEmpty()) return@forEach
            item(key = "group-${kind.name}") {
                GlassPanel(header = kind.plural) {
                    group.forEachIndexed { index, item ->
                        ExtensionRow(
                            name = item.name,
                            subtitle = item.subtitle(),
                            iconUrl = item.iconUrl,
                            nsfw = item.nsfw,
                            status = item.statusText(),
                            tone = when {
                                item.broken -> RowTone.FAILED
                                item.update != null -> RowTone.ACCENT
                                else -> RowTone.NEUTRAL
                            },
                            onClick = { onOpen(item) },
                            trailing = {
                                if (item.canToggle) {
                                    GlassSwitch(
                                        checked = item.enabled,
                                        onCheckedChange = { enabled -> onToggle(item, enabled) },
                                        modifier = Modifier.semantics {
                                            contentDescription = item.name
                                        }
                                    )
                                } else {
                                    AFText(
                                        text = if (item.enabled) "On" else "Off",
                                        style = AFTheme.type.caption,
                                        color = AFTheme.colors.textLo
                                    )
                                }
                            }
                        )
                        if (index != group.lastIndex) AFDivider()
                    }
                }
            }
        }
        item(key = "signature-note") {
            NoticePanel(
                header = "Signed files",
                body = "Manga extensions downloaded from a repository are checked against " +
                    "that repository's signing key before their code is loaded. " +
                    "Uninstalling an extension never deletes your library entries."
            )
        }
    }
}

// ---------------------------------------------------------------- updates

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
        GlassEmpty(
            title = if (state.loadedRepositoryCount == 0) {
                "No repositories loaded"
            } else {
                "Everything is up to date"
            },
            body = if (state.loadedRepositoryCount == 0) {
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
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "summary") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AFText(
                    text = "${updates.size} " +
                        if (updates.size == 1) "update available" else "updates available",
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    modifier = Modifier.weight(1f)
                )
                AFTextButton(
                    label = "Update all",
                    onClick = onUpdateAll,
                    enabled = state.busyKey == null
                )
            }
        }
        item(key = "list") {
            GlassPanel(header = "New versions published") {
                updates.forEachIndexed { index, item ->
                    val entry = item.update ?: return@forEachIndexed
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
                        tone = RowTone.ACCENT,
                        onClick = { onOpen(item) },
                        trailing = {
                            if (state.busyKey == entry.key) {
                                AFSpinner(size = 18.dp)
                            } else {
                                AFTextButton(
                                    label = "Update",
                                    onClick = { onUpdate(item) },
                                    enabled = state.busyKey == null
                                )
                            }
                        }
                    )
                    if (index != updates.lastIndex) AFDivider()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- discover

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
        }.sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() }))
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
        SkeletonRows(
            count = 6,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        return
    }
    if (visible.isEmpty() && state.repos.isEmpty()) {
        GlassEmpty(
            title = "No repositories configured",
            body = "Add the address of a Mihon/Komikku manga repository or an LNReader " +
                "plugin index to install extensions without leaving AllFun.",
            actionLabel = "Repositories",
            onAction = onManageRepositories
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
    ) {
        if (failed.isNotEmpty()) {
            item(key = "repo-errors") {
                GlassPanel(header = "Repository issues") {
                    failed.forEachIndexed { index, repo ->
                        RepoIssueRow(
                            name = repo.name,
                            message = repo.error?.message.orEmpty(),
                            onRetry = { onRetryRepository(repo.config) }
                        )
                        if (index != failed.lastIndex) AFDivider()
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (scope == ExtensionScope.ALL) {
            item(key = "video-addons") {
                StremioPanel(
                    installedCount = state.installed.count { it.kind == ExtensionKind.STREMIO },
                    onAdd = onAddStremio,
                    body = "Paste an addon URL to add its catalogs and streams. Only the " +
                        "addon itself is contacted; nothing is uploaded."
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                GlassEmpty(
                    title = if (query.isNotBlank()) "No matches" else "Nothing in this filter",
                    body = if (query.isNotBlank()) {
                        "No repository extension matches \"$query\". Try a shorter search or " +
                            "another filter."
                    } else {
                        "The loaded repositories contain no extensions of this kind."
                    }
                )
            }
        } else {
            item(key = "summary") {
                AFText(
                    text = "${visible.size} extensions · ${state.loadedRepositoryCount} " +
                        if (state.loadedRepositoryCount == 1) "repository" else "repositories",
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textLo,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            RepositoryKind.entries.forEach { kind ->
                val group = visible.filter { it.kind == kind }
                if (group.isEmpty()) return@forEach
                item(key = "header-${kind.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AFOverline(
                            text = if (kind == RepositoryKind.MANGA) {
                                "Manga extensions"
                            } else {
                                "Novel plugins"
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AFText(
                            text = group.size.toString(),
                            style = AFTheme.type.numeric,
                            color = AFTheme.colors.textLo
                        )
                    }
                }
                items(group, key = { it.key }) { entry ->
                    val installed = state.installed.firstOrNull { it.key == entry.key }
                    val busy = state.busyKey == entry.key
                    DiscoverRow(
                        entry = entry,
                        installed = installed,
                        busy = busy,
                        enabled = state.busyKey == null,
                        onOpen = { onOpen(entry) },
                        onInstall = { onInstall(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverRow(
    entry: RepoExtension,
    installed: InstalledExtension?,
    busy: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit
) {
    Column {
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
            tone = if (installed?.update != null) RowTone.ACCENT else RowTone.NEUTRAL,
            onClick = onOpen,
            trailing = {
                when {
                    busy -> AFSpinner(size = 18.dp)
                    installed == null -> AFTextButton(
                        label = "Install",
                        onClick = onInstall,
                        enabled = enabled
                    )
                    installed.update != null -> AFTextButton(
                        label = "Update",
                        onClick = onInstall,
                        enabled = enabled
                    )
                    else -> AFText(
                        text = "Installed",
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo
                    )
                }
            }
        )
        AFDivider()
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

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "stremio-hero") {
            StremioPanel(
                installedCount = addons.size,
                onAdd = onAddStremio,
                body = "Video sources come from Stremio addons. An addon can supply " +
                    "catalogs, metadata or streams; most are installed from a URL ending " +
                    "in /manifest.json."
            )
        }
        item(key = "directory") {
            GlassPanel(header = "Addon directory") {
                AFText(
                    text = "One tap installs the addon. Nothing is uploaded; AllFun only " +
                        "asks the addon for its manifest.",
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textLo,
                    onGlass = true,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                featuredAddons.forEachIndexed { index, addon ->
                    val installed = addons.any {
                        it.location?.trimEnd('/') == addon.baseUrl
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AFText(
                                    text = addon.name,
                                    style = AFTheme.type.body,
                                    color = AFTheme.colors.textHi,
                                    onGlass = true,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(8.dp))
                                AFText(
                                    text = addon.role.label.uppercase(),
                                    style = AFTheme.type.overline,
                                    color = AFTheme.colors.textLo,
                                    onGlass = true
                                )
                            }
                            AFText(
                                text = addon.description,
                                style = AFTheme.type.caption,
                                color = AFTheme.colors.textMid,
                                onGlass = true,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        when {
                            state.featuredInstalling == addon.baseUrl -> AFSpinner(size = 18.dp)
                            installed -> AFIcon(
                                icon = AFIcons.Check,
                                contentDescription = "Installed",
                                tint = AFTheme.colors.ok,
                                size = 20.dp
                            )
                            else -> AFTextButton(
                                label = "Install",
                                onClick = { onInstallFeatured(addon.name, addon.url) },
                                enabled = !state.addingStremio
                            )
                        }
                    }
                    if (index != featuredAddons.lastIndex) AFDivider()
                }
            }
        }
        if (addons.isNotEmpty()) {
            item(key = "addons") {
                GlassPanel(header = "Installed addons") {
                    addons.forEachIndexed { index, item ->
                        ExtensionRow(
                            name = item.name,
                            subtitle = item.subtitle(),
                            iconUrl = item.iconUrl,
                            nsfw = item.nsfw,
                            status = item.statusText(),
                            tone = if (item.broken) RowTone.FAILED else RowTone.NEUTRAL,
                            onClick = { onOpenInstalled(item) }
                        )
                        if (index != addons.lastIndex) AFDivider()
                    }
                }
            }
        }
        if (servers.isNotEmpty()) {
            item(key = "servers") {
                GlassPanel(header = "Jellyfin servers") {
                    servers.forEachIndexed { index, item ->
                        ExtensionRow(
                            name = item.name,
                            subtitle = item.subtitle(),
                            iconUrl = item.iconUrl,
                            nsfw = item.nsfw,
                            status = item.statusText(),
                            tone = RowTone.NEUTRAL,
                            onClick = { onOpenInstalled(item) }
                        )
                        if (index != servers.lastIndex) AFDivider()
                    }
                }
            }
        }
        item(key = "video-note") {
            NoticePanel(
                header = "How video sources work",
                body = "A Stremio addon supplies catalogs, metadata or streams; most are " +
                    "installed from a URL ending in /manifest.json. Jellyfin servers are " +
                    "connected with the + button on the Movies screen."
            )
        }
    }
}

@Composable
private fun StremioPanel(
    installedCount: Int,
    onAdd: () -> Unit,
    body: String
) {
    GlassPanel(header = "Stremio addons") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AFIcon(
                icon = AFIcons.Clapper,
                contentDescription = null,
                tint = AFTheme.colors.accent,
                size = 22.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                AFText(
                    text = when (installedCount) {
                        0 -> "No addons installed"
                        1 -> "1 addon installed"
                        else -> "$installedCount addons installed"
                    },
                    style = AFTheme.type.body,
                    color = AFTheme.colors.textHi,
                    onGlass = true
                )
                AFText(
                    text = body,
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textLo,
                    onGlass = true,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        AFTextButton(
            label = "Install Stremio addon",
            onClick = onAdd,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ---------------------------------------------------------------- repositories

@Composable
private fun RepositoriesTab(
    state: ExtensionsViewModel.UiState,
    onRetry: (RepositoryConfig) -> Unit,
    onRemove: (RepositoryConfig) -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "intro") {
            AFText(
                text = "A repository is a JSON index. Manga repositories use the " +
                    "Mihon/Komikku index format; novel repositories use LNReader's " +
                    "plugins.min.json array. AllFun reads the index before saving the " +
                    "address, so a broken or unrelated URL is rejected immediately.",
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo
            )
        }
        items(state.repos, key = { "${it.config.kind}:${it.config.url}" }) { repo ->
            RepositoryPanel(
                repo = repo,
                onRetry = { onRetry(repo.config) },
                onRemove = if (repo.builtIn) null else ({ onRemove(repo.config) })
            )
        }
        item(key = "add") {
            GlassButton(
                label = "Add repository",
                onClick = onAdd,
                icon = AFIcons.Plus,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item(key = "note") {
            NoticePanel(
                header = "Signing keys",
                body = "Signing keys are read from the manga repository's index. When a " +
                    "repository publishes one, AllFun refuses an APK whose certificate does " +
                    "not match before loading any of its code."
            )
        }
    }
}

@Composable
private fun RepositoryPanel(
    repo: RepositoryState,
    onRetry: () -> Unit,
    onRemove: (() -> Unit)?
) {
    var showTechnical by remember(repo.config) { mutableStateOf(false) }
    GlassPanel(header = repo.name) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                AFText(
                    text = repo.kindLabel + if (repo.builtIn) " · built in" else " · custom",
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textLo,
                    onGlass = true
                )
                AFText(
                    text = repo.config.url,
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textMid,
                    onGlass = true,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (onRemove != null) {
                AFIconButton(
                    icon = AFIcons.Trash,
                    contentDescription = "Remove ${repo.name}",
                    onClick = onRemove,
                    visualSize = 36.dp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            repo.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                AFSpinner(size = 14.dp)
                Spacer(Modifier.width(8.dp))
                AFText(
                    text = "Reading index",
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textLo,
                    onGlass = true
                )
            }

            repo.error != null -> {
                AFText(
                    text = repo.error.message,
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.failed,
                    onGlass = true
                )
                Row {
                    AFTextButton(label = "Retry", onClick = onRetry)
                    if (!repo.error.technical.isNullOrBlank()) {
                        AFTextButton(
                            label = if (showTechnical) "Hide technical details"
                            else "Technical details",
                            onClick = { showTechnical = !showTechnical }
                        )
                    }
                }
                if (showTechnical && !repo.error.technical.isNullOrBlank()) {
                    AFText(
                        text = repo.error.technical.orEmpty(),
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        onGlass = true,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            else -> AFText(
                text = "${repo.extensionCount} " +
                    if (repo.extensionCount == 1) "extension available" else "extensions available",
                style = AFTheme.type.caption,
                color = AFTheme.colors.ok,
                onGlass = true
            )
        }
    }
}

@Composable
private fun RepoIssueRow(name: String, message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            AFText(
                text = "Could not read $name",
                style = AFTheme.type.body,
                color = AFTheme.colors.textHi,
                onGlass = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AFText(
                text = message,
                style = AFTheme.type.caption,
                color = AFTheme.colors.failed,
                onGlass = true,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        AFTextButton(label = "Retry", onClick = onRetry)
    }
}

// ---------------------------------------------------------------- disabled

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
        GlassEmpty(
            title = "Nothing is disabled",
            body = "Disabled extensions keep their files and library entries but are " +
                "hidden from the categories and stop receiving updates until you enable " +
                "them again."
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
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "intro") {
            AFText(
                text = "Disabled sources are hidden from the categories and search. Their " +
                    "library entries stay readable only if the content was already imported.",
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo
            )
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                GlassEmpty(
                    title = "No disabled extension matches",
                    body = "Nothing disabled matches \"$needle\". Try a shorter search."
                )
            }
        } else {
            item(key = "list") {
                GlassPanel(header = "Disabled extensions") {
                    val sorted = visible.sortedBy { it.name.lowercase() }
                    sorted.forEachIndexed { index, item ->
                        ExtensionRow(
                            name = item.name,
                            subtitle = item.subtitle(),
                            iconUrl = item.iconUrl,
                            nsfw = item.nsfw,
                            status = "Disabled",
                            tone = RowTone.NEUTRAL,
                            onClick = { onOpen(item) },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AFTextButton(
                                        label = "Enable",
                                        onClick = { onEnable(item) }
                                    )
                                    AFIconButton(
                                        icon = AFIcons.Trash,
                                        contentDescription = "Remove ${item.name}",
                                        onClick = { onUninstall(item) },
                                        tint = AFTheme.colors.failed,
                                        visualSize = 36.dp
                                    )
                                }
                            }
                        )
                        if (index != sorted.lastIndex) AFDivider()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared rows

@Composable
private fun ExtensionRow(
    name: String,
    subtitle: String,
    iconUrl: String?,
    nsfw: Boolean,
    status: String?,
    tone: RowTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = AFTheme.colors
    val haptics = app.allfun.ui.glass.rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Open details for $name",
                onClick = {
                    haptics.tick()
                    onClick()
                }
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExtensionAvatar(iconUrl, name, Modifier.size(42.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AFText(
                    text = name,
                    style = AFTheme.type.body,
                    color = colors.textHi,
                    onGlass = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (nsfw) {
                    Spacer(Modifier.width(6.dp))
                    AFText(
                        text = "18+",
                        style = AFTheme.type.overline,
                        color = colors.failed,
                        onGlass = true
                    )
                }
            }
            AFText(
                text = subtitle,
                style = AFTheme.type.caption,
                color = colors.textLo,
                onGlass = true,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (status != null) {
                AFText(
                    text = status,
                    style = AFTheme.type.caption,
                    color = when (tone) {
                        RowTone.NEUTRAL -> colors.textLo
                        RowTone.ACCENT -> colors.accent
                        RowTone.FAILED -> colors.failed
                    },
                    onGlass = true,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun ExtensionAvatar(url: String?, name: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(AFTheme.colors.bg1),
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
    AFText(
        text = name.take(1).uppercase(),
        style = AFTheme.type.heading,
        color = AFTheme.colors.accent
    )
}

@Composable
private fun NoticePanel(header: String, body: String) {
    GlassPanel(header = header) {
        AFText(
            text = body,
            style = AFTheme.type.caption,
            color = AFTheme.colors.textLo,
            onGlass = true
        )
    }
}

// ---------------------------------------------------------------- forms

@Composable
private fun AddRepositorySheet(
    adding: Boolean,
    error: UserError?,
    onConfirm: (RepositoryKind, String) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(RepositoryKind.MANGA) }
    var url by remember { mutableStateOf("") }
    var showTechnical by remember { mutableStateOf(false) }

    GlassSheet(onDismiss = { if (!adding) onDismiss() }) {
        AFText(
            text = "Add repository",
            style = AFTheme.type.title,
            color = AFTheme.colors.textHi
        )
        Spacer(Modifier.height(6.dp))
        AFText(
            text = "AllFun reads the index first, so a broken or unrelated address is " +
                "rejected before anything is saved.",
            style = AFTheme.type.caption,
            color = AFTheme.colors.textLo
        )
        Spacer(Modifier.height(14.dp))
        GlassSegmented(
            options = RepositoryKind.entries.map {
                if (it == RepositoryKind.MANGA) "Manga" else "Novel"
            },
            selectedIndex = RepositoryKind.entries.indexOf(kind).coerceAtLeast(0),
            onSelect = { kind = RepositoryKind.entries[it] },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        GlassTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = if (kind == RepositoryKind.MANGA) {
                "https://example.com/index.json"
            } else {
                "https://example.com/plugins.min.json"
            },
            enabled = !adding,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            AFText(
                text = error.message,
                style = AFTheme.type.bodySmall,
                color = AFTheme.colors.failed
            )
            if (!error.technical.isNullOrBlank()) {
                AFTextButton(
                    label = if (showTechnical) "Hide technical details"
                    else "Technical details",
                    onClick = { showTechnical = !showTechnical },
                    color = AFTheme.colors.textLo
                )
                if (showTechnical) {
                    AFText(
                        text = error.technical.orEmpty(),
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                label = "Cancel",
                onClick = onDismiss,
                enabled = !adding,
                modifier = Modifier.weight(1f)
            )
            GlassPrimaryAction(
                label = if (adding) "Checking" else "Add",
                onClick = { onConfirm(kind, url) },
                loading = adding,
                enabled = !adding && url.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AddStremioSheet(
    adding: Boolean,
    error: UserError?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var showTechnical by remember { mutableStateOf(false) }

    GlassSheet(onDismiss = { if (!adding) onDismiss() }) {
        AFText(
            text = "Install Stremio addon",
            style = AFTheme.type.title,
            color = AFTheme.colors.textHi
        )
        Spacer(Modifier.height(6.dp))
        AFText(
            text = "Enter the addon's manifest URL, for example a Stremio addon page's " +
                "Install link, or the addon's base URL.",
            style = AFTheme.type.caption,
            color = AFTheme.colors.textLo
        )
        Spacer(Modifier.height(14.dp))
        GlassTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = "https://addon.example.com/manifest.json",
            enabled = !adding,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            AFText(
                text = error.message,
                style = AFTheme.type.bodySmall,
                color = AFTheme.colors.failed
            )
            if (!error.technical.isNullOrBlank()) {
                AFTextButton(
                    label = if (showTechnical) "Hide technical details"
                    else "Technical details",
                    onClick = { showTechnical = !showTechnical },
                    color = AFTheme.colors.textLo
                )
                if (showTechnical) {
                    AFText(
                        text = error.technical.orEmpty(),
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                label = "Cancel",
                onClick = onDismiss,
                enabled = !adding,
                modifier = Modifier.weight(1f)
            )
            GlassPrimaryAction(
                label = if (adding) "Checking" else "Install",
                onClick = { onConfirm(url) },
                loading = adding,
                enabled = !adding && url.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- details

@Composable
private fun ExtensionDetailsSheet(
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
            if (it == RepositoryKind.MANGA) ExtensionKind.MANGA.label
            else ExtensionKind.NOVEL.label
        }
        ?: "Extension"
    val version = installed?.version ?: entry?.version
    val developer = installed?.developer ?: entry?.developer
    val description = installed?.description ?: entry?.description
    val permissions = installed?.permissions.orEmpty()

    GlassSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExtensionAvatar(installed?.iconUrl ?: entry?.iconUrl, name, Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    AFText(
                        text = name,
                        style = AFTheme.type.title,
                        color = AFTheme.colors.textHi,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    AFText(
                        text = listOfNotNull(kindLabel, version?.let { "v$it" })
                            .joinToString(" · "),
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo
                    )
                }
                AFIconButton(
                    icon = AFIcons.Close,
                    contentDescription = "Close details",
                    onClick = onDismiss
                )
            }

            if (installed?.broken == true) {
                Spacer(Modifier.height(10.dp))
                AFText(
                    text = "The file for this extension is missing. Reinstall it, or remove " +
                        "the extension and its orphaned sources.",
                    style = AFTheme.type.bodySmall,
                    color = AFTheme.colors.failed
                )
            }

            developer?.let {
                Spacer(Modifier.height(12.dp))
                DetailLine("Developer", it)
            }
            description?.let {
                Spacer(Modifier.height(6.dp))
                DetailLine("About", it)
            }
            entry?.site?.let {
                Spacer(Modifier.height(6.dp))
                DetailLine("Site", it)
            }
            (installed?.repoUrl ?: entry?.repoUrl)?.let {
                Spacer(Modifier.height(6.dp))
                DetailLine("Repository", it)
            }
            if (entry != null && entry.sourceCount > 0) {
                Spacer(Modifier.height(6.dp))
                DetailLine("Sources", entry.sourceCount.toString())
            }
            installed?.location?.let { location ->
                if (installed.kind != ExtensionKind.MANGA) {
                    Spacer(Modifier.height(6.dp))
                    DetailLine("Location", location)
                }
            }
            installed?.let {
                Spacer(Modifier.height(6.dp))
                DetailLine("Status", if (it.enabled) "Enabled" else "Disabled")
            }

            Spacer(Modifier.height(14.dp))
            AFDivider()
            Spacer(Modifier.height(12.dp))
            AFOverline("Permissions")
            Spacer(Modifier.height(4.dp))
            if (permissions.isEmpty()) {
                AFText(
                    text = when {
                        installed?.kind == ExtensionKind.MANGA ->
                            "This package declares no Android permissions."
                        installed == null && entry != null ->
                            "Permissions are read from the installed package. AllFun " +
                                "verifies the publisher's signing key before loading manga " +
                                "extension code."
                        else -> "No permissions recorded for this source."
                    },
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textLo
                )
            } else {
                permissions.forEach { permission ->
                    AFText(
                        text = "• $permission",
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo
                    )
                }
            }
            if (installed?.kind == ExtensionKind.MANGA) {
                Spacer(Modifier.height(6.dp))
                AFText(
                    text = "Declared permissions are informational. Extension code runs " +
                        "inside AllFun with AllFun's own permissions; Android does not " +
                        "grant these declarations separately.",
                    style = AFTheme.type.caption,
                    color = AFTheme.colors.textLo,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry != null) {
                Spacer(Modifier.height(16.dp))
                val availableUpdate = installed == null || installed.update != null
                if (availableUpdate) {
                    GlassPrimaryAction(
                        label = if (installed == null) {
                            "Install ${entry.name}"
                        } else {
                            "Update to v${entry.version ?: "new version"}"
                        },
                        onClick = onInstall,
                        loading = busy,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    AFText(
                        text = "Installed from this repository · " +
                            "v${installed?.version ?: "unknown"}",
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.ok
                    )
                }
            }
            if (installed != null) {
                if (installed.canToggle) {
                    Spacer(Modifier.height(8.dp))
                    GlassButton(
                        label = if (installed.enabled) "Disable" else "Enable",
                        onClick = { onToggle(!installed.enabled) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                AFTextButton(
                    label = "Uninstall",
                    onClick = onUninstall,
                    color = AFTheme.colors.failed
                )
            }
            Spacer(Modifier.height(4.dp))
            AFTextButton(
                label = "Close",
                onClick = onDismiss,
                color = AFTheme.colors.textLo
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        AFText(
            text = label,
            style = AFTheme.type.caption,
            color = AFTheme.colors.textLo,
            modifier = Modifier.width(92.dp)
        )
        AFText(
            text = value,
            style = AFTheme.type.bodySmall,
            color = AFTheme.colors.textMid,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------- helpers

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
