package app.allfun.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.BuildConfig
import app.allfun.data.AppContainer
import app.allfun.data.settings.AccentPalette
import app.allfun.data.settings.AppTextSize
import app.allfun.data.settings.CornerStyle
import app.allfun.data.settings.GlassQuality
import app.allfun.data.settings.LibraryGrid
import app.allfun.data.settings.LibrarySort
import app.allfun.data.settings.LibraryView
import app.allfun.data.settings.MangaFit
import app.allfun.data.settings.NovelTheme
import app.allfun.data.settings.ReaderMode
import app.allfun.data.settings.ReadingDirection
import app.allfun.data.settings.SeekStep
import app.allfun.data.settings.Settings
import app.allfun.data.settings.ThemeMode
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFDivider
import app.allfun.ui.glass.AFIcon
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
import app.allfun.ui.glass.GlassProgress
import app.allfun.ui.glass.GlassSegmented
import app.allfun.ui.glass.GlassSlider
import app.allfun.ui.glass.GlassSurface
import app.allfun.ui.glass.GlassSwitch
import app.allfun.ui.glass.GlassTextField
import app.allfun.ui.glass.GlassTier
import app.allfun.ui.glass.GlassToastHost
import app.allfun.ui.glass.GlassTopCapsule
import app.allfun.ui.glass.LocalGlassNavState
import app.allfun.ui.glass.ScreenHeader
import app.allfun.ui.glass.Section
import app.allfun.ui.glass.TopScrim
import app.allfun.ui.glass.WorldBackdrop
import app.allfun.ui.glass.rememberGlassToastState
import app.allfun.ui.glass.rememberHaptics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Settings is a hub, not one long page: each row opens a short screen for one kind of
 * preference, with the current value as the subtitle. Groups are single frosted panels;
 * rows inside them never get a second glass layer of their own.
 */
private enum class SettingsPage(val label: String, val blurb: String) {
    APPEARANCE("Appearance", "Theme, glass, colour and motion"),
    LIBRARY("Library", "How your shelf is drawn"),
    READING("Reading", "Manga pages and novel typography"),
    PLAYBACK("Playback", "Video player behaviour"),
    DOWNLOADS("Downloads", "Queue and connection rules"),
    STORAGE("Storage", "Cache, extensions and database"),
    NETWORK("Network", "Connection test and request timeouts"),
    BACKUP("Backup", "Export or restore your data"),
    ABOUT("About", "Version, licenses and repositories")
}

private data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val page: SettingsPage? = null,
    val action: (() -> Unit)? = null,
    val keywords: String = ""
)

private data class SettingsGroup(val title: String, val entries: List<SettingsEntry>)

private val NAV_RESERVE = 140.dp

