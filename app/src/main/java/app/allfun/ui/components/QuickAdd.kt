package app.allfun.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.net.userMessage
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.GlassPromptDialog
import app.allfun.ui.glass.GlassPromptField
import app.allfun.ui.glass.GlassSheet
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.SheetRow
import app.allfun.ui.glass.AFIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private enum class QuickAddDialog { NONE, STREMIO, JELLYFIN }

/**
 * One entry point for adding something new without leaving the current category: a Stremio
 * add-on, a Jellyfin server, a manga extension APK or an LNReader plugin. Extension discovery
 * stays on the Extensions screen, where repositories and updates live.
 *
 * The view model is hoisted by the host screen so a message is never lost when the sheet
 * closes mid-import.
 */
@Composable
fun GlassQuickAdd(vm: QuickAddViewModel, onDismiss: () -> Unit) {
    var dialog by remember { mutableStateOf(QuickAddDialog.NONE) }
    var sheetVisible by remember { mutableStateOf(true) }

    val extensionPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importExtension) }

    val novelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importNovelPlugin) }

    fun open(next: QuickAddDialog) {
        dialog = next
        sheetVisible = false
    }

    if (sheetVisible) {
        GlassSheet(onDismiss = onDismiss) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    AFText("Add a source", style = AFTheme.type.title, color = AFTheme.colors.textHi)
                    Spacer(Modifier.height(4.dp))
                    AFText(
                        "Everything you add feeds the three categories automatically.",
                        style = AFTheme.type.bodySmall,
                        color = AFTheme.colors.textLo
                    )
                }
                AFTextButton("Close", onDismiss)
            }
            Spacer(Modifier.height(10.dp))
            SheetRow(
                title = "Stremio addon",
                subtitle = "Play films and episodes from a manifest URL",
                leading = { AFIcon(AFIcons.Link, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = { open(QuickAddDialog.STREMIO) }
            )
            SheetRow(
                title = "Jellyfin server",
                subtitle = "Stream movies and shows from your own server",
                leading = { AFIcon(AFIcons.Server, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = { open(QuickAddDialog.JELLYFIN) }
            )
            SheetRow(
                title = "Manga extension",
                subtitle = "Install a Tachiyomi / Komikku .apk from this device",
                leading = { AFIcon(AFIcons.Folder, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = {
                    sheetVisible = false
                    extensionPicker.launch(arrayOf("*/*"))
                }
            )
            SheetRow(
                title = "Novel plugin",
                subtitle = "Install an LNReader .js plugin file",
                leading = { AFIcon(AFIcons.Code, null, tint = AFTheme.colors.textMid, size = 20.dp) },
                onClick = {
                    sheetVisible = false
                    novelPicker.launch(
                        arrayOf("text/javascript", "application/javascript", "text/plain", "*/*")
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    when (dialog) {
        QuickAddDialog.STREMIO -> GlassPromptDialog(
            title = "Add a Stremio addon",
            body = "Paste the manifest URL. The addon's own catalogue becomes searchable here.",
            fields = listOf(
                GlassPromptField(
                    label = "Manifest URL",
                    placeholder = "https://example.com/manifest.json",
                    keyboard = KeyboardType.Uri
                )
            ),
            confirmLabel = "Add",
            onConfirm = {
                vm.addStremio(it.first().trim())
                dialog = QuickAddDialog.NONE
                onDismiss()
            },
            onDismiss = onDismiss
        )

        QuickAddDialog.JELLYFIN -> GlassPromptDialog(
            title = "Connect Jellyfin",
            body = "Your server stays on your network; credentials are stored on this device.",
            fields = listOf(
                GlassPromptField(
                    label = "Server URL",
                    placeholder = "http://192.168.1.10:8096",
                    keyboard = KeyboardType.Uri
                ),
                GlassPromptField(label = "Username", placeholder = "Username"),
                GlassPromptField(label = "Password", placeholder = "Password", secret = true)
            ),
            confirmLabel = "Connect",
            onConfirm = {
                vm.addJellyfin(it[0].trim(), it[1].trim(), it[2])
                dialog = QuickAddDialog.NONE
                onDismiss()
            },
            onDismiss = onDismiss,
            confirmEnabled = { it.size >= 2 && it[0].isNotBlank() && it[1].isNotBlank() }
        )

        QuickAddDialog.NONE -> Unit
    }
}

/**
 * The former Sources screen's add actions, kept without its browsing surface. The installed
 * extensions and connected servers keep feeding the three categories through the same
 * SourceHub calls.
 */
class QuickAddViewModel(private val container: AppContainer) : ViewModel() {

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
