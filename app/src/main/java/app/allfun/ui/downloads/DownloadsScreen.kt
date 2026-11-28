package app.allfun.ui.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.download.QueueOrder
import app.allfun.ui.components.AllFunButton
import app.allfun.ui.components.AllFunButtonVariant
import app.allfun.ui.components.AllFunSheet
import app.allfun.ui.components.AnimatedProgress
import app.allfun.ui.components.AppTopBar
import app.allfun.ui.components.EmptyIllustration
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.Overline
import app.allfun.ui.components.TopBarAction
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.components.staggeredReveal
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.AllFunType
import app.allfun.ui.formatBytes
import java.text.DateFormat
import java.util.Date

/**
 * Downloads → the live queue, with Komikku's behaviour.
 *
 * The queue is explicit and persisted: every item has a position that drag-free menu
 * actions ("move to top"/"move to bottom"), whole-title moves and the sort menu rewrite.
 * One floating button pauses or resumes the downloader; the overflow can cancel the whole
 * queue. Completed downloads stay because they are real files the Library reads offline.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenMedia: (String) -> Unit
) {
    val vm = rememberViewModel { DownloadsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val openMediaId by vm.openMediaId.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingCancel by remember { mutableStateOf<DownloadEntity?>(null) }
    var pendingRemove by remember { mutableStateOf<DownloadEntity?>(null) }
    var pendingCancelGroup by remember { mutableStateOf<DownloadGroup?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearQueue by remember { mutableStateOf(false) }
    var showConcurrency by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(openMediaId) {
        openMediaId?.let { id ->
            onOpenMedia(id)
            vm.clearOpen()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                AppTopBar(
                    title = "Downloads",
                    meta = when {
                        state.activeCount > 0 -> "${state.activeCount} downloading"
                        state.rows.isEmpty() -> "Nothing queued"
                        else -> "${state.rows.size} in the queue"
                    },
                    onBack = onBack,
                    actions = {
                        if (state.canToggleQueue) {
                            TopBarAction(
                                icon = if (state.paused) Icons.Outlined.PlayArrow
                                else Icons.Outlined.Pause,
                                contentDescription = if (state.paused) {
                                    "Resume downloads"
                                } else {
                                    "Pause downloads"
                                },
                                onClick = vm::toggleQueue
                            )
                        }
                        if (state.rows.isNotEmpty()) {
                            Box {
                                TopBarAction(
                                    icon = Icons.Outlined.Sort,
                                    contentDescription = "Order the queue",
                                    onClick = { sortOpen = true }
                                )
                                DropdownMenu(
                                    expanded = sortOpen,
                                    onDismissRequest = { sortOpen = false }
                                ) {
                                    QueueOrder.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                vm.sortQueue(option)
                                                sortOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            Box {
                                TopBarAction(
                                    icon = Icons.Outlined.MoreVert,
                                    contentDescription = "Queue options",
                                    onClick = { menuOpen = true }
                                )
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Cancel all") },
                                    enabled = state.activeCount > 0,
                                    onClick = {
                                        confirmClearQueue = true
                                        menuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Download only on Wi-Fi") },
                                    trailingIcon = {
                                        if (state.wifiOnly) {
                                            Icon(Icons.Outlined.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        vm.setWifiOnly(!state.wifiOnly)
                                        menuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Download at the same time: ${state.concurrent}") },
                                    onClick = {
                                        showConcurrency = true
                                        menuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh queue") },
                                    onClick = {
                                        vm.refresh()
                                        menuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear finished") },
                                    enabled = state.completed.isNotEmpty() || state.failed.isNotEmpty(),
                                    onClick = {
                                        confirmClear = true
                                        menuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            if (state.rows.isEmpty()) {
                item("empty") {
                    EmptyState(
                        icon = Icons.Outlined.Download,
                        title = "Nothing queued",
                        message = "Open a chapter, a book or a film and choose Download. " +
                            "Progress and failures are shown here with what really happened.",
                        illustration = EmptyIllustration.CARDS
                    )
                }
            }

            if (state.rows.isNotEmpty()) {
                item("summary") {
                    DownloadSummary(
                        active = state.activeCount,
                        saved = state.completed.size,
                        failed = state.failed.size,
                        paused = state.paused,
                        concurrent = state.concurrent
                    )
                }
            }

            state.groups.forEachIndexed { groupIndex, group ->
                stickyHeader(key = "group:${group.mediaId}") {
                    GroupHeader(
                        group = group,
                        onPauseAll = { vm.pauseAll(group.rows) },
                        onResumeAll = { vm.resumeAll(group.rows) },
                        onCancelAll = { pendingCancelGroup = group },
                        onMoveTop = { vm.moveGroupToTop(group.mediaId) },
                        onMoveBottom = { vm.moveGroupToBottom(group.mediaId) },
                        onShow = { vm.openMedia(group.mediaId) }
                    )
                }
                itemsIndexed(group.rows, key = { _, row -> row.key }) { index, row ->
                    DownloadRow(
                        row = row,
                        onMoveTop = { vm.moveToTop(row.key) },
                        onMoveBottom = { vm.moveToBottom(row.key) },
                        onMoveGroupTop = { vm.moveGroupToTop(group.mediaId) },
                        onMoveGroupBottom = { vm.moveGroupToBottom(group.mediaId) },
                        onShow = { vm.openMedia(group.mediaId) },
                        onCancelGroup = { pendingCancelGroup = group },
                        onPause = { vm.pause(row.key) },
                        onResume = { vm.resume(row.key) },
                        onRetry = { vm.retry(row.key) },
                        onCancel = { pendingCancel = row },
                        onRemove = { pendingRemove = row },
                        modifier = Modifier
                            .staggeredReveal(index + groupIndex)
                            .animateItem()
                    )
                }
            }

            if (state.rows.isNotEmpty()) {
                item("storage") {
                    Text(
                        text = "${formatBytes(state.completedBytes)} stored on this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = AllFun.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }
            }
        }
    }

    if (showConcurrency) {
        AllFunSheet(
            title = "Downloads at the same time",
            onDismiss = { showConcurrency = false }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { vm.setConcurrent(state.concurrent - 1) },
                    enabled = state.concurrent > 1
                ) { Icon(Icons.Outlined.Remove, contentDescription = "Fewer downloads") }
                Text(
                    text = "${state.concurrent}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AllFun.colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                IconButton(
                    onClick = { vm.setConcurrent(state.concurrent + 1) },
                    enabled = state.concurrent < 4
                ) { Icon(Icons.Outlined.Add, contentDescription = "More downloads") }
            }
            Text(
                text = "Between 1 and 4 files download at once.",
                style = MaterialTheme.typography.bodySmall,
                color = AllFun.colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
            AllFunButton(
                text = "Done",
                onClick = { showConcurrency = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            )
        }
    }

    pendingCancel?.let { row ->
        ConfirmSheet(
            title = "Cancel this download?",
            body = "\"${row.unitTitle}\" stops downloading and its partial files are deleted.",
            confirmLabel = "Cancel download",
            dismissLabel = "Keep it",
            onConfirm = {
                vm.cancel(row.key)
                pendingCancel = null
            },
            onDismiss = { pendingCancel = null }
        )
    }

    pendingCancelGroup?.let { group ->
        ConfirmSheet(
            title = "Cancel this title's queue?",
            body = "Every active part of \"${group.title}\" stops and its partial files are " +
                "deleted. Completed parts stay on this device.",
            confirmLabel = "Cancel ${group.active.size}",
            dismissLabel = "Keep them",
            onConfirm = {
                vm.cancelAll(group.rows)
                pendingCancelGroup = null
            },
            onDismiss = { pendingCancelGroup = null }
        )
    }

    pendingRemove?.let { row ->
        ConfirmSheet(
            title = "Delete downloaded files?",
            body = "\"${row.unitTitle}\" (${formatBytes(row.totalBytes)}) is removed from this " +
                "device. The title stays in your library.",
            confirmLabel = "Delete",
            dismissLabel = "Keep",
            onConfirm = {
                vm.remove(row.key)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null }
        )
    }

    if (confirmClearQueue) {
        ConfirmSheet(
            title = "Cancel the whole queue?",
            body = "Every unfinished download stops and its partial files are deleted. " +
                "Completed downloads stay on this device.",
            confirmLabel = "Cancel all",
            dismissLabel = "Keep them",
            onConfirm = {
                vm.clearQueue()
                confirmClearQueue = false
            },
            onDismiss = { confirmClearQueue = false }
        )
    }

    if (confirmClear) {
        ConfirmSheet(
            title = "Clear finished downloads?",
            body = "Completed files are deleted from this device, and failed or cancelled " +
                "entries disappear. Titles stay in your library.",
            confirmLabel = "Clear",
            dismissLabel = "Cancel",
            onConfirm = {
                vm.clearFinished()
                confirmClear = false
            },
            onDismiss = { confirmClear = false }
        )
    }
}

/** Destructive confirmations share one sheet shape, so they never look like alerts. */
@Composable
private fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AllFunSheet(title = title, onDismiss = onDismiss) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = AllFun.colors.textSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AllFunButton(
                text = dismissLabel,
                onClick = onDismiss,
                variant = AllFunButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
            AllFunButton(
                text = confirmLabel,
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GroupHeader(
    group: DownloadGroup,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAll: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onShow: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    // The sticky header carries the group's own progress, so a title keeps answering
    // "how far along is this?" even when its rows have scrolled away.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AllFun.colors.bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
                color = AllFun.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${group.rows.size}",
                style = AllFunType.mono,
                color = AllFun.colors.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "Actions for ${group.title}",
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Move this title to top") },
                        enabled = group.active.isNotEmpty(),
                        onClick = {
                            onMoveTop()
                            menu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move this title to bottom") },
                        enabled = group.active.isNotEmpty(),
                        onClick = {
                            onMoveBottom()
                            menu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open title") },
                        onClick = {
                            onShow()
                            menu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Pause all") },
                        enabled = group.active.any {
                            it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
                        },
                        onClick = {
                            onPauseAll()
                            menu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Resume all") },
                        enabled = group.rows.any {
                            it.isPaused ||
                                it.status == DownloadStatus.WAITING ||
                                it.status == DownloadStatus.FAILED ||
                                it.status == DownloadStatus.CANCELED
                        },
                        onClick = {
                            onResumeAll()
                            menu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Cancel all") },
                        enabled = group.active.isNotEmpty(),
                        onClick = {
                            onCancelAll()
                            menu = false
                        }
                    )
                }
            }
        }
        Text(
            text = groupSummary(group),
            style = AllFunType.mono,
            color = AllFun.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp)
        )
        val progress = group.progress
        if (progress != null && group.active.isNotEmpty()) {
            AnimatedProgress(
                progress = progress / 100f,
                modifier = Modifier.padding(horizontal = 12.dp),
                height = 3
            )
            Spacer(Modifier.height(6.dp))
        } else {
            HorizontalDivider(color = AllFun.colors.hairline.copy(alpha = 0.5f))
        }
    }
}

/**
 * A one-glance answer to "what is the queue doing?" before the groups start: how many
 * parts are moving, how many are on disk, and how many need attention.
 */
@Composable
private fun DownloadSummary(
    active: Int,
    saved: Int,
    failed: Int,
    paused: Boolean,
    concurrent: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
    ) {
        Overline("Queue")
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildList {
                add(
                    when {
                        paused -> "paused"
                        active == 0 -> "idle"
                        active == 1 -> "1 downloading"
                        else -> "$active downloading"
                    }
                )
                add("$saved saved")
                if (failed > 0) add("$failed failed")
                add("$concurrent at a time")
            }.joinToString(" · "),
            style = AllFunType.mono,
            color = if (failed > 0) AllFun.colors.danger else AllFun.colors.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = AllFun.colors.hairline)
    }
}

