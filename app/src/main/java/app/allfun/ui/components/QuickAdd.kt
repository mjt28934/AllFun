package app.allfun.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.net.userMessage
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.AllFunType
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

    TopBarAction(
        icon = Icons.Outlined.Add,
        contentDescription = "Add a source",
        onClick = { expanded = true }
    )

    if (expanded) {
        AllFunSheet(title = "Add a source", onDismiss = { expanded = false }) {
            QuickAddItem(
                icon = Icons.Outlined.Link,
                title = "Stremio addon",
                subtitle = "Films and series from a manifest URL",
                hint = "URL"
            ) {
                expanded = false
                dialog = QuickAddDialog.STREMIO
            }
            QuickAddItem(
                icon = Icons.Outlined.Dns,
                title = "Jellyfin server",
                subtitle = "Stream from your own server",
                hint = "URL"
            ) {
                expanded = false
                dialog = QuickAddDialog.JELLYFIN
            }
            QuickAddItem(
                icon = Icons.Outlined.Extension,
                title = "Manga extension",
                subtitle = "Install a .apk from this device",
                hint = ".apk"
            ) {
                expanded = false
                extensionPicker.launch(arrayOf("*/*"))
            }
            QuickAddItem(
                icon = Icons.Outlined.Code,
                title = "Novel plugin",
                subtitle = "Install a .js plugin file",
                hint = ".js"
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
    hint: String,
    onClick: () -> Unit
) {
    val colors = AllFun.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = hint,
            style = AllFunType.mono,
            color = colors.textSecondary,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
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
    AllFunSheet(title = title, onDismiss = onDismiss) {
        AllFunTextField(
            value = value,
            onValueChange = { value = it },
            label = label,
            placeholder = placeholder
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AllFunButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = AllFunButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
            AllFunButton(
                text = confirmLabel,
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun JellyfinDialog(
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AllFunSheet(title = "Connect Jellyfin", onDismiss = onDismiss) {
        AllFunTextField(
            value = url,
            onValueChange = { url = it },
            label = "Server URL",
            placeholder = "http://192.168.1.10:8096",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Spacer(Modifier.height(12.dp))
        AllFunTextField(
            value = user,
            onValueChange = { user = it },
            label = "Username"
        )
        Spacer(Modifier.height(12.dp))
        AllFunTextField(
            value = pass,
            onValueChange = { pass = it },
            label = "Password",
            visualTransformation = PasswordVisualTransformation()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AllFunButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = AllFunButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
            AllFunButton(
                text = "Connect",
                onClick = { onConfirm(url.trim(), user.trim(), pass) },
                enabled = url.isNotBlank() && user.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    }
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