@Composable
fun SettingsScreen(
    container: AppContainer,
    onManageExtensions: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onOpenDownloads: () -> Unit = {}
) {
    val vm = rememberViewModel { SettingsViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val toast = rememberGlassToastState()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val hubState = rememberLazyListState()
    val pageState = rememberLazyListState()
    val navState = LocalGlassNavState.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(Unit) {
        vm.messages.collect { toast.show(it) }
    }
    LaunchedEffect(hubState) {
        snapshotFlow { hubState.firstVisibleItemIndex to hubState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> navState.onScroll(index, offset) }
    }
    LaunchedEffect(pageState) {
        snapshotFlow { pageState.firstVisibleItemIndex to pageState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> navState.onScroll(index, offset) }
    }
    LaunchedEffect(page) {
        pageState.scrollToItem(0)
        navState.show()
    }
    DisposableEffect(Unit) {
        onDispose { navState.show() }
    }

    val suggestedName = remember {
        "allfun-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImport = uri }

    BackHandler(enabled = page != null) { page = null }

    WorldBackdrop(section = Section.SYSTEM) {
        Box(Modifier.fillMaxSize()) {
            if (page == null) {
                SettingsHub(
                    settings = settings,
                    busy = busy,
                    listState = hubState,
                    topPadding = statusTop + 72.dp,
                    onOpenPage = { page = it },
                    onManageExtensions = onManageExtensions,
                    onOpenUpdates = onOpenUpdates
                )
            } else {
                SettingsPageContent(
                    page = page!!,
                    vm = vm,
                    settings = settings,
                    storage = storage,
                    busy = busy,
                    listState = pageState,
                    topPadding = statusTop + 72.dp,
                    onOpenDownloads = onOpenDownloads,
                    onExport = { exportLauncher.launch(suggestedName) },
                    onImport = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                )
            }

            TopScrim(Modifier.align(Alignment.TopCenter))
            BottomScrim(Modifier.align(Alignment.BottomCenter))

            GlassTopCapsule(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                onBack = if (page != null) ({ page = null }) else null
            ) {
                AFText(
                    text = page?.label ?: "Settings",
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                if (page == null) {
                    AFText(
                        text = BuildConfig.VERSION_NAME,
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

    pendingImport?.let { uri ->
        GlassDialog(
            title = "Restore this backup?",
            body = "Titles in the file are added, or replaced when they have the same id. " +
                "Nothing currently in your library is deleted. Settings and repository URLs " +
                "are overwritten by the file's values.",
            confirmLabel = "Restore",
            onConfirm = {
                pendingImport = null
                vm.importBackup(uri)
            },
            onDismiss = { pendingImport = null }
        )
    }
}

@Composable
private fun SettingsHub(
    settings: Settings,
    busy: String?,
    listState: LazyListState,
    topPadding: androidx.compose.ui.unit.Dp,
    onOpenPage: (SettingsPage) -> Unit,
    onManageExtensions: () -> Unit,
    onOpenUpdates: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim().lowercase(Locale.ROOT)
    val groups = settingsGroups(settings, onManageExtensions, onOpenUpdates).mapNotNull { group ->
        group.copy(
            entries = group.entries.filter { entry ->
                needle.isBlank() ||
                    entry.title.lowercase(Locale.ROOT).contains(needle) ||
                    entry.subtitle.lowercase(Locale.ROOT).contains(needle) ||
                    entry.keywords.contains(needle)
            }
        ).takeIf { it.entries.isNotEmpty() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topPadding,
            bottom = NAV_RESERVE
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item(key = "header") {
            Column {
                ScreenHeader(
                    title = "Settings",
                    subtitle = "AllFun ${BuildConfig.VERSION_NAME} · saved as you change them",
                    overline = "AllFun"
                )
                Spacer(Modifier.height(18.dp))
                GlassTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search settings",
                    leading = AFIcons.Search,
                    trailing = {
                        if (query.isNotEmpty()) {
                            AFTextButton(
                                label = "Clear",
                                onClick = { query = "" },
                                color = AFTheme.colors.textLo
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (busy != null) {
            item(key = "busy") { BusyRow(busy) }
        }

        if (groups.isEmpty()) {
            item(key = "empty") {
                GlassEmpty(
                    title = "No settings match \"$query\"",
                    body = "Try a shorter word, such as theme, reader, storage or backup.",
                    actionLabel = "Clear search",
                    onAction = { query = "" }
                )
            }
        }

        groups.forEach { group ->
            item(key = "group:" + group.title) {
                GlassPanel(header = group.title) {
                    group.entries.forEachIndexed { index, entry ->
                        if (index > 0) {
                            AFDivider()
                        }
                        SettingsEntryRow(
                            entry = entry,
                            onClick = {
                                entry.page?.let(onOpenPage) ?: entry.action?.invoke()
                            }
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            AFText(
                text = "Glass ${settings.glassQuality.label} · ${settings.themeMode.label()}",
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsEntryRow(
    entry: SettingsEntry,
    onClick: () -> Unit
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = entry.title
            ) {
                haptics.tick()
                onClick()
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AFIcon(entry.icon, null, tint = AFTheme.colors.textMid, size = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            AFText(
                text = entry.title,
                style = AFTheme.type.body,
                color = AFTheme.colors.textHi,
                onGlass = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AFText(
                text = entry.subtitle,
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo,
                onGlass = true,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        AFIcon(AFIcons.ChevronRight, null, tint = AFTheme.colors.textLo, size = 18.dp)
    }
}

private fun settingsGroups(
    settings: Settings,
    onManageExtensions: () -> Unit,
    onOpenUpdates: () -> Unit
): List<SettingsGroup> = listOf(
    SettingsGroup(
        title = "Look & feel",
        entries = listOf(
            SettingsEntry(
                title = "Appearance",
                subtitle = "${settings.themeMode.label()} · ${settings.accent.label} · " +
                    "Glass ${settings.glassQuality.label}",
                icon = AFIcons.Sparkle,
                page = SettingsPage.APPEARANCE,
                keywords = "theme dark light system colours color accent wallpaper motion " +
                    "animation glass corners shape text size"
            ),
            SettingsEntry(
                title = "Library",
                subtitle = "${settings.libraryView.label()} · ${settings.librarySort.label} · " +
                    settings.libraryGrid.label,
                icon = AFIcons.Bookmark,
                page = SettingsPage.LIBRARY,
                keywords = "shelf grid list sort order titles poster size"
            )
        )
    ),
    SettingsGroup(
        title = "Reading & playback",
        entries = listOf(
            SettingsEntry(
                title = "Reading",
                subtitle = "${settings.readerMode.label()} · ${settings.mangaFit.label}",
                icon = AFIcons.Book,
                page = SettingsPage.READING,
                keywords = "manga novel reader pages fit direction font line spacing theme"
            ),
            SettingsEntry(
                title = "Playback",
                subtitle = "Resume ${settings.resumePlayback.onOff()} · " +
                    "Subtitles ${settings.subtitlesEnabled.onOff()}",
                icon = AFIcons.Play,
                page = SettingsPage.PLAYBACK,
                keywords = "video film episode player subtitles resume"
            )
        )
    ),
    SettingsGroup(
        title = "Sources & storage",
        entries = listOf(
            SettingsEntry(
                title = "Extensions",
                subtitle = "Sources, add-ons and plugins",
                icon = AFIcons.Stack,
                action = onManageExtensions,
                keywords = "repositories stremio jellyfin source addon plugin install update"
            ),
            SettingsEntry(
                title = "Downloads",
                subtitle = "Wi-Fi only ${settings.downloadWifiOnly.onOff()} · " +
                    "${settings.concurrentDownloads} at a time",
                icon = AFIcons.Download,
                page = SettingsPage.DOWNLOADS,
                keywords = "offline download queue wifi storage chapters parallel"
            ),
            SettingsEntry(
                title = "Storage",
                subtitle = "Cache, extensions and database use",
                icon = AFIcons.Folder,
                page = SettingsPage.STORAGE,
                keywords = "cache clear space bytes database temporary"
            ),
            SettingsEntry(
                title = "Network",
                subtitle = "Connection test and request timeouts",
                icon = AFIcons.Wifi,
                page = SettingsPage.NETWORK,
                keywords = "connection repository timeout internet proxy"
            )
        )
    ),
    SettingsGroup(
        title = "Data & about",
        entries = listOf(
            SettingsEntry(
                title = "Backup",
                subtitle = "Export or restore a JSON backup",
                icon = AFIcons.Share,
                page = SettingsPage.BACKUP,
                keywords = "export import restore json library progress"
            ),
            SettingsEntry(
                title = "App updates",
                subtitle = "Check GitHub releases for a newer AllFun",
                icon = AFIcons.Refresh,
                action = onOpenUpdates,
                keywords = "update version release apk github"
            ),
            SettingsEntry(
                title = "About",
                subtitle = "AllFun ${BuildConfig.VERSION_NAME}",
                icon = AFIcons.Info,
                page = SettingsPage.ABOUT,
                keywords = "version credits licenses repositories source code"
            )
        )
    )
)

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    vm: SettingsViewModel,
    settings: Settings,
    storage: SettingsViewModel.StorageStats?,
    busy: String?,
    listState: LazyListState,
    topPadding: androidx.compose.ui.unit.Dp,
    onOpenDownloads: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topPadding,
            bottom = NAV_RESERVE
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "page-header") {
            ScreenHeader(
                title = page.label,
                subtitle = page.blurb,
                overline = "Settings"
            )
        }
        if (busy != null) {
            item(key = "busy") { BusyRow(busy) }
        }
        item(key = "page-body") {
            SettingsPageBody(
                page = page,
                vm = vm,
                settings = settings,
                storage = storage,
                busy = busy,
                onOpenDownloads = onOpenDownloads,
                onExport = onExport,
                onImport = onImport
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPageBody(
    page: SettingsPage,
    vm: SettingsViewModel,
    settings: Settings,
    storage: SettingsViewModel.StorageStats?,
    busy: String?,
    onOpenDownloads: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        when (page) {
            SettingsPage.APPEARANCE -> {
                SettingsSection("Theme") {
                    GlassSegmented(
                        options = ThemeMode.entries.map { it.shortLabel() },
                        selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                        onSelect = { vm.setTheme(ThemeMode.entries[it]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    SwitchRow(
                        title = "Material You colours",
                        subtitle = "Tint the neutral world from your wallpaper. Section accents " +
                            "stay on the palette below.",
                        checked = settings.dynamicColor,
                        onChange = vm::setDynamicColor
                    )
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        title = "Pitch black",
                        subtitle = "Draw dark screens on OLED black. Light theme is unaffected.",
                        checked = settings.amoledBlack,
                        onChange = vm::setAmoledBlack
                    )
                }
                SettingsSection("Accent") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccentPalette.entries.forEach { palette ->
                            GlassChip(
                                label = palette.label,
                                selected = settings.accent == palette,
                                onClick = { vm.setAccent(palette) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    PanelNote(
                        if (settings.dynamicColor) {
                            "Material You handles the background; this accent still marks the " +
                                "current section, actions and progress."
                        } else {
                            "Tints buttons, highlights and the navigation indicator."
                        }
                    )
                }
                SettingsSection("Glass level") {
                    GlassSegmented(
                        options = GlassQuality.entries.map { it.label },
                        selectedIndex = GlassQuality.entries.indexOf(settings.glassQuality),
                        onSelect = { vm.setGlassQuality(GlassQuality.entries[it]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    PanelNote(
                        "High adds the 2% grain and the one-time specular sweep. Standard keeps " +
                            "the lens and rim. Lite drops both for older devices; every level " +
                            "still looks like glass."
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassLevelPreview(settings.glassQuality)
                }
                SettingsSection("Shape & text") {
                    SettingLabel("Corner style")
                    ChoiceChips(
                        options = CornerStyle.entries.toList(),
                        selected = settings.cornerStyle,
                        label = { it.label },
                        onSelect = vm::setCornerStyle
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingLabel("Text size")
                    ChoiceChips(
                        options = AppTextSize.entries.toList(),
                        selected = settings.appTextSize,
                        label = { it.label },
                        onSelect = vm::setAppTextSize
                    )
                    Spacer(Modifier.height(8.dp))
                    PanelNote(
                        "Scales the app's text on top of Android's own font size. System keeps " +
                            "whatever you set in display settings."
                    )
                }
                SettingsSection("Accessibility") {
                    SwitchRow(
                        title = "Reduce motion",
                        subtitle = "Only crossfades remain; glass springs, the sweep and the " +
                            "nav blob stop moving.",
                        checked = settings.reduceMotion,
                        onChange = vm::setReduceMotion
                    )
                    Spacer(Modifier.height(10.dp))
                    PanelNote(
                        "AllFun follows your system font size. Text wraps instead of clipping at " +
                            "large sizes, controls keep 48 dp touch targets, icons have spoken " +
                            "labels, and colour is never the only way status is shown."
                    )
                }
            }

            SettingsPage.LIBRARY -> {
                SettingsSection("Library") {
                    SettingLabel("Layout")
                    GlassSegmented(
                        options = LibraryView.entries.map { it.label() },
                        selectedIndex = LibraryView.entries.indexOf(settings.libraryView),
                        onSelect = { vm.setLibraryView(LibraryView.entries[it]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingLabel("Poster size")
                    ChoiceChips(
                        options = LibraryGrid.entries.toList(),
                        selected = settings.libraryGrid,
                        label = { it.label },
                        onSelect = vm::setLibraryGrid
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingLabel("Sort order")
                    ChoiceChips(
                        options = LibrarySort.entries.toList(),
                        selected = settings.librarySort,
                        label = { it.label },
                        onSelect = vm::setLibrarySort
                    )
                    Spacer(Modifier.height(10.dp))
                    PanelNote(
                        "Titles you started show their progress on the card itself, so the shelf " +
                            "has no separate Continue row."
                    )
                }
            }

            SettingsPage.READING -> {
                SettingsSection("Manga") {
                    SettingLabel("Page layout")
                    GlassSegmented(
                        options = ReaderMode.entries.map { it.label() },
                        selectedIndex = ReaderMode.entries.indexOf(settings.readerMode),
                        onSelect = { vm.setReaderMode(ReaderMode.entries[it]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingLabel("Fit pages")
                    ChoiceChips(
                        options = MangaFit.entries.toList(),
                        selected = settings.mangaFit,
                        label = { it.label },
                        onSelect = vm::setMangaFit
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingLabel("Page order")
                    ChoiceChips(
                        options = ReadingDirection.entries.toList(),
                        selected = settings.readingDirection,
                        label = {
                            if (it == ReadingDirection.LTR) "Left to right" else "Right to left"
                        },
                        onSelect = vm::setReadingDirection
                    )
                }
                SettingsSection("Reader") {
                    SwitchRow(
                        title = "Keep the screen awake",
                        subtitle = "Stops the display sleeping while a chapter is open",
                        checked = settings.readerKeepScreenOn,
                        onChange = vm::setReaderKeepScreenOn
                    )
                }
                SettingsSection("Novels") {
                    SliderRow(
                        title = "Text size",
                        value = settings.novelFontSize.toFloat(),
                        valueRange = 70f..180f,
                        valueLabel = { "${it.roundToInt()}%" },
                        onValueChangeFinished = { vm.setFontSize(it.roundToInt()) }
                    )
                    SliderRow(
                        title = "Line spacing",
                        value = settings.novelLineSpacing,
                        valueRange = 1.2f..2.4f,
                        valueLabel = { String.format(Locale.US, "%.1f×", it) },
                        onValueChangeFinished = {
                            vm.setLineSpacing((it * 10f).roundToInt() / 10f)
                        }
                    )
                    SettingLabel("Reader theme")
                    ChoiceChips(
                        options = NovelTheme.entries.toList(),
                        selected = settings.novelTheme,
                        label = { it.label },
                        onSelect = vm::setNovelTheme
                    )
                }
            }

            SettingsPage.PLAYBACK -> {
                SettingsSection("Playback") {
                    SwitchRow(
                        title = "Resume where you stopped",
                        subtitle = "Continue video from the last saved position",
                        checked = settings.resumePlayback,
                        onChange = vm::setResumePlayback
                    )
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        title = "Show subtitles",
                        subtitle = "Turn on subtitle tracks a stream provides",
                        checked = settings.subtitlesEnabled,
                        onChange = vm::setSubtitlesEnabled
                    )
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        title = "Play the next episode",
                        subtitle = "Continue to the next episode when one finishes",
                        checked = settings.autoPlayNext,
                        onChange = vm::setAutoPlayNext
                    )
                }
                SettingsSection("Player controls") {
                    SettingLabel("Skip amount")
                    ChoiceChips(
                        options = SeekStep.entries.toList(),
                        selected = settings.seekStep,
                        label = { it.label },
                        onSelect = vm::setSeekStep
                    )
                    Spacer(Modifier.height(8.dp))
                    PanelNote(
                        "The player's rewind and forward buttons jump this far, and double-tapping " +
                            "the left or right half of the video does the same."
                    )
                }
            }

            SettingsPage.DOWNLOADS -> {
                SettingsSection("Downloading") {
                    SwitchRow(
                        title = "Only on Wi-Fi or Ethernet",
                        subtitle = "Queued downloads wait for an unmetered connection",
                        checked = settings.downloadWifiOnly,
                        onChange = vm::setDownloadWifiOnly
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingLabel("Downloads at the same time")
                    ChoiceChips(
                        options = (1..4).toList(),
                        selected = settings.concurrentDownloads,
                        label = { "$it" },
                        onSelect = vm::setConcurrentDownloads
                    )
                }
                SettingsSection("Queue") {
                    PanelNote(
                        "Pause, reorder, retry or cancel individual downloads from the queue. " +
                            "Completed files stay on this device and can be played without a " +
                            "connection."
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassButton(
                        label = "Open download queue",
                        onClick = onOpenDownloads,
                        icon = AFIcons.Download,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SettingsPage.STORAGE -> {
                SettingsSection("Storage") {
                    val stats = storage
                    if (stats == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AFSpinner(size = 16.dp)
                            Spacer(Modifier.width(10.dp))
                            AFText(
                                text = "Calculating storage use…",
                                style = AFTheme.type.bodySmall,
                                color = AFTheme.colors.textLo,
                                onGlass = true
                            )
                        }
                    } else {
                        StatRow("Temporary files", SettingsViewModel.formatBytes(stats.cacheBytes))
                        StatRow(
                            "Installed extensions",
                            SettingsViewModel.formatBytes(stats.extensionBytes)
                        )
                        StatRow(
                            "Library database",
                            SettingsViewModel.formatBytes(stats.databaseBytes)
                        )
                        AFDivider(Modifier.padding(vertical = 8.dp))
                        StatRow("Total", SettingsViewModel.formatBytes(stats.totalBytes))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            label = "Clear cache",
                            onClick = vm::clearCache,
                            enabled = busy == null
                        )
                        AFTextButton(
                            label = "Refresh",
                            onClick = vm::refreshStorage,
                            enabled = busy == null
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PanelNote(
                        "Clearing cached files removes downloaded images and repository scratch " +
                            "files. Installed extensions, books and your library are kept."
                    )
                }
            }

            SettingsPage.NETWORK -> {
                SettingsSection("Network") {
                    PanelNote(
                        "AllFun talks to sources directly from this device; there is no account " +
                            "or proxy server in between. Requests time out after 20 seconds to " +
                            "connect and 30 seconds to read."
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassButton(
                        label = "Test repository connection",
                        onClick = vm::testConnection,
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    PanelNote(
                        "The test downloads the public Keiyoushi manga index and reports how many " +
                            "extensions it lists."
                    )
                }
            }

            SettingsPage.BACKUP -> {
                SettingsSection("Backup") {
                    PanelNote(
                        "A backup is a JSON file with your settings, repository URLs, library, " +
                            "chapters or episodes, and reading/watching progress."
                    )
                    Spacer(Modifier.height(8.dp))
                    PanelNote(
                        "Extension code and Jellyfin/Stremio credentials are not included. " +
                            "Reinstall extensions and sign in again after restoring on another " +
                            "device."
                    )
                    Spacer(Modifier.height(16.dp))
                    GlassPrimaryAction(
                        label = "Export backup",
                        onClick = onExport,
                        icon = AFIcons.Share,
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        label = "Restore from a file",
                        onClick = onImport,
                        icon = AFIcons.Refresh,
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SettingsPage.ABOUT -> {
                SettingsSection("About") {
                    AFText(
                        text = "AllFun ${BuildConfig.VERSION_NAME}",
                        style = AFTheme.type.title,
                        color = AFTheme.colors.textHi,
                        onGlass = true
                    )
                    Spacer(Modifier.height(8.dp))
                    PanelNote(
                        "One library for manga, light novels and video. Kotlin and Jetpack " +
                            "Compose, Room for the shelf, Media3 for playback, a WebView novel " +
                            "reader, and a source layer that runs Jellyfin, Stremio addons, " +
                            "Tachiyomi/Komikku extensions and LNReader plugins."
                    )
                    Spacer(Modifier.height(10.dp))
                    PanelNote(
                        "Built-in repositories: Keiyoushi (manga extensions) and LNReader (novel " +
                            "plugins). Both are public indexes downloaded on request."
                    )
                    Spacer(Modifier.height(10.dp))
                    PanelNote(
                        "Not implemented: tracker sync (MAL, AniList) and declarative remote JSON " +
                            "sources."
                    )
                }
            }
        }
    }
}

@Composable
private fun BusyRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AFSpinner(size = 16.dp)
        Spacer(Modifier.width(10.dp))
        AFText(
            text = label,
            style = AFTheme.type.label,
            color = AFTheme.colors.textMid
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(header = title, content = content)
}

@Composable
private fun SettingLabel(text: String) {
    AFText(
        text = text,
        style = AFTheme.type.body,
        color = AFTheme.colors.textHi,
        onGlass = true
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PanelNote(text: String) {
    AFText(
        text = text,
        style = AFTheme.type.bodySmall,
        color = AFTheme.colors.textLo,
        onGlass = true
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            GlassChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onChange
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            AFText(
                text = title,
                style = AFTheme.type.body,
                color = AFTheme.colors.textHi,
                onGlass = true
            )
            AFText(
                text = subtitle,
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo,
                onGlass = true
            )
        }
        Spacer(Modifier.width(12.dp))
        // The row owns the toggle semantics, so the switch is not a second target.
        GlassSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onValueChangeFinished: (Float) -> Unit
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(value) { dragged = null }
    val shown = dragged ?: value
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AFText(
                text = title,
                style = AFTheme.type.body,
                color = AFTheme.colors.textHi,
                onGlass = true,
                modifier = Modifier.weight(1f)
            )
            AFText(
                text = valueLabel(shown),
                style = AFTheme.type.numeric,
                color = AFTheme.colors.accent
            )
        }
        Spacer(Modifier.height(4.dp))
        GlassSlider(
            value = shown,
            valueRange = valueRange,
            onValueChange = { dragged = it },
            onValueChangeFinished = { dragged?.let(onValueChangeFinished) }
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AFText(
            text = label,
            style = AFTheme.type.bodySmall,
            color = AFTheme.colors.textMid,
            onGlass = true,
            modifier = Modifier.weight(1f)
        )
        AFText(
            text = value,
            style = AFTheme.type.numeric,
            color = AFTheme.colors.textHi,
            onGlass = true
        )
    }
}

/**
 * A static sample rendered with the live theme, so the glass level and accent can be judged
 * before leaving the page. Nothing here is interactive.
 */
@Composable
private fun GlassLevelPreview(quality: GlassQuality) {
    val colors = AFTheme.colors
    GlassSurface(
        tier = GlassTier.INSET,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        pressScale = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(44.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.accent.copy(alpha = 0.65f),
                                colors.bg2
                            )
                        )
                    )
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                AFText(
                    text = "Live preview",
                    style = AFTheme.type.label,
                    color = colors.textHi,
                    onGlass = true
                )
                AFText(
                    text = "Glass ${quality.label} · Aa 123",
                    style = AFTheme.type.caption,
                    color = colors.textLo,
                    onGlass = true
                )
                Spacer(Modifier.height(8.dp))
                GlassProgress(progress = 0.62f, height = 3.dp)
            }
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System theme"
    ThemeMode.DARK -> "Dark theme"
    ThemeMode.LIGHT -> "Light theme"
}

private fun ThemeMode.shortLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.DARK -> "Dark"
    ThemeMode.LIGHT -> "Light"
}

private fun LibraryView.label(): String = if (this == LibraryView.GRID) "Grid" else "List"

private fun ReaderMode.label(): String =
    if (this == ReaderMode.VERTICAL) "Vertical scroll" else "Paged"

private fun Boolean.onOff(): String = if (this) "on" else "off"
