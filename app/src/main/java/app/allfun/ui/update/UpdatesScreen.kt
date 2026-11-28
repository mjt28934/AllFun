package app.allfun.ui.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import app.allfun.ui.components.AllFunButton
import app.allfun.ui.components.AllFunButtonVariant
import app.allfun.ui.components.AppTopBar
import app.allfun.ui.components.Overline
import app.allfun.ui.components.TopBarAction
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.settings.SettingsViewModel
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.AllFunType
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            item {
                AppTopBar(
                    title = "App updates",
                    meta = "AllFun ${state.installedVersion}",
                    onBack = onBack,
                    actions = {
                        if (state.checking) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                                color = AllFun.colors.accent
                            )
                        } else {
                            TopBarAction(
                                icon = Icons.Outlined.Refresh,
                                contentDescription = "Check for updates",
                                onClick = vm::checkForUpdates
                            )
                        }
                    }
                )
            }

            when {
                release != null -> item {
                    UpdateCard(releaseName = release.versionName) {
                        if (release.publishedAt.isNotBlank()) {
                            Text(
                                text = "Published ${formatIsoDate(release.publishedAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AllFun.colors.textSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            text = release.notes.ifBlank { "This release has no notes." },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (showAllNotes) Int.MAX_VALUE else 18
                        )
                        if (release.notes.length > 900) {
                            AllFunButton(
                                text = if (showAllNotes) "Show less" else "Show all changes",
                                onClick = { showAllNotes = !showAllNotes },
                                variant = AllFunButtonVariant.TEXT
                            )
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
                            AllFunButton(
                                text = "Open release page",
                                onClick = { uriHandler.openUri(release.releaseUrl) },
                                variant = AllFunButtonVariant.TEXT,
                                icon = Icons.AutoMirrored.Outlined.OpenInNew
                            )
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
                        AllFunButton(
                            text = "Try again",
                            onClick = vm::checkForUpdates,
                            variant = AllFunButtonVariant.SECONDARY
                        )
                    }
                }

                else -> item {
                    Column(Modifier.padding(top = 20.dp)) {
                        Text(
                            text = if (state.latest == null) {
                                "No stable release yet"
                            } else {
                                "Up to date · ${state.installedVersion}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = AllFun.colors.textPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Last checked · ${state.lastCheckAt?.let(::formatCheckTime) ?: "Not checked"}",
                            style = AllFunType.mono,
                            color = AllFun.colors.textSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        AllFunButton(
                            text = "Check again",
                            onClick = vm::checkForUpdates,
                            variant = AllFunButtonVariant.TEXT
                        )
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
    Column(Modifier.fillMaxWidth()) {
        Overline("New update")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Version $releaseName",
            style = MaterialTheme.typography.headlineMedium,
            color = AllFun.colors.textPrimary
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = AllFun.colors.hairline)
        Spacer(Modifier.height(16.dp))
        content()
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
                color = AllFun.colors.textSecondary
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
    val tint = if (tone == MessageTone.ERROR) AllFun.colors.danger else AllFun.colors.success
    Row(Modifier.fillMaxWidth()) {
        // A 2 dp rule carries the tone; the text stays in normal reading colours.
        Box(
            Modifier
                .width(2.dp)
                .height(if (tone == MessageTone.ERROR) 56.dp else 40.dp)
                .background(tint)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AllFun.colors.textPrimary
                )
            }
            Spacer(Modifier.height(12.dp))
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
                color = AllFun.colors.textSecondary
            )
            AllFunButton(
                text = "Cancel",
                onClick = onCancel,
                variant = AllFunButtonVariant.TEXT
            )
        }

        ready -> {
            Text(
                text = "Downloaded and verified. Android asks you to confirm the install.",
                style = MaterialTheme.typography.bodySmall,
                color = AllFun.colors.textSecondary
            )
            Spacer(Modifier.height(10.dp))
            AllFunButton(
                text = if (canInstall) "Install $release" else "Allow installs",
                onClick = onInstall,
                icon = Icons.Outlined.SystemUpdate,
                modifier = Modifier.fillMaxWidth(),
                fullWidth = true
            )
        }

        else -> {
            if (failed != null) {
                Text(
                    text = failed.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = AllFun.colors.danger
                )
                Spacer(Modifier.height(10.dp))
            }
            AllFunButton(
                text = if (failed != null) "Retry download" else "Download update",
                onClick = onDownload,
                icon = Icons.Outlined.Download,
                modifier = Modifier.fillMaxWidth(),
                fullWidth = true
            )
        }
    }
}

private fun formatCheckTime(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatIsoDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrDefault(iso)
