package app.allfun.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.BuildConfig
import app.allfun.data.AppContainer
import app.allfun.data.settings.AccentPalette
import app.allfun.data.settings.AppTextSize
import app.allfun.data.settings.CornerStyle
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
import app.allfun.ui.components.pressScale
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.components.staggeredReveal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Settings is a hub, not one long page: each row opens a short screen for one kind of
 * preference, with the current value as the subtitle. The structure follows Komikku's
 * settings (category list with icons, subtitle summaries and a search), adapted to the
 * preferences AllFun actually has.
 */
private enum class SettingsPage(val label: String) {
    APPEARANCE("Appearance"),
    LIBRARY("Library"),
    READING("Reading"),
    PLAYBACK("Playback"),
    DOWNLOADS("Downloads"),
    STORAGE("Storage"),
    NETWORK("Network"),
    BACKUP("Backup"),
    ABOUT("About")
}

private data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val page: SettingsPage? = null,
    val action: (() -> Unit)? = null,
    val keywords: String = ""
)

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
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // Kept above the hub so coming back from a sub-screen returns to the same row instead
    // of jumping to the top of the list.
    val hubState = rememberLazyListState()

    LaunchedEffect(Unit) {
        vm.messages.collect { message -> snackbar.showSnackbar(message) }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        // The root already pads for the bottom bar; insets are applied once, on the list.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) { innerPadding ->
        if (page == null) {
            SettingsHub(
                settings = settings,
                busy = busy,
                listState = hubState,
                onOpenPage = { page = it },
                onManageExtensions = onManageExtensions,
                onOpenUpdates = onOpenUpdates,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            )
        } else {
            SettingsPageContent(
                page = page!!,
                vm = vm,
                settings = settings,
                storage = storage,
                busy = busy,
                onBack = { page = null },
                onOpenDownloads = onOpenDownloads,
                onExport = { exportLauncher.launch(suggestedName) },
                onImport = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            )
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Titles in the file are added, or replaced when they have the same id. " +
                        "Nothing currently in your library is deleted. Settings and repository " +
                        "URLs are overwritten by the file's values."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    vm.importBackup(uri)
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsHub(
    settings: Settings,
    busy: String?,
    listState: LazyListState,
    onOpenPage: (SettingsPage) -> Unit,
    onManageExtensions: () -> Unit,
    onOpenUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val entries = settingsEntries(settings, onManageExtensions, onOpenUpdates)
    val needle = query.trim().lowercase(Locale.ROOT)
    val visible = entries.filter { entry ->
        needle.isBlank() ||
            entry.title.lowercase(Locale.ROOT).contains(needle) ||
            entry.subtitle.lowercase(Locale.ROOT).contains(needle) ||
            entry.keywords.contains(needle)
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AllFun ${BuildConfig.VERSION_NAME} · saved as you change them",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search settings") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (busy != null) {
            item { BusyBar(busy) }
        }

        if (visible.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                    Text("No settings match \"$query\"", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Try a shorter word, such as theme, reader, storage or backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column {
                        visible.forEachIndexed { index, entry ->
                            if (index > 0) {
                                HorizontalDivider(Modifier.padding(start = 68.dp))
                            }
                            SettingsEntryRow(
                                entry = entry,
                                onClick = {
                                    entry.page?.let(onOpenPage) ?: entry.action?.invoke()
                                },
                                modifier = Modifier.staggeredReveal(index)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEntryRow(
    entry: SettingsEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun settingsEntries(
    settings: Settings,
    onManageExtensions: () -> Unit,
    onOpenUpdates: () -> Unit
): List<SettingsEntry> = listOf(
    SettingsEntry(
        title = "Appearance",
        subtitle = "${settings.themeMode.label()} · " +
            (if (settings.dynamicColor) "Material You" else settings.accent.label) +
            " · ${settings.cornerStyle.label}",
        icon = Icons.Outlined.Palette,
        page = SettingsPage.APPEARANCE,
        keywords = "theme dark light system colours color accent wallpaper motion animation " +
            "corners shape text size"
    ),
    SettingsEntry(
        title = "Library",
        subtitle = "${settings.libraryView.label()} · ${settings.librarySort.label} · " +
            settings.libraryGrid.label,
        icon = Icons.Outlined.CollectionsBookmark,
        page = SettingsPage.LIBRARY,
        keywords = "shelf grid list sort order titles poster size"
    ),
    SettingsEntry(
        title = "Reading",
        subtitle = "${settings.readerMode.label()} · ${settings.mangaFit.label}",
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        page = SettingsPage.READING,
        keywords = "manga novel reader pages fit direction font line spacing theme"
    ),
    SettingsEntry(
        title = "Playback",
        subtitle = "Resume ${settings.resumePlayback.onOff()} · " +
            "Subtitles ${settings.subtitlesEnabled.onOff()}",
        icon = Icons.Outlined.PlayCircle,
        page = SettingsPage.PLAYBACK,
        keywords = "video film episode player subtitles resume"
    ),
    SettingsEntry(
        title = "Downloads",
        subtitle = "Wi-Fi only ${settings.downloadWifiOnly.onOff()} · " +
            "${settings.concurrentDownloads} at a time",
        icon = Icons.Outlined.Download,
        page = SettingsPage.DOWNLOADS,
        keywords = "offline download queue wifi storage chapters parallel"
    ),
    SettingsEntry(
        title = "Extensions",
        subtitle = "Sources, add-ons and plugins",
        icon = Icons.Outlined.Extension,
        action = onManageExtensions,
        keywords = "repositories stremio jellyfin source addon plugin install update"
    ),
    SettingsEntry(
        title = "Storage",
        subtitle = "Cache, extensions and database use",
        icon = Icons.Outlined.Storage,
        page = SettingsPage.STORAGE,
        keywords = "cache clear space bytes database temporary"
    ),
    SettingsEntry(
        title = "Network",
        subtitle = "Connection test and request timeouts",
        icon = Icons.Outlined.Wifi,
        page = SettingsPage.NETWORK,
        keywords = "connection repository timeout internet proxy"
    ),
    SettingsEntry(
        title = "Backup",
        subtitle = "Export or restore a JSON backup",
        icon = Icons.Outlined.Save,
        page = SettingsPage.BACKUP,
        keywords = "export import restore json library progress"
    ),
    SettingsEntry(
        title = "App updates",
        subtitle = "Check GitHub releases for a newer AllFun",
        icon = Icons.Outlined.SystemUpdate,
        action = onOpenUpdates,
        keywords = "update version release apk github"
    ),
    SettingsEntry(
        title = "About",
        subtitle = "AllFun ${BuildConfig.VERSION_NAME}",
        icon = Icons.Outlined.Info,
        page = SettingsPage.ABOUT,
        keywords = "version credits licenses repositories source code"
    )
)

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    vm: SettingsViewModel,
    settings: Settings,
    storage: SettingsViewModel.StorageStats?,
    busy: String?,
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            SettingsPageHeader(title = page.label, onBack = onBack)
        }
        if (busy != null) {
            item { BusyBar(busy) }
        }
        when (page) {
            SettingsPage.APPEARANCE -> item {
                Column {
                    SettingsSection("Appearance") {
                        SettingLabel("Theme")
                        ChoiceChips(
                            options = ThemeMode.entries.toList(),
                            selected = settings.themeMode,
                            label = { it.label() },
                            onSelect = vm::setTheme
                        )
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            title = "Material You colours",
                            subtitle = "Use the wallpaper palette on Android 12 and later",
                            checked = settings.dynamicColor,
                            onChange = vm::setDynamicColor
                        )
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            title = "Pitch black",
                            subtitle = "Draw dark screens on OLED black. Light theme is unaffected.",
                            checked = settings.amoledBlack,
                            onChange = vm::setAmoledBlack
                        )
                        Spacer(Modifier.height(14.dp))
                        SettingLabel("Accent colour")
                        ChoiceChips(
                            options = AccentPalette.entries.toList(),
                            selected = settings.accent,
                            label = { it.label },
                            onSelect = vm::setAccent
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (settings.dynamicColor) {
                                "Material You is on, so the wallpaper palette wins. Turn it off " +
                                    "above to use this accent."
                            } else {
                                "Tints buttons, highlights and the navigation indicator."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Scales the app's text on top of Android's own font size. " +
                                "System keeps whatever you set in display settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SettingsSection("Accessibility") {
                        SwitchRow(
                            title = "Reduce motion",
                            subtitle = "Turns off screen cross-fades and other non-essential animation",
                            checked = settings.reduceMotion,
                            onChange = vm::setReduceMotion
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "AllFun follows your system font size. Text wraps instead of " +
                                "clipping at large sizes, buttons keep at least 48 dp touch " +
                                "targets, icons have spoken labels, and colour is never the only " +
                                "way status is shown. Change text size in Android's display " +
                                "settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsPage.LIBRARY -> item {
                SettingsSection("Library") {
                    SettingLabel("Layout")
                    ChoiceChips(
                        options = LibraryView.entries.toList(),
                        selected = settings.libraryView,
                        label = { it.label() },
                        onSelect = vm::setLibraryView
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingLabel("Poster size")
                    ChoiceChips(
                        options = LibraryGrid.entries.toList(),
                        selected = settings.libraryGrid,
                        label = { it.label },
                        onSelect = vm::setLibraryGrid
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingLabel("Sort order")
                    ChoiceChips(
                        options = LibrarySort.entries.toList(),
                        selected = settings.librarySort,
                        label = { it.label },
                        onSelect = vm::setLibrarySort
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Titles you started show their progress on the card itself, so " +
                            "the shelf has no separate Continue row.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsPage.READING -> item {
                Column {
                    SettingsSection("Manga") {
                        SettingLabel("Page layout")
                        ChoiceChips(
                            options = ReaderMode.entries.toList(),
                            selected = settings.readerMode,
                            label = { it.label() },
                            onSelect = vm::setReaderMode
                        )
                        Spacer(Modifier.height(8.dp))
                        SettingLabel("Fit pages")
                        ChoiceChips(
                            options = MangaFit.entries.toList(),
                            selected = settings.mangaFit,
                            label = { it.label },
                            onSelect = vm::setMangaFit
                        )
                        Spacer(Modifier.height(8.dp))
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
            }

            SettingsPage.PLAYBACK -> item {
                Column {
                    SettingsSection("Playback") {
                        SwitchRow(
                            title = "Resume where you stopped",
                            subtitle = "Continue video from the last saved position",
                            checked = settings.resumePlayback,
                            onChange = vm::setResumePlayback
                        )
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            title = "Show subtitles",
                            subtitle = "Turn on subtitle tracks a stream provides",
                            checked = settings.subtitlesEnabled,
                            onChange = vm::setSubtitlesEnabled
                        )
                        Spacer(Modifier.height(8.dp))
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
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "The player's rewind and forward buttons jump this far, and " +
                                "double-tapping the left or right half of the video does the same.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsPage.DOWNLOADS -> item {
                Column {
                    SettingsSection("Downloading") {
                        SwitchRow(
                            title = "Only on Wi-Fi or Ethernet",
                            subtitle = "Queued downloads wait for an unmetered connection",
                            checked = settings.downloadWifiOnly,
                            onChange = vm::setDownloadWifiOnly
                        )
                        Spacer(Modifier.height(10.dp))
                        SettingLabel("Downloads at the same time")
                        ChoiceChips(
                            options = (1..4).toList(),
                            selected = settings.concurrentDownloads,
                            label = { "$it" },
                            onSelect = vm::setConcurrentDownloads
                        )
                    }
                    SettingsSection("Queue") {
                        Text(
                            text = "Pause, reorder, retry or cancel individual downloads from " +
                                "the queue. Completed files stay on this device and can be " +
                                "played without a connection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onOpenDownloads) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open download queue")
                        }
                    }
                }
            }

            SettingsPage.STORAGE -> item {
                SettingsSection("Storage") {
                    val stats = storage
                    if (stats == null) {
                        Text(
                            text = "Calculating storage use…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        StatRow("Temporary files", SettingsViewModel.formatBytes(stats.cacheBytes))
                        StatRow("Installed extensions", SettingsViewModel.formatBytes(stats.extensionBytes))
                        StatRow("Library database", SettingsViewModel.formatBytes(stats.databaseBytes))
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        StatRow("Total", SettingsViewModel.formatBytes(stats.totalBytes))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = vm::clearCache,
                            enabled = busy == null
                        ) { Text("Clear cached files") }
                        TextButton(
                            onClick = vm::refreshStorage,
                            enabled = busy == null
                        ) { Text("Refresh") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Clearing cached files removes downloaded images and repository " +
                            "scratch files. Installed extensions, books and your library are " +
                            "kept.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsPage.NETWORK -> item {
                SettingsSection("Network") {
                    Text(
                        text = "AllFun talks to sources directly from this device; there is no " +
                            "account or proxy server in between. Requests time out after 20 " +
                            "seconds to connect and 30 seconds to read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = vm::testConnection,
                        enabled = busy == null
                    ) { Text("Test repository connection") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "The test downloads the public Keiyoushi manga index and reports " +
                            "how many extensions it lists.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsPage.BACKUP -> item {
                SettingsSection("Backup") {
                    Text(
                        text = "A backup is a JSON file with your settings, repository URLs, " +
                            "library, chapters or episodes, and reading/watching progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Extension code and Jellyfin/Stremio credentials are not " +
                            "included. Reinstall extensions and sign in again after restoring " +
                            "on another device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExport, enabled = busy == null) {
                            Icon(Icons.Outlined.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Export")
                        }
                        OutlinedButton(onClick = onImport, enabled = busy == null) {
                            Icon(Icons.Outlined.Restore, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Restore")
                        }
                    }
                }
            }

            SettingsPage.ABOUT -> item {
                SettingsSection("About") {
                    Text(
                        text = "AllFun ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "One library for manga, light novels and video. Kotlin and " +
                            "Jetpack Compose, Room for the shelf, Media3 for playback, a WebView " +
                            "novel reader, and a source layer that runs Jellyfin, Stremio addons, " +
                            "Tachiyomi/Komikku extensions and LNReader plugins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Built-in repositories: Keiyoushi (manga extensions) and " +
                            "LNReader (novel plugins). Both are public indexes downloaded on " +
                            "request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Not implemented: tracker sync (MAL, AniList) and declarative " +
                            "remote JSON sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to settings")
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BusyBar(label: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
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
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        // The row owns the toggle semantics, so the switch itself is not a second target.
        Switch(checked = checked, onCheckedChange = null)
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
    // A continuous track: Material 3 draws a row of tick dots for stepped sliders, which
    // turned these two settings into a dotted progress bar. The value is rounded by the
    // callback, and written once the finger lifts instead of on every dragged pixel.
    var dragged by remember { mutableStateOf<Float?>(null) }
    // Drop the drag overlay only once the stored value arrives, so the thumb does not
    // snap back to the old position for a frame while the preference is written.
    LaunchedEffect(value) { dragged = null }
    val shown = dragged ?: value
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel(shown),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Slider(
            value = shown,
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let(onValueChangeFinished)
            },
            valueRange = valueRange
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System theme"
    ThemeMode.DARK -> "Dark theme"
    ThemeMode.LIGHT -> "Light theme"
}

private fun LibraryView.label(): String = if (this == LibraryView.GRID) "Grid" else "List"

private fun ReaderMode.label(): String =
    if (this == ReaderMode.VERTICAL) "Vertical scroll" else "Paged"

private fun Boolean.onOff(): String = if (this) "on" else "off"
