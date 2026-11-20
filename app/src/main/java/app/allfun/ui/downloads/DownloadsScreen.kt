package app.allfun.ui.downloads

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.formatBytes
import java.text.DateFormat
import java.util.Date

/**
 * Downloads → the live queue.
 *
 * Grouped by title like Komikku's download queue: each group reports how much of the
 * title is saved and can pause, resume or cancel its own items. Every menu entry maps
 * to a real queue operation; sort order and the Wi-Fi/concurrency rules are handled
 * here instead of a separate settings page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm = rememberViewModel { DownloadsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingCancel by remember { mutableStateOf<DownloadEntity?>(null) }
    var pendingRemove by remember { mutableStateOf<DownloadEntity?>(null) }
    var pendingCancelGroup by remember { mutableStateOf<DownloadGroup?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showConcurrency by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Download queue")
                        if (state.activeCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "${state.activeCount}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = "Sort queue")
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            DownloadSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = {
                                        if (state.sort == option) {
                                            Icon(Icons.Outlined.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.size(24.dp))
                                        }
                                    },
                                    onClick = {
                                        vm.setSort(option)
                                        sortOpen = false
                                    }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Queue options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
            )
        },
        floatingActionButton = {
            if (state.pausedCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = vm::resumePaused,
                    icon = {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "Resume paused downloads"
                        )
                    },
                    text = { Text("Resume") }
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
                        message = "Open a manga chapter, a novel chapter or a film and tap " +
                            "Download. Progress and failures are shown here with what really " +
                            "happened."
                    )
                }
            }

            state.groups.forEach { group ->
                item(key = "group:${group.title}") {
                    GroupHeader(
                        group = group,
                        onPauseAll = { vm.pauseAll(group.rows) },
                        onResumeAll = { vm.resumeAll(group.rows) },
                        onCancelAll = { pendingCancelGroup = group }
                    )
                }
                items(group.rows, key = { it.key }) { row ->
                    DownloadRow(
                        row = row,
                        onPause = { vm.pause(row.key) },
                        onResume = { vm.resume(row.key) },
                        onRetry = { vm.retry(row.key) },
                        onCancel = { pendingCancel = row },
                        onRemove = { pendingRemove = row }
                    )
                }
                item(key = "group-divider:${group.title}") {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
                    )
                }
            }

            if (state.rows.isNotEmpty()) {
                item("storage") {
                    Text(
                        text = "${formatBytes(state.completedBytes)} stored on this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }

    if (showConcurrency) {
        AlertDialog(
            onDismissRequest = { showConcurrency = false },
            title = { Text("Downloads at the same time") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    IconButton(
                        onClick = { vm.setConcurrent(state.concurrent + 1) },
                        enabled = state.concurrent < 4
                    ) { Icon(Icons.Outlined.Add, contentDescription = "More downloads") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConcurrency = false }) { Text("Done") }
            }
        )
    }

    pendingCancel?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("Cancel this download?") },
            text = {
                Text(
                    "\"${row.unitTitle}\" stops downloading and its partial files are deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.cancel(row.key)
                        pendingCancel = null
                    }
                ) { Text("Cancel download", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = null }) { Text("Keep it") }
            }
        )
    }

    pendingCancelGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { pendingCancelGroup = null },
            title = { Text("Cancel this queue group?") },
            text = {
                Text(
                    "Every active part of \"${group.title}\" stops and its partial files are " +
                        "deleted. Completed parts stay on this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.cancelAll(group.rows)
                        pendingCancelGroup = null
                    }
                ) { Text("Cancel ${group.active.size}", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancelGroup = null }) { Text("Keep them") }
            }
        )
    }

    pendingRemove?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Delete downloaded files?") },
            text = {
                Text(
                    "\"${row.unitTitle}\" (${formatBytes(row.totalBytes)}) is removed from this " +
                        "device. The title stays in your library."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.remove(row.key)
                        pendingRemove = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Keep") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear finished downloads?") },
            text = {
                Text(
                    "Completed files are deleted from this device, and failed or cancelled " +
                        "entries disappear. Titles stay in your library."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearFinished()
                        confirmClear = false
                    }
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GroupHeader(
    group: DownloadGroup,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAll: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = groupSummary(group),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Actions for ${group.title}")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Pause all") },
                    enabled = group.active.any { it.status == DownloadStatus.RUNNING ||
                        it.status == DownloadStatus.QUEUED },
                    onClick = {
                        onPauseAll()
                        menu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Resume all") },
                    enabled = group.rows.any {
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
}

@Composable
private fun DownloadRow(
    row: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.unitTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText(row),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.status == DownloadStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Actions for ${row.unitTitle}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (row.status.isActive) {
                        if (row.status == DownloadStatus.WAITING) {
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
                    }
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
                                    color = MaterialTheme.colorScheme.error
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
            Spacer(Modifier.height(8.dp))
            if (row.progress in 0..100) {
                LinearProgressIndicator(
                    progress = { row.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                )
            }
        }
    }
}

private fun groupSummary(group: DownloadGroup): String = buildString {
    val progress = group.progress
    val paused = group.active.count {
        it.status == DownloadStatus.WAITING && it.note == "Paused"
    }
    val running = group.active.size - paused
    when {
        group.active.isNotEmpty() -> {
            if (running > 0) append("$running active")
            if (paused > 0) {
                if (isNotEmpty()) append(" · ")
                append("$paused paused")
            }
            if (progress != null) append(" · $progress%")
            val saved = group.completed.size
            if (saved > 0) append(" · $saved saved")
        }
        group.completed.size == group.rows.size -> {
            append("${group.completed.size} saved")
        }
        group.failed.isNotEmpty() -> {
            append("${group.failed.size} failed")
            if (group.completed.isNotEmpty()) append(" · ${group.completed.size} saved")
        }
        else -> append("${group.rows.size} items")
    }
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
