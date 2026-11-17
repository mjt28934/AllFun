package app.allfun.ui.update

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import app.allfun.data.AppContainer
import app.allfun.data.update.UpdateSource
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Settings → About → App Updates. Everything shown here comes from GitHub Releases: the
 * installed build, the latest published release, its notes and the real download state.
 */
@Composable
fun UpdatesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm = rememberViewModel { UpdatesViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showAllNotes by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(vm.notificationsAllowed()) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // Android 13+ needs a runtime permission before the update notification can appear.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = vm.notificationsAllowed()
        vm.setNotify(granted)
        if (!granted) {
            scope.launch {
                snackbar.showSnackbar(
                    "Android did not grant notification permission, so update alerts stay off."
                )
            }
        }
    }

    // Returning from the app's notification settings re-reads whether alerts can appear.
    val notificationSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        notificationsAllowed = vm.notificationsAllowed()
        vm.setNotify(vm.notificationsAllowed())
    }

    // Returning from the system's "install unknown apps" screen re-checks the setting.
    val installPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.refreshInstallPermission() }

    val ready = state.download as? UpdateDownloadState.Ready
    val running = state.download as? UpdateDownloadState.Running
    val failed = state.download as? UpdateDownloadState.Failed

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("App Updates", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Releases from github.com/${UpdateSource.OWNER}/${UpdateSource.REPO}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(8.dp)
                                .width(22.dp)
                                .height(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = vm::checkForUpdates) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Check for updates")
                        }
                    }
                }
            }

            item {
                UpdateSection("Versions") {
                    InfoRow("Installed", "${state.installedVersion} (code ${state.installedCode})")
                    InfoRow(
                        "Latest release",
                        state.latest?.let { "${it.versionName} · ${it.tag}" } ?: "Not checked yet"
                    )
                    InfoRow("Last checked", state.lastCheckAt?.let(::formatTimestamp) ?: "Never")
                    if (state.installedIsAhead) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "This build is newer than the latest published release, so " +
                                "there is nothing to install.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val status = state.status
            if (status != null) {
                item {
                    Surface(
                        color = if (state.checkFailed) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (state.checkFailed) {
                                    Icons.Outlined.ErrorOutline
                                } else {
                                    Icons.Outlined.CheckCircle
                                },
                                contentDescription = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.checkFailed) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                }
            }

            val release = state.available
            if (release != null) {
                item {
                    UpdateSection("Available update ${release.versionName}") {
                        if (release.publishedAt.isNotBlank()) {
                            Text(
                                text = "Published ${formatIsoDate(release.publishedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = "Release notes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = release.notes.ifBlank { "This release has no notes." },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (showAllNotes) Int.MAX_VALUE else 12
                        )
                        if (release.notes.length > 700) {
                            TextButton(
                                onClick = { showAllNotes = !showAllNotes },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (showAllNotes) "Show less" else "Show all notes")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        InfoRow(
                            "APK",
                            "${release.apkAssetName} · ${SettingsViewModel.formatBytes(release.apkSize)}"
                        )
                        InfoRow(
                            "SHA-256",
                            release.checksumSha256?.take(16)?.plus("…") ?: "not published"
                        )
                        if (release.releaseUrl.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { uriHandler.openUri(release.releaseUrl) }) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open release page")
                            }
                        }
                    }
                }
            }

            item {
                UpdateSection("Actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = vm::checkForUpdates,
                            enabled = !state.checking && running == null
                        ) {
                            Text("Check for Updates")
                        }
                        if (release != null && ready == null) {
                            Button(
                                onClick = vm::downloadUpdate,
                                enabled = !state.checking &&
                                    running == null &&
                                    state.download !is UpdateDownloadState.Verifying
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (failed != null) "Retry Download" else "Download Update")
                            }
                        }
                        if (running != null || state.download is UpdateDownloadState.Verifying) {
                            OutlinedButton(onClick = vm::cancelDownload) {
                                Text("Cancel")
                            }
                        }
                    }

                    if (running != null) {
                        Spacer(Modifier.height(12.dp))
                        val fraction = if (running.total > 0) {
                            (running.bytes.toFloat() / running.total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                if (running.total > 0) {
                                    append("${(fraction ?: 0f).times(100).toInt()}% · ")
                                }
                                append(SettingsViewModel.formatBytes(running.bytes))
                                if (running.total > 0) {
                                    append(" of ${SettingsViewModel.formatBytes(running.total)}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    when (val download = state.download) {
                        is UpdateDownloadState.Verifying -> {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Download finished. Validating SHA-256, package identity and " +
                                    "signing certificate…",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        is UpdateDownloadState.Ready -> {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Verified ${download.file.name} " +
                                    "(${SettingsViewModel.formatBytes(download.file.length())}). " +
                                    "Same package and signing key as this install.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                if (state.canInstallPackages) {
                                    vm.installUpdate()
                                } else {
                                    UpdatesViewModel.unknownSourcesSettingsIntent(
                                        context.packageName
                                    )?.let(installPermission::launch)
                                }
                            }) {
                                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.canInstallPackages) "Install Update" else "Allow Installs")
                            }
                            if (!state.canInstallPackages) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Android needs permission for AllFun to install its own " +
                                        "updates. The system dialog still asks you to confirm.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is UpdateDownloadState.Failed -> {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = download.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = vm::dismissDownloadError) {
                                Text("Dismiss")
                            }
                        }
                        else -> Unit
                    }
                }
            }

            val report = state.installReport
            if (report != null) {
                item {
                    UpdateSection("Last installation") {
                        Text(
                            text = if (report.success) {
                                "Android reported a successful install. AllFun is now " +
                                    "${state.installedVersion}."
                            } else {
                                "The system installer did not finish the update."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (report.message.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Installer message: ${report.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        InfoRow("When", formatTimestamp(report.at))
                        TextButton(
                            onClick = vm::dismissInstallReport,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            item {
                UpdateSection("Automatic checks") {
                    SwitchRow(
                        title = "Check on launch and in the background",
                        subtitle = "At most once every 12 hours. Runs only while a network is " +
                            "available, using WorkManager.",
                        checked = state.autoCheck,
                        onChange = { enabled -> vm.setAutoCheck(enabled) }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SwitchRow(
                        title = "Notify me when an update is found",
                        subtitle = when {
                            notificationsAllowed ->
                                "One low-priority notification per published version."
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED ->
                                "Android has not granted notification permission yet."
                            else ->
                                "Notifications for AllFun are turned off in Android settings."
                        },
                        checked = state.notify && notificationsAllowed,
                        onChange = { enabled ->
                            if (!enabled) {
                                vm.setNotify(false)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else if (!notificationsAllowed) {
                                notificationSettings.launch(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            } else {
                                vm.setNotify(true)
                            }
                        }
                    )
                }
            }

            item {
                UpdateSection("How updates work") {
                    Text(
                        text = "Only published, non-draft GitHub Releases count as updates. " +
                            "A commit or a tag without a published release is ignored. The APK " +
                            "is downloaded from the release asset, its SHA-256 must match the " +
                            "published checksum, and Android's own installer performs the " +
                            "upgrade after you confirm it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { uriHandler.openUri(UpdateSource.releasesUrl) }
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("All releases on GitHub")
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
private fun InfoRow(label: String, value: String) {
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
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun formatTimestamp(at: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(at))

private fun formatIsoDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrDefault(iso)