@Composable
private fun DownloadRow(
    row: DownloadEntity,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onMoveGroupTop: () -> Unit,
    onMoveGroupBottom: () -> Unit,
    onShow: () -> Unit,
    onCancelGroup: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(AllFun.colors.surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (row.status) {
                        DownloadStatus.COMPLETED -> Icons.Outlined.DownloadDone
                        DownloadStatus.FAILED -> Icons.Outlined.Close
                        DownloadStatus.CANCELED -> Icons.Outlined.Cancel
                        DownloadStatus.WAITING -> Icons.Outlined.Pause
                        else -> Icons.Outlined.Download
                    },
                    contentDescription = null,
                    tint = when (row.status) {
                        DownloadStatus.COMPLETED -> AllFun.colors.accent
                        DownloadStatus.FAILED -> AllFun.colors.danger
                        else -> AllFun.colors.accent
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.unitTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText(row),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.status == DownloadStatus.FAILED) {
                        AllFun.colors.danger
                    } else {
                        AllFun.colors.textSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (row.status == DownloadStatus.RUNNING && row.progress in 0..100) {
                Text(
                    text = "${row.progress}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = AllFun.colors.accent
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "Actions for ${row.unitTitle}",
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (row.status.isActive) {
                        // Same actions as Komikku's per-download menu.
                        DropdownMenuItem(
                            text = { Text("Move to top") },
                            onClick = {
                                onMoveTop()
                                menu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move this title to top") },
                            onClick = {
                                onMoveGroupTop()
                                menu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to bottom") },
                            onClick = {
                                onMoveBottom()
                                menu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move this title to bottom") },
                            onClick = {
                                onMoveGroupBottom()
                                menu = false
                            }
                        )
                        if (row.isPaused || row.status == DownloadStatus.WAITING) {
                            DropdownMenuItem(
                                text = { Text("Resume") },
                                onClick = {
                                    onResume()
                                    menu = false
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Pause") },
                                onClick = {
                                    onPause()
                                    menu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Cancel") },
                            onClick = {
                                onCancel()
                                menu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel all for this title") },
                            onClick = {
                                onCancelGroup()
                                menu = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Open title") },
                        onClick = {
                            onShow()
                            menu = false
                        }
                    )
                    if (row.status == DownloadStatus.FAILED ||
                        row.status == DownloadStatus.CANCELED
                    ) {
                        DropdownMenuItem(
                            text = { Text("Retry") },
                            onClick = {
                                onRetry()
                                menu = false
                            }
                        )
                    }
                    if (row.status == DownloadStatus.COMPLETED ||
                        row.status == DownloadStatus.FAILED ||
                        row.status == DownloadStatus.CANCELED
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (row.status == DownloadStatus.COMPLETED) {
                                        "Delete files"
                                    } else {
                                        "Remove from queue"
                                    },
                                    color = AllFun.colors.danger
                                )
                            },
                            onClick = {
                                onRemove()
                                menu = false
                            }
                        )
                    }
                }
            }
        }
        if (row.status == DownloadStatus.RUNNING) {
            Spacer(Modifier.height(4.dp))
            if (row.progress in 0..100) {
                AnimatedProgress(
                    progress = row.progress / 100f,
                    modifier = Modifier.padding(start = 44.dp, end = 44.dp),
                    height = 3
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(start = 44.dp, end = 44.dp),
                    color = AllFun.colors.accent,
                    trackColor = AllFun.colors.surface2
                )
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
                if (group.completed.isNotEmpty()) append(" · ${group.completed.size} saved")
            }
            else -> "${group.rows.size} items"
        }
    }
    val paused = group.active.count { it.isPaused }
    val downloading = group.active.count { it.status == DownloadStatus.RUNNING }
    val queued = group.active.count {
        it.status == DownloadStatus.QUEUED && !it.isPaused
    }
    // Anything left is waiting on a condition such as Wi-Fi rather than on the user.
    val waiting = (group.active.size - paused - downloading - queued).coerceAtLeast(0)
    val parts = buildList {
        if (downloading > 0) add("$downloading downloading")
        if (queued > 0) add("$queued queued")
        if (paused > 0) add("$paused paused")
        if (waiting > 0) add("$waiting waiting")
        group.progress?.let { add("$it%") }
        if (group.completed.isNotEmpty()) add("${group.completed.size} saved")
    }
    return parts.joinToString(" · ")
}

private fun statusText(row: DownloadEntity): String = when (row.status) {
    DownloadStatus.RUNNING -> buildString {
        if (row.progress in 0..100) append("Downloading ${row.progress}%")
        else append("Downloading")
        if (row.totalBytes > 0) {
            append(" · ${formatBytes(row.downloadedBytes)} of ${formatBytes(row.totalBytes)}")
        } else if (row.downloadedBytes > 0) {
            append(" · ${formatBytes(row.downloadedBytes)}")
        }
    }
    DownloadStatus.QUEUED -> row.note ?: "Queued"
    DownloadStatus.WAITING -> row.note ?: "Paused"
    DownloadStatus.COMPLETED -> buildString {
        append("Completed")
        row.completedAt?.let {
            append(" · ")
            append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)))
        }
        if (row.totalBytes > 0) append(" · ${formatBytes(row.totalBytes)}")
    }
    DownloadStatus.FAILED -> row.error ?: "Failed"
    DownloadStatus.CANCELED -> "Cancelled"
}
