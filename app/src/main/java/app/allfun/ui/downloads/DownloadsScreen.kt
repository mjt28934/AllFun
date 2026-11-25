package app.allfun.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.download.QueueOrder
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.formatBytes
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFOverline
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.BottomScrim
import app.allfun.ui.glass.GlassChip
import app.allfun.ui.glass.GlassDialog
import app.allfun.ui.glass.GlassEmpty
import app.allfun.ui.glass.GlassPanel
import app.allfun.ui.glass.GlassProgress
import app.allfun.ui.glass.GlassSegmented
import app.allfun.ui.glass.GlassSheet
import app.allfun.ui.glass.GlassToastHost
import app.allfun.ui.glass.GlassTopCapsule
import app.allfun.ui.glass.ScreenHeader
import app.allfun.ui.glass.Section
import app.allfun.ui.glass.SheetRow
import app.allfun.ui.glass.TopScrim
import app.allfun.ui.glass.WorldBackdrop
import app.allfun.ui.glass.rememberGlassToastState
import app.allfun.ui.glass.rememberHaptics
import java.text.DateFormat
import java.util.Date

private val NAV_RESERVE = 160.dp

/**
 * Downloads â†’ the live queue, with Komikku's behaviour.
 *
 * The queue is explicit and persisted: every item has a position that menu actions ("move
 * to top"/"move to bottom"), whole-title moves and the sort sheet rewrite. One capsule
 * control pauses or resumes the downloader; the overflow can cancel the whole queue.
 * Completed downloads stay because they are real files the Library reads offline.
 */
