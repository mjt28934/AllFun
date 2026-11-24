package app.allfun.ui.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.settings.SettingsViewModel
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings → App Updates. One job: say whether a newer release exists and what changed in
 * it, then offer the one action that applies (download, install or retry). Installed codes,
 * checksums, schedules and install reports stay in the background where they belong.
 */
@Composable
fun UpdatesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm = rememberViewModel { UpdatesViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showAllNotes by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // Returning from the system's "install unknown apps" screen re-checks the setting.
    val installPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.refreshInstallPermission() }

    val ready = state.download as? UpdateDownloadState.Ready
    val running = state.download as? UpdateDownloadState.Running
    val failed = state.download as? UpdateDownloadState.Failed
    val verifying = state.download is UpdateDownloadState.Verifying
    val release = state.available

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "App Updates",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = vm::checkForUpdates) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Check for updates")
                        }
                    }
                }
            }

            when {
                release != null -> item {
                    UpdateCard(releaseName = release.versionName) {
                        if (release.publishedAt.isNotBlank()) {
                            Text(
                                text = "Published ${formatIsoDate(release.publishedAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            text = release.notes.ifBlank { "This release has no notes." },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (showAllNotes) Int.MAX_VALUE else 18
                        )
                        if (release.notes.length > 900) {
                            TextButton(
                                onClick = { showAllNotes = !showAllNotes },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (showAllNotes) "Show less" else "Show all changes")
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        UpdateAction(
                            release = release.versionName,
                            running = running,
                            verifying = verifying,
                            ready = ready != null,
                            failed = failed,
                            canInstall = state.canInstallPackages,
                            onDownload = vm::downloadUpdate,
                            onCancel = vm::cancelDownload,
                            onInstall = {
                                if (state.canInstallPackages) {
                                    vm.installUpdate()
                                } else {
                                    UpdatesViewModel.unknownSourcesSettingsIntent(
                                        context.packageName
                                    )?.let(installPermission::launch)
                                }
                            }
                        )
                        if (release.releaseUrl.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = { uriHandler.openUri(release.releaseUrl) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Open release page")
                            }
                        }
                    }
                }

                state.checking -> item { CheckingCard() }

                state.checkFailed -> item {
                    MessageCard(
                        icon = Icons.Outlined.ErrorOutline,
                        message = state.status ?: "Could not reach GitHub.",
                        tone = MessageTone.ERROR
                    ) {
                        OutlinedButton(onClick = vm::checkForUpdates) { Text("Try again") }
                    }
                }

                else -> item {
                    MessageCard(
                        icon = Icons.Outlined.CheckCircle,
                        message = "You're on the latest release.",
                        tone = MessageTone.SUCCESS
                    ) {
                        Text(
                            text = "Installed version ${state.installedVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = vm::checkForUpdates) {
                            Text("Check again")
                        }
                    }
                }
            }
        }
    }
}

/** The version and its changes, framed as the only thing this screen is about. */
@Composable
private fun UpdateCard(
    releaseName: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Version $releaseName",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "New update available",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun CheckingCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Checking GitHub releases…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class MessageTone { SUCCESS, ERROR }

@Composable
private fun MessageCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    tone: MessageTone,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val container = if (tone == MessageTone.ERROR) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = if (tone == MessageTone.ERROR) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        color = container,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = onContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = onContainer
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun UpdateAction(
    release: String,
    running: UpdateDownloadState.Running?,
    verifying: Boolean,
    ready: Boolean,
    failed: UpdateDownloadState.Failed?,
    canInstall: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit
) {
    when {
        running != null || verifying -> {
            val fraction = if (running != null && running.total > 0) {
                (running.bytes.toFloat() / running.total.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (verifying) {
                    "Checking the download…"
                } else {
                    buildString {
                        append("${((fraction ?: 0f) * 100).toInt()}% · ")
                        append(SettingsViewModel.formatBytes(running!!.bytes))
                        if (running.total > 0) {
                            append(" of ${SettingsViewModel.formatBytes(running.total)}")
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) {
                Text("Cancel")
            }
        }

        ready -> {
            Text(
                text = "Downloaded and verified. Android asks you to confirm the install.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (canInstall) "Install $release" else "Allow installs")
            }
        }

        else -> {
            if (failed != null) {
                Text(
                    text = failed.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(10.dp))
            }
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (failed != null) "Retry download" else "Download update")
            }
        }
    }
}

private fun formatIsoDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrDefault(iso)
