package app.allfun.ui.browse

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.model.MediaStub
import app.allfun.ui.components.DiscoveryCard
import app.allfun.ui.components.EmptyState
import app.allfun.ui.components.ErrorState
import app.allfun.ui.components.GridSkeleton
import app.allfun.ui.components.LoadingState
import app.allfun.ui.components.rememberViewModel

private enum class AddDialog { NONE, STREMIO, JELLYFIN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    container: AppContainer,
    onOpenMedia: (String) -> Unit,
    onManageExtensions: () -> Unit,
    onBack: () -> Unit
) {
    val vm = rememberViewModel { BrowseViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(AddDialog.NONE) }

    val epubPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importEpub(it) } }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.importVideos(it) } }

    val extensionPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importExtension(it) } }

    val novelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importNovelPlugin(it) } }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        vm.openMediaId.collect { id ->
            if (id != null) {
                onOpenMedia(id)
                vm.clearOpen()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(text = "Sources", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Connect a provider, then its titles appear in the three categories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                IconButton(onClick = onManageExtensions) {
                    Icon(Icons.Outlined.Extension, contentDescription = "Manage extensions")
                }
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add a source")
                }
            }

            val selected = state.sources.firstOrNull { it.id == state.selectedSourceId }
            val selectedType = state.selectedSource?.type ?: selected?.type
            val summary = if (selectedType == null) "" else buildString {
                append(vm.typeLabel(selectedType))
                if (state.capabilities.isNotEmpty()) {
                    append(" · ")
                    append(state.capabilities.joinToString(" · "))
                }
            }
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }

            if (state.supportsSearch) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = {
                        Text("Search ${selected?.name ?: "this source"}")
                    },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.submitSearch() })
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.sources.size) { index ->
                    val source = state.sources[index]
                    FilterChip(
                        selected = source.id == state.selectedSourceId,
                        onClick = { vm.selectSource(source.id) },
                        label = { Text(source.name, maxLines = 1) }
                    )
                }
            }

            if (state.catalogs.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.catalogs.size) { index ->
                        val catalog = state.catalogs[index]
                        FilterChip(
                            selected = catalog.id == state.selectedCatalog,
                            onClick = { vm.selectCatalog(catalog.id) },
                            label = { Text(catalog.name) }
                        )
                    }
                }
            }

            if (state.opening) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(Modifier.fillMaxSize()) {
                val error = state.error
                when {
                    state.loading -> Column(Modifier.fillMaxSize()) {
                        LoadingState("Loading ${selected?.name ?: "source"}")
                        GridSkeleton(count = 9)
                    }

                    error != null && state.results.isEmpty() -> ErrorState(
                        error = error,
                        onRetry = { vm.retry() },
                        secondary = "Manage extensions" to onManageExtensions
                    )

                    state.results.isEmpty() -> EmptyState(
                        icon = if (state.supportsSearch) Icons.Outlined.Search else Icons.Outlined.Extension,
                        title = when {
                            state.query.isNotBlank() -> "No results"
                            !state.supportsCatalogs -> "No catalogue for this source"
                            else -> "Nothing to show"
                        },
                        message = when {
                            state.query.isNotBlank() ->
                                "No results for \"${state.query}\". Try a shorter search."
                            !state.supportsCatalogs ->
                                "This source is library-only: it does not advertise a browse " +
                                    "catalogue. Search still works if the source supports it."
                            else -> "This source returned an empty catalogue. Try again later, or " +
                                "add another source."
                        },
                        actionLabel = if (state.supportsCatalogs) "Try again" else "Manage extensions",
                        onAction = if (state.supportsCatalogs) {
                            { vm.retry() }
                        } else {
                            onManageExtensions
                        }
                    )

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(120.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (error != null) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = error.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { vm.clearError() }) { Text("Dismiss") }
                                }
                            }
                        }
                        items(state.results) { stub ->
                            DiscoveryCard(
                                stub = stub,
                                sourceName = selected?.name ?: "Source",
                                onClick = { vm.open(stub) },
                                width = 120
                            )
                        }
                        if (!state.endReached && state.query.isBlank()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.pageLoading) {
                                        CircularProgressIndicator()
                                    } else {
                                        Button(onClick = vm::loadMore) { Text("Load more") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            AddSourceSheet(
                onStremio = { showAdd = false; dialog = AddDialog.STREMIO },
                onJellyfin = { showAdd = false; dialog = AddDialog.JELLYFIN },
                onEpub = {
                    showAdd = false
                    epubPicker.launch(arrayOf("application/epub+zip", "application/octet-stream"))
                },
                onFolder = {
                    showAdd = false
                    folderPicker.launch(null)
                },
                onExtension = {
                    showAdd = false
                    extensionPicker.launch(arrayOf("*/*"))
                },
                onNovelPlugin = {
                    showAdd = false
                    novelPicker.launch(
                        arrayOf("text/javascript", "application/javascript", "text/plain", "*/*")
                    )
                },
                onManageExtensions = {
                    showAdd = false
                    onManageExtensions()
                }
            )
        }
    }

    when (dialog) {
        AddDialog.STREMIO -> TextPromptDialog(
            title = "Add a Stremio addon",
            label = "Manifest URL",
            placeholder = "https://example.com/manifest.json",
            confirmLabel = "Add",
            onConfirm = { vm.addStremio(it); dialog = AddDialog.NONE },
            onDismiss = { dialog = AddDialog.NONE }
        )

        AddDialog.JELLYFIN -> JellyfinDialog(
            onConfirm = { url, user, pass ->
                vm.addJellyfin(url, user, pass)
                dialog = AddDialog.NONE
            },
            onDismiss = { dialog = AddDialog.NONE }
        )

        AddDialog.NONE -> Unit
    }
}

