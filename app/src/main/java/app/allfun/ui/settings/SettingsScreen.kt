package app.allfun.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.BuildConfig
import app.allfun.data.AppContainer
import app.allfun.data.settings.LibrarySort
import app.allfun.data.settings.LibraryView
import app.allfun.data.settings.MangaFit
import app.allfun.data.settings.NovelTheme
import app.allfun.data.settings.ReaderMode
import app.allfun.data.settings.ReadingDirection
import app.allfun.data.settings.ThemeMode
import app.allfun.ui.components.rememberViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    container: AppContainer,
    onManageExtensions: () -> Unit = {},
    onOpenUpdates: () -> Unit = {}
) {
    val vm = rememberViewModel { SettingsViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        // The root already pads for the bottom bar; insets are applied once, on the list.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            if (busy != null) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text(
                            text = busy.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            // 1. Appearance
            item {
                SettingsSection("Appearance") {
                    SettingLabel("Theme")
                    ChoiceChips(
                        options = ThemeMode.entries.toList(),
                        selected = settings.themeMode,
                        label = {
                            when (it) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.LIGHT -> "Light"
                            }
                        },
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
                    SettingLabel("Library layout")
                    ChoiceChips(
                        options = LibraryView.entries.toList(),
                        selected = settings.libraryView,
                        label = { if (it == LibraryView.GRID) "Grid" else "List" },
                        onSelect = vm::setLibraryView
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingLabel("Library sort")
                    ChoiceChips(
                        options = LibrarySort.entries.toList(),
                        selected = settings.librarySort,
                        label = { it.label },
                        onSelect = vm::setLibrarySort
                    )
                }
            }

            // 2. Accessibility
            item {
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

            // 3. Reading
            item {
                SettingsSection("Reading") {
                    SettingLabel("Manga page layout")
                    ChoiceChips(
                        options = ReaderMode.entries.toList(),
                        selected = settings.readerMode,
                        label = {
                            if (it == ReaderMode.VERTICAL) "Vertical scroll" else "Paged"
                        },
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
                        label = { if (it == ReadingDirection.LTR) "Left to right" else "Right to left" },
                        onSelect = vm::setReadingDirection
                    )

                    SectionDivider()

                    SliderRow(
                        title = "Novel text size",
                        valueLabel = "${settings.novelFontSize}%",
                        value = settings.novelFontSize.toFloat(),
                        valueRange = 70f..180f,
                        steps = 21,
                        onValueChange = { vm.setFontSize(it.toInt()) }
                    )
                    SliderRow(
                        title = "Novel line spacing",
                        valueLabel = String.format(Locale.US, "%.1f×", settings.novelLineSpacing),
                        value = settings.novelLineSpacing,
                        valueRange = 1.2f..2.4f,
                        steps = 11,
                        onValueChange = vm::setLineSpacing
                    )
                    SettingLabel("Novel theme")
                    ChoiceChips(
                        options = NovelTheme.entries.toList(),
                        selected = settings.novelTheme,
                        label = { it.label },
                        onSelect = vm::setNovelTheme
                    )
                }
            }

            // 4. Playback
            item {
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
                }
            }

            // 5. Extensions
            item {
                SettingsSection("Extensions") {
                    Text(
                        text = "Manga extensions, LNReader plugins, Stremio addons and Jellyfin " +
                            "connections are installed from the Extensions screen, where each " +
                            "one shows its version, status and permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onManageExtensions) {
                        Icon(Icons.Outlined.Extension, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Manage extensions")
                    }
                }
            }

            // 6. Downloads
            item {
                SettingsSection("Downloads") {
                    Text(
                        text = "Offline downloads are not implemented. AllFun streams video " +
                            "from the source you choose and does not save episodes to this " +
                            "device, so there is no download queue or download quality setting " +
                            "here yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 7. Storage
            item {
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

            // 8. Network
            item {
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

            // 9. Backup
            item {
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
                        Button(
                            onClick = { exportLauncher.launch(suggestedName) },
                            enabled = busy == null
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            enabled = busy == null
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Restore")
                        }
                    }
                }
            }

            // 10. About
            item {
                SettingsSection("About") {
                    Text(
                        text = "AllFun ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenUpdates)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("App Updates", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Check GitHub Releases, download and install a newer version",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
                        text = "Not implemented: offline downloads, tracker sync (MAL, AniList), " +
                            "and declarative remote JSON sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
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

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
}
