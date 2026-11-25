package app.allfun.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.net.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private enum class QuickAddDialog { NONE, STREMIO, JELLYFIN }

/**
 * One entry point for adding something new without leaving the current category: a Stremio
 * add-on, a Jellyfin server, a manga extension APK or an LNReader plugin. Extension discovery
 * stays on the Extensions screen, where repositories and updates live.
 */
@Composable
fun QuickAddButton(
    container: AppContainer,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val vm = rememberViewModel { QuickAddViewModel(container) }
    var expanded by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(QuickAddDialog.NONE) }

    val extensionPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importExtension) }

    val novelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importNovelPlugin) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Box {
        IconButton(onClick = { expanded = true }, modifier = modifier) {
            Icon(Icons.Outlined.Add, contentDescription = "Add a source")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QuickAddItem(
                icon = Icons.Outlined.Link,
                title = "Stremio addon",
                subtitle = "Play films and episodes from a manifest URL"
            ) {
                expanded = false
                dialog = QuickAddDialog.STREMIO
            }
            QuickAddItem(
                icon = Icons.Outlined.Dns,
                title = "Jellyfin server",
                subtitle = "Stream movies and shows from your own server"
            ) {
                expanded = false
                dialog = QuickAddDialog.JELLYFIN
            }
            QuickAddItem(
                icon = Icons.Outlined.Extension,
                title = "Manga extension (.apk)",
                subtitle = "Install a Tachiyomi/Komikku file from this device"
            ) {
                expanded = false
                extensionPicker.launch(arrayOf("*/*"))
            }
            QuickAddItem(
                icon = Icons.Outlined.Code,
                title = "Novel plugin (.js)",
                subtitle = "Install an LNReader plugin file"
            ) {
                expanded = false
                novelPicker.launch(
                    arrayOf("text/javascript", "application/javascript", "text/plain", "*/*")
                )
            }
        }
    }

    when (dialog) {
        QuickAddDialog.STREMIO -> TextPromptDialog(
            title = "Add a Stremio addon",
            label = "Manifest URL",
            placeholder = "https://example.com/manifest.json",
            confirmLabel = "Add",
            onConfirm = { vm.addStremio(it); dialog = QuickAddDialog.NONE },
            onDismiss = { dialog = QuickAddDialog.NONE }
        )

        QuickAddDialog.JELLYFIN -> JellyfinDialog(
            onConfirm = { url, user, pass ->
                vm.addJellyfin(url, user, pass)
                dialog = QuickAddDialog.NONE
            },
            onDismiss = { dialog = QuickAddDialog.NONE }
        )

        QuickAddDialog.NONE -> Unit
    }
}

@Composable
private fun QuickAddItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
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
    AlertDialog(
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
    AlertDialog(
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

/**
 * The former Sources screen's add actions, kept without its browsing surface. The installed
 * extensions and connected servers keep feeding the three categories through the same
 * SourceHub calls.
 */
private class QuickAddViewModel(private val container: AppContainer) : ViewModel() {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun importExtension(uri: Uri) {
        viewModelScope.launch {
            try {
                val added = container.sourceHub.addExtension(uri).getOrThrow()
                _messages.send(
                    if (added.size == 1) "Installed ${added.first().name}"
                    else "Installed ${added.size} manga sources"
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _messages.send("Could not install that extension: ${t.userMessage("the extension")}")
            }
        }
    }

    fun importNovelPlugin(uri: Uri) {
        viewModelScope.launch {
            try {
                val added = container.sourceHub.addLnReader(uri).getOrThrow()
                _messages.send("Installed ${added.name}")
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _messages.send("Could not install that plugin: ${t.userMessage("the plugin")}")
            }
        }
    }

    fun addStremio(url: String) {
        viewModelScope.launch {
            container.sourceHub.addStremio(url)
                .onSuccess { _messages.send("Added ${it.name}") }
                .onFailure { _messages.send("Could not add addon: ${it.userMessage("the addon")}") }
        }
    }

    fun addJellyfin(url: String, username: String, password: String) {
        viewModelScope.launch {
            container.sourceHub.addJellyfin(url, username, password)
                .onSuccess { _messages.send("Connected to ${it.name}") }
                .onFailure { _messages.send("Jellyfin: ${it.userMessage("the server")}") }
        }
    }
}