@Composable
private fun AddSourceSheet(
    onStremio: () -> Unit,
    onJellyfin: () -> Unit,
    onEpub: () -> Unit,
    onFolder: () -> Unit,
    onExtension: () -> Unit,
    onNovelPlugin: () -> Unit,
    onManageExtensions: () -> Unit
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text = "Add a source",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        SheetOption(
            icon = Icons.Outlined.Extension,
            title = "Discover extensions",
            subtitle = "Browse repositories, install updates and manage installed sources."
        ) { onManageExtensions() }
        SheetOption(
            icon = Icons.Outlined.Link,
            title = "Stremio addon",
            subtitle = "Paste an addon manifest URL. Direct HTTP streams play in-app."
        ) { onStremio() }
        SheetOption(
            icon = Icons.Outlined.Dns,
            title = "Jellyfin server",
            subtitle = "Connect your own server. Movies and episodes stream from it."
        ) { onJellyfin() }
        SheetOption(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            title = "EPUB file",
            subtitle = "Import a book from device storage into your library."
        ) { onEpub() }
        SheetOption(
            icon = Icons.Outlined.FolderOpen,
            title = "Video folder",
            subtitle = "Import MP4/MKV files. Subfolders with several videos become series."
        ) { onFolder() }
        SheetOption(
            icon = Icons.Outlined.Extension,
            title = "Manga extension (.apk)",
            subtitle = "Install a Tachiyomi/Komikku extension file from this device."
        ) { onExtension() }
        SheetOption(
            icon = Icons.Outlined.Code,
            title = "Novel plugin (.js)",
            subtitle = "Install an LNReader plugin file. It runs in a sandboxed JS host."
        ) { onNovelPlugin() }
    }
}

@Composable
private fun SheetOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TextPromptDialog(
    title: String,
    label: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun JellyfinDialog(
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Jellyfin") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.10:8096") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(url.trim(), user.trim(), pass) },
                enabled = url.isNotBlank() && user.isNotBlank()
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