@Composable
fun DownloadsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenMedia: (String) -> Unit
) {
    val vm = rememberViewModel { DownloadsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val openMediaId by vm.openMediaId.collectAsStateWithLifecycle()
    val toast = rememberGlassToastState()
    var pendingCancel by remember { mutableStateOf<DownloadEntity?>(null) }
    var pendingRemove by remember { mutableStateOf<DownloadEntity?>(null) }
    var pendingCancelGroup by remember { mutableStateOf<DownloadGroup?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearQueue by remember { mutableStateOf(false) }
    var showConcurrency by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var rowMenu by remember { mutableStateOf<DownloadEntity?>(null) }
    var groupMenu by remember { mutableStateOf<DownloadGroup?>(null) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(Unit) {
        vm.messages.collect { toast.show(it) }
    }
    LaunchedEffect(openMediaId) {
        openMediaId?.let { id ->
            onOpenMedia(id)
            vm.clearOpen()
        }
    }

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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "header") {
                    ScreenHeader(
                        title = "Downloads",
                        subtitle = "Saved files play without a connection",
                        overline = "Offline"
                    )
                }

                if (state.rows.isEmpty()) {
                    item(key = "empty") {
                        GlassEmpty(
                            title = "Nothing queued",
                            body = "Open a manga chapter, a novel chapter or a film and tap " +
                                "Download. Progress and failures are shown here with what " +
                                "really happened.",
                            actionLabel = "Back",
                            onAction = onBack
                        )
                    }
                } else {
                    item(key = "summary") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AFText(
                                text = buildString {
                                    append("${state.activeCount} active")
                                    append(" Â· ${state.completed.size} saved")
                                    if (state.failed.isNotEmpty()) {
                                        append(" Â· ${state.failed.size} failed")
                                    }
                                    append(" Â· ${state.concurrent} at a time")
                                },
                                style = AFTheme.type.caption,
                                color = if (state.failed.isNotEmpty()) {
                                    AFTheme.colors.failed
                                } else {
                                    AFTheme.colors.textLo
                                },
                                modifier = Modifier.weight(1f)
                            )
                            AFText(
                                text = when {
                                    state.paused -> "Paused"
                                    state.activeCount == 0 -> "Idle"
                                    else -> "Running"
                                },
                                style = AFTheme.type.label,
                                color = if (state.paused) AFTheme.colors.textMid
                                else AFTheme.colors.textMid
                            )
                        }
                    }

                    state.groups.forEach { group ->
                        item(key = "group:" + group.mediaId) {
                            DownloadGroupPanel(
                                group = group,
                                onOpenGroupMenu = { groupMenu = group },
                                onOpenRowMenu = { rowMenu = it },
                                onOpenTitle = { vm.openMedia(group.mediaId) }
                            )
                        }
                    }

                    item(key = "storage") {
                        AFText(
                            text = "${formatBytes(state.completedBytes)} stored on this device",
                            style = AFTheme.type.caption,
                            color = AFTheme.colors.textLo,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                    text = "Downloads",
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                if (state.activeCount > 0) {
                    AFText(
                        text = state.activeCount.toString(),
                        style = AFTheme.type.numeric,
                        color = AFTheme.colors.accent,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                if (state.canToggleQueue) {
                    AFIconButton(
                        icon = if (state.paused) AFIcons.Play else AFIcons.Pause,
                        contentDescription = if (state.paused) {
                            "Resume the queue"
                        } else {
                            "Pause the queue"
                        },
                        onClick = vm::toggleQueue
                    )
                }
                if (state.rows.isNotEmpty()) {
                    AFIconButton(
                        icon = AFIcons.More,
                        contentDescription = "Queue options",
                        onClick = { moreOpen = true }
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

    if (sortOpen) {
        GlassSheet(onDismiss = { sortOpen = false }) {
            AFText("Order the queue", style = AFTheme.type.title, color = AFTheme.colors.textHi)
            Spacer(Modifier.height(10.dp))
            QueueOrder.entries.forEach { option ->
                SheetRow(
                    title = option.label,
                    onClick = {
                        vm.sortQueue(option)
                        sortOpen = false
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    if (moreOpen) {
        GlassSheet(onDismiss = { moreOpen = false }) {
            AFText("Queue options", style = AFTheme.type.title, color = AFTheme.colors.textHi)
            Spacer(Modifier.height(10.dp))
            SheetRow(
                title = "Order the queue",
                subtitle = "Sort active downloads by their position or title",
                leading = { AFIcon(AFIcons.Sort, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = {
                    moreOpen = false
                    sortOpen = true
                }
            )
            SheetRow(
                title = "Download only on Wi-Fi",
                subtitle = "Queued files wait for an unmetered connection",
                selected = state.wifiOnly,
                leading = { AFIcon(AFIcons.Wifi, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = { vm.setWifiOnly(!state.wifiOnly) }
            )
            SheetRow(
                title = "Downloads at the same time",
                subtitle = "Currently ${state.concurrent}",
                leading = { AFIcon(AFIcons.Sliders, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = {
                    moreOpen = false
                    showConcurrency = true
                }
            )
            SheetRow(
                title = "Refresh queue",
                leading = { AFIcon(AFIcons.Refresh, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = {
                    vm.refresh()
                    moreOpen = false
                }
            )
            SheetRow(
                title = "Clear finished",
                subtitle = "Delete completed files and drop failed entries",
                leading = { AFIcon(AFIcons.Trash, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = {
                    moreOpen = false
                    confirmClear = true
                }
            )
            SheetRow(
                title = "Cancel all",
                subtitle = "Stop every unfinished download",
                leading = { AFIcon(AFIcons.Close, null, tint = AFTheme.colors.failed, size = 20.dp) },
                onClick = {
                    moreOpen = false
                    confirmClearQueue = true
                }
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    rowMenu?.let { row ->
        GlassSheet(onDismiss = { rowMenu = null }) {
            AFText(
                text = row.unitTitle,
                style = AFTheme.type.title,
                color = AFTheme.colors.textHi,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            if (row.status.isActive) {
                SheetRow("Move to top", onClick = { vm.moveToTop(row.key); rowMenu = null })
                SheetRow(
                    "Move this title to top",
                    onClick = { vm.moveGroupToTop(row.mediaId); rowMenu = null }
                )
                SheetRow("Move to bottom", onClick = { vm.moveToBottom(row.key); rowMenu = null })
                SheetRow(
                    "Move this title to bottom",
                    onClick = { vm.moveGroupToBottom(row.mediaId); rowMenu = null }
                )
                if (row.isPaused || row.status == DownloadStatus.WAITING) {
                    SheetRow("Resume", onClick = { vm.resume(row.key); rowMenu = null })
                } else {
                    SheetRow("Pause", onClick = { vm.pause(row.key); rowMenu = null })
                }
                SheetRow("Cancel", onClick = { pendingCancel = row; rowMenu = null })
                SheetRow(
                    "Cancel all for this title",
                    onClick = {
                        groupMenu = null
                        pendingCancelGroup = state.groups.firstOrNull { group ->
                            group.rows.any { it.key == row.key }
                        }
                        rowMenu = null
                    }
                )
            }
            SheetRow("Open title", onClick = { vm.openMedia(row.mediaId); rowMenu = null })
            if (row.status == DownloadStatus.FAILED || row.status == DownloadStatus.CANCELED) {
                SheetRow("Retry", onClick = { vm.retry(row.key); rowMenu = null })
            }
            if (row.status == DownloadStatus.COMPLETED ||
                row.status == DownloadStatus.FAILED ||
                row.status == DownloadStatus.CANCELED
            ) {
                SheetRow(
                    title = if (row.status == DownloadStatus.COMPLETED) {
                        "Delete files"
                    } else {
                        "Remove from queue"
                    },
                    onClick = { pendingRemove = row; rowMenu = null }
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    groupMenu?.let { group ->
        GlassSheet(onDismiss = { groupMenu = null }) {
            AFText(
                text = group.title,
                style = AFTheme.type.title,
                color = AFTheme.colors.textHi,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            SheetRow(
                "Move this title to top",
                onClick = { vm.moveGroupToTop(group.mediaId); groupMenu = null }
            )
            SheetRow(
                "Move this title to bottom",
                onClick = { vm.moveGroupToBottom(group.mediaId); groupMenu = null }
            )
            SheetRow("Open title", onClick = { vm.openMedia(group.mediaId); groupMenu = null })
            SheetRow("Pause all", onClick = { vm.pauseAll(group.rows); groupMenu = null })
            SheetRow("Resume all", onClick = { vm.resumeAll(group.rows); groupMenu = null })
            SheetRow(
                "Cancel all",
                onClick = { pendingCancelGroup = group; groupMenu = null }
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showConcurrency) {
        GlassSheet(onDismiss = { showConcurrency = false }) {
            AFText(
                text = "Downloads at the same time",
                style = AFTheme.type.title,
                color = AFTheme.colors.textHi
            )
            Spacer(Modifier.height(12.dp))
            GlassSegmented(
                options = (1..4).map { it.toString() },
                selectedIndex = (state.concurrent - 1).coerceIn(0, 3),
                onSelect = { vm.setConcurrent(it + 1) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            AFText(
                text = "More parallel files finish a title sooner but compete for bandwidth.",
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    pendingCancel?.let { row ->
        GlassDialog(
            title = "Cancel this download?",
            body = "\"${row.unitTitle}\" stops downloading and its partial files are deleted.",
            confirmLabel = "Cancel download",
            destructive = true,
            onConfirm = {
                vm.cancel(row.key)
                pendingCancel = null
            },
            onDismiss = { pendingCancel = null },
            dismissLabel = "Keep it"
        )
    }

    pendingCancelGroup?.let { group ->
        GlassDialog(
            title = "Cancel this title's queue?",
            body = "Every active part of \"${group.title}\" stops and its partial files are " +
                "deleted. Completed parts stay on this device.",
            confirmLabel = "Cancel ${group.active.size}",
            destructive = true,
            onConfirm = {
                vm.cancelAll(group.rows)
                pendingCancelGroup = null
            },
            onDismiss = { pendingCancelGroup = null },
            dismissLabel = "Keep them"
        )
    }

    pendingRemove?.let { row ->
        GlassDialog(
            title = "Delete downloaded files?",
            body = "\"${row.unitTitle}\" (${formatBytes(row.totalBytes)}) is removed from this " +
                "device. The title stays in your library.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                vm.remove(row.key)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null },
            dismissLabel = "Keep"
        )
    }

    if (confirmClearQueue) {
        GlassDialog(
            title = "Cancel the whole queue?",
            body = "Every unfinished download stops and its partial files are deleted. " +
                "Completed downloads stay on this device.",
            confirmLabel = "Cancel all",
            destructive = true,
            onConfirm = {
                vm.clearQueue()
                confirmClearQueue = false
            },
            onDismiss = { confirmClearQueue = false },
            dismissLabel = "Keep them"
        )
    }

    if (confirmClear) {
        GlassDialog(
            title = "Clear finished downloads?",
            body = "Completed files are deleted from this device, and failed or cancelled " +
                "entries disappear. Titles stay in your library.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                vm.clearFinished()
                confirmClear = false
            },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun DownloadGroupPanel(
    group: DownloadGroup,
    onOpenGroupMenu: () -> Unit,
    onOpenRowMenu: (DownloadEntity) -> Unit,
    onOpenTitle: () -> Unit
) {
    GlassPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Open ${group.title}"
                ) { onOpenTitle() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                AFText(
                    text = group.title,
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    onGlass = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AFText(
                    text = groupSummary(group),
                    style = AFTheme.type.caption,
                    color = if (group.failed.isNotEmpty()) AFTheme.colors.failed
                    else AFTheme.colors.textLo,
                    onGlass = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            AFText(
                text = group.rows.size.toString(),
                style = AFTheme.type.numeric,
                color = AFTheme.colors.textLo,
                onGlass = true
            )
            AFIconButton(
                icon = AFIcons.More,
                contentDescription = "Actions for ${group.title}",
                onClick = onOpenGroupMenu
            )
        }
        val progress = group.progress
        if (progress != null && group.active.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            GlassProgress(progress = progress / 100f, height = 3.dp)
        }
        Spacer(Modifier.height(6.dp))
        group.rows.forEachIndexed { index, row ->
            if (index > 0) {
                Spacer(Modifier.height(2.dp))
            }
            DownloadRow(
                row = row,
                onOpenMenu = { onOpenRowMenu(row) },
                onOpenTitle = onOpenTitle
            )
        }
    }
}

@Composable
private fun DownloadRow(
    row: DownloadEntity,
    onOpenMenu: () -> Unit,
    onOpenTitle: () -> Unit
) {
    val colors = AFTheme.colors
    val haptics = rememberHaptics()
    val tone = when (row.status) {
        DownloadStatus.COMPLETED -> colors.ok
        DownloadStatus.FAILED -> colors.failed
        DownloadStatus.CANCELED -> colors.textLo
        DownloadStatus.WAITING -> colors.textMid
        else -> colors.accent
    }
    val icon = when (row.status) {
        DownloadStatus.COMPLETED -> AFIcons.Check
        DownloadStatus.FAILED -> AFIcons.Close
        DownloadStatus.CANCELED -> AFIcons.Close
        DownloadStatus.WAITING -> AFIcons.Pause
        else -> AFIcons.Download
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Open ${row.unitTitle}"
            ) {
                haptics.tick()
                onOpenTitle()
            }
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AFIcon(icon, null, tint = tone, size = 18.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AFText(
                    text = row.unitTitle,
                    style = AFTheme.type.label,
                    color = colors.textHi,
                    onGlass = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AFText(
                    text = statusText(row),
                    style = AFTheme.type.caption,
                    color = if (row.status == DownloadStatus.FAILED) colors.failed else colors.textLo,
                    onGlass = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (row.status == DownloadStatus.RUNNING && row.progress in 0..100) {
                AFText(
                    text = "${row.progress}%",
                    style = AFTheme.type.numeric,
                    color = colors.accent,
                    onGlass = true
                )
            }
            AFIconButton(
                icon = AFIcons.More,
                contentDescription = "Actions for ${row.unitTitle}",
                onClick = onOpenMenu,
                visualSize = 40.dp
            )
        }
        if (row.status == DownloadStatus.RUNNING) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.padding(start = 30.dp, end = 44.dp)) {
                if (row.progress in 0..100) {
                    GlassProgress(progress = row.progress / 100f, height = 3.dp)
                } else {
                    GlassProgress(progress = 0.35f, height = 3.dp)
                }
            }
        }
    }
}

private fun groupSummary(group: DownloadGroup): String {
    if (group.active.isEmpty()) {
        return when {
            group.completed.size == group.rows.size -> "${group.completed.size} saved"
            group.failed.isNotEmpty() -> buildString {
                append("${group.failed.size} failed")
                if (group.completed.isNotEmpty()) append(" Â· ${group.completed.size} saved")
            }
            else -> "${group.rows.size} items"
        }
    }
    val paused = group.active.count { it.isPaused }
    val downloading = group.active.count { it.status == DownloadStatus.RUNNING }
    val queued = group.active.count {
        it.status == DownloadStatus.QUEUED && !it.isPaused
    }
    val waiting = (group.active.size - paused - downloading - queued).coerceAtLeast(0)
    val parts = buildList {
        if (downloading > 0) add("$downloading downloading")
        if (queued > 0) add("$queued queued")
        if (paused > 0) add("$paused paused")
        if (waiting > 0) add("$waiting waiting")
        group.progress?.let { add("$it%") }
        if (group.completed.isNotEmpty()) add("${group.completed.size} saved")
    }
    return parts.joinToString(" Â· ")
}

private fun statusText(row: DownloadEntity): String = when (row.status) {
    DownloadStatus.RUNNING -> buildString {
        if (row.progress in 0..100) append("Downloading ${row.progress}%")
        else append("Downloading")
        if (row.totalBytes > 0) {
            append(" Â· ${formatBytes(row.downloadedBytes)} of ${formatBytes(row.totalBytes)}")
        } else if (row.downloadedBytes > 0) {
            append(" Â· ${formatBytes(row.downloadedBytes)}")
        }
    }
    DownloadStatus.QUEUED -> row.note ?: "Queued"
    DownloadStatus.WAITING -> row.note ?: "Paused"
    DownloadStatus.COMPLETED -> buildString {
        append("Completed")
        row.completedAt?.let {
            append(" Â· ")
            append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)))
        }
        if (row.totalBytes > 0) append(" Â· ${formatBytes(row.totalBytes)}")
    }
    DownloadStatus.FAILED -> row.error ?: "Failed"
    DownloadStatus.CANCELED -> "Cancelled"
}
