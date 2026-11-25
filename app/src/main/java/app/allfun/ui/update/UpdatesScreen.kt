package app.allfun.ui.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFSpinner
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.BottomScrim
import app.allfun.ui.glass.GlassButton
import app.allfun.ui.glass.GlassPanel
import app.allfun.ui.glass.GlassPrimaryAction
import app.allfun.ui.glass.GlassProgress
import app.allfun.ui.glass.GlassToastHost
import app.allfun.ui.glass.GlassTopCapsule
import app.allfun.ui.glass.ScreenHeader
import app.allfun.ui.glass.Section
import app.allfun.ui.glass.TopScrim
import app.allfun.ui.glass.WorldBackdrop
import app.allfun.ui.glass.rememberGlassToastState
import app.allfun.ui.settings.SettingsViewModel
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NAV_RESERVE = 140.dp

/**
 * Settings → App Updates. One job: say whether a newer release exists and what changed in
 * it, then offer the one action that applies (download, install or retry). Installed codes,
 * checksums, schedules and install reports stay in the background where they belong.
 */
@Composable
fun UpdatesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm = rememberViewModel { UpdatesViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = rememberGlassToastState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showAllNotes by remember { mutableStateOf(false) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(Unit) {
        vm.messages.collect { toast.show(it) }
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

    WorldBackdrop(section = Section.SYSTEM) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = statusTop + 72.dp,
                    bottom = NAV_RESERVE
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item(key = "header") {
                    ScreenHeader(
                        title = "App updates",
                        subtitle = "One check against the public GitHub releases",
                        overline = "Settings"
                    )
                }

                when {
                    release != null -> item(key = "release") {
                        UpdateCard(releaseName = release.versionName) {
                            if (release.publishedAt.isNotBlank()) {
                                AFText(
                                    text = "Published ${formatIsoDate(release.publishedAt)}",
                                    style = AFTheme.type.caption,
                                    color = AFTheme.colors.textLo,
                                    onGlass = true
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            AFText(
                                text = release.notes.ifBlank { "This release has no notes." },
                                style = AFTheme.type.bodySmall,
                                color = AFTheme.colors.textMid,
                                onGlass = true,
                                maxLines = if (showAllNotes) Int.MAX_VALUE else 18,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (release.notes.length > 900) {
                                AFTextButton(
                                    label = if (showAllNotes) "Show less" else "Show all changes",
                                    onClick = { showAllNotes = !showAllNotes }
                                )
                            }
                            Spacer(Modifier.height(14.dp))
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
                                Spacer(Modifier.height(8.dp))
                                AFTextButton(
                                    label = "Open release page",
                                    onClick = { uriHandler.openUri(release.releaseUrl) }
                                )
                            }
                        }
                    }

                    state.checking -> item(key = "checking") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AFSpinner(size = 22.dp)
                            Spacer(Modifier.width(12.dp))
                            AFText(
                                text = "Checking GitHub releases…",
                                style = AFTheme.type.body,
                                color = AFTheme.colors.textMid
                            )
                        }
                    }

                    state.checkFailed -> item(key = "failed") {
                        GlassPanel(header = "Could not check") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AFIcon(
                                    icon = AFIcons.Info,
                                    contentDescription = null,
                                    tint = AFTheme.colors.failed,
                                    size = 20.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                AFText(
                                    text = state.status ?: "Could not reach GitHub.",
                                    style = AFTheme.type.body,
                                    color = AFTheme.colors.textHi,
                                    onGlass = true
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            GlassButton(
                                label = "Try again",
                                onClick = vm::checkForUpdates,
                                icon = AFIcons.Refresh
                            )
                        }
                    }

                    else -> item(key = "latest") {
                        GlassPanel(header = "Up to date") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AFIcon(
                                    icon = AFIcons.Check,
                                    contentDescription = null,
                                    tint = AFTheme.colors.ok,
                                    size = 20.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                AFText(
                                    text = "You're on the latest release.",
                                    style = AFTheme.type.body,
                                    color = AFTheme.colors.textHi,
                                    onGlass = true
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            AFText(
                                text = "Installed version ${state.installedVersion}",
                                style = AFTheme.type.caption,
                                color = AFTheme.colors.textLo,
                                onGlass = true
                            )
                            Spacer(Modifier.height(14.dp))
                            GlassButton(
                                label = "Check again",
                                onClick = vm::checkForUpdates,
                                icon = AFIcons.Refresh
                            )
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
                onBack = onBack
            ) {
                AFText(
                    text = "App updates",
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                if (state.checking) {
                    Box(Modifier.padding(end = 18.dp)) { AFSpinner(size = 18.dp) }
                } else {
                    AFTextButton(
                        label = "Check",
                        onClick = vm::checkForUpdates,
                        modifier = Modifier.padding(end = 10.dp)
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

/** The version and its changes, framed as the only thing this screen is about. */
@Composable
private fun UpdateCard(
    releaseName: String,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassPanel(header = "New update available") {
        AFText(
            text = "Version $releaseName",
            style = AFTheme.type.title,
            color = AFTheme.colors.textHi,
            onGlass = true
        )
        Spacer(Modifier.height(4.dp))
        AFText(
            text = "Downloaded from the release attached to this repository",
            style = AFTheme.type.caption,
            color = AFTheme.colors.accent,
            onGlass = true
        )
        Spacer(Modifier.height(14.dp))
        content()
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
                0f
            }
            GlassProgress(progress = fraction, height = 4.dp)
            Spacer(Modifier.height(10.dp))
            AFText(
                text = if (verifying) {
                    "Checking the download…"
                } else {
                    buildString {
                        append("${(fraction * 100).toInt()}% · ")
                        append(SettingsViewModel.formatBytes(running!!.bytes))
                        if (running.total > 0) {
                            append(" of ${SettingsViewModel.formatBytes(running.total)}")
                        }
                    }
                },
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo,
                onGlass = true
            )
            Spacer(Modifier.height(4.dp))
            AFTextButton("Cancel", onCancel)
        }

        ready -> {
            AFText(
                text = "Downloaded and verified. Android asks you to confirm the install.",
                style = AFTheme.type.bodySmall,
                color = AFTheme.colors.textMid,
                onGlass = true
            )
            Spacer(Modifier.height(12.dp))
            GlassPrimaryAction(
                label = if (canInstall) "Install $release" else "Allow installs",
                onClick = onInstall,
                icon = AFIcons.Download,
                modifier = Modifier.fillMaxWidth()
            )
        }

        else -> {
            if (failed != null) {
                AFText(
                    text = failed.message,
                    style = AFTheme.type.bodySmall,
                    color = AFTheme.colors.failed,
                    onGlass = true
                )
                Spacer(Modifier.height(10.dp))
            }
            GlassPrimaryAction(
                label = if (failed != null) "Retry download" else "Download update",
                onClick = onDownload,
                icon = AFIcons.Download,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatIsoDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrDefault(iso)
