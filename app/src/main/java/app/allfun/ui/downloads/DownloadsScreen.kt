package app.allfun.ui.downloads

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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

/** Settings → Downloads: the real queue, its progress and the rules it follows. */
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
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.completed.isNotEmpty() || state.failed.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text("Clear finished") }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item("summary") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = buildString {
                            append("${state.active.size} active")
                            append(" · ${state.completed.size} completed")
                            if (state.failed.isNotEmpty()) append(" · ${state.failed.size} failed")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${formatBytes(state.completedBytes)} stored on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item("rules") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Download only on Wi-Fi", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Queued items wait and show why instead of using mobile data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.wifiOnly,
                            onCheckedChange = vm::setWifiOnly
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("At the same time", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "How many files transfer concurrently.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { vm.setConcurrent(state.concurrent - 1) },
                            enabled = state.concurrent > 1
                        ) { Icon(Icons.Outlined.Remove, contentDescription = "Fewer downloads") }
                        Text(
                            text = "${state.concurrent}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(
                            onClick = { vm.setConcurrent(state.concurrent + 1) },
                            enabled = state.concurrent < 4
                        ) { Icon(Icons.Outlined.Add, contentDescription = "More downloads") }
                    }
                }
            }

            item("divider") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

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

            if (state.active.isNotEmpty()) {
                item("active-header") { SectionTitle("Active") }
                items(state.active, key = { it.key }) { row ->
                    DownloadRow(
                        row = row,
                        onPause = { vm.pause(row.key) },
                        onResume = { vm.resume(row.key) },
                        onCancel = { pendingCancel = row }
                    )
                }
            }

            if (state.failed.isNotEmpty()) {
                item("failed-header") { SectionTitle("Failed and cancelled") }
                items(state.failed, key = { it.key }) { row ->
                    DownloadRow(
                        row = row,
                        onRetry = { vm.retry(row.key) },
                        onRemove = { pendingRemove = row }
                    )
                }
            }

            if (state.completed.isNotEmpty()) {
                item("completed-header") { SectionTitle("Completed") }
                items(state.completed, key = { it.key }) { row ->
                    DownloadRow(
                        row = row,
                        onRemove = { pendingRemove = row }
                    )
                }
            }
        }
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun DownloadRow(
    row: DownloadEntity,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .padding(0.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (row.status) {
                        DownloadStatus.COMPLETED -> Icons.Outlined.DownloadDone
                        DownloadStatus.FAILED -> Icons.Outlined.Close
                        DownloadStatus.CANCELED -> Icons.Outlined.Cancel
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
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.unitTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.mediaTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            when {
                onPause != null -> IconButton(onClick = onPause) {
                    Icon(Icons.Outlined.Pause, contentDescription = "Pause ${row.unitTitle}")
                }
                onResume != null -> IconButton(onClick = onResume) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume ${row.unitTitle}")
                }
                onRetry != null -> IconButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Retry ${row.unitTitle}")
                }
            }
            if (onCancel != null) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Cancel, contentDescription = "Cancel ${row.unitTitle}")
                }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete files for ${row.unitTitle}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        if (row.status == DownloadStatus.RUNNING && row.progress in 0..100) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { row.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
            )
        } else if (row.status == DownloadStatus.RUNNING) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
            )
        }
    }
}

private fun statusText(row: DownloadEntity): String = when (row.status) {
    DownloadStatus.RUNNING -> buildString {
        if (row.progress in 0..100) append("Downloading ${row.progress}%")
        else append("Downloading")
        if (row.totalBytes > 0) append(" · ${formatBytes(row.downloadedBytes)} of ${formatBytes(row.totalBytes)}")
        else if (row.downloadedBytes > 0) append(" · ${formatBytes(row.downloadedBytes)}")
    }
    DownloadStatus.QUEUED -> "Queued"
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
