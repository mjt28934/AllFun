package app.allfun.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.allfun.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

/**
 * The platform gives a Dialog a wrap-content window. A bottom sheet has to be anchored to the
 * real screen edge, so the window is stretched to the full display and the system dim is turned
 * off: the sheet draws its own, much lighter scrim and the world stays visible behind it.
 */
@Composable
private fun FullScreenSheetWindow() {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        // A floating dialog is normally laid out inside the status-bar frame, and a
        // MATCH_PARENT height would then hang below the display. Letting the window ignore
        // the display frame and pinning it to the top corner makes it cover the real screen,
        // so the sheet's bottom edge sits on the navigation bar instead of past it.
        window.setGravity(android.view.Gravity.TOP or android.view.Gravity.START)
        val lp = window.attributes
        lp.flags = lp.flags or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            // Floating dialog frames normally shrink to the status and navigation bars;
            // fitting no insets keeps the window on the full display instead.
            lp.fitInsetsTypes = 0
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = lp
        window.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        window.setDimAmount(0f)
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }
}

/** Bottom sheet in frosted glass, with the 36x4 handle and a scrim you can tap. */
@Composable
fun GlassSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        FullScreenSheetWindow()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            AnimatedVisibility(
                visible = shown,
                enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it } +
                    fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GlassSurface(
                    modifier = modifier
                        .fillMaxWidth()
                        .heightIn(max = (maxHeight - 56.dp).coerceAtLeast(240.dp))
                        .imePadding(),
                    tier = GlassTier.FROSTED,
                    shape = RoundedCornerShape(topStart = GlassTokens.rSheet, topEnd = GlassTokens.rSheet),
                    shadow = true
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.35f))
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            content = content
                        )
                    }
                }
            }
        }
    }
}

/** Centered confirmation in frosted glass. */
@Composable
fun GlassDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        FullScreenSheetWindow()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                tier = GlassTier.FROSTED,
                shape = RoundedCornerShape(GlassTokens.rPanel),
                shadow = true
            ) {
                Column(Modifier.padding(24.dp)) {
                    AFText(title, style = AFTheme.type.title, color = AFTheme.colors.textHi)
                    Spacer(Modifier.height(10.dp))
                    AFText(body, style = AFTheme.type.body, color = AFTheme.colors.textMid)
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(dismissLabel, onDismiss, Modifier.weight(1f))
                        GlassPrimaryAction(
                            label = confirmLabel,
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** One-line feedback that grows from the bottom and leaves on its own. */
class GlassToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var id by mutableIntStateOf(0)
        private set

    fun show(text: String) {
        message = text
        id++
    }

    fun dismiss() {
        message = null
    }
}

@Composable
fun rememberGlassToastState(): GlassToastState = remember { GlassToastState() }

@Composable
fun GlassToastHost(state: GlassToastState, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    LaunchedEffect(state.id) {
        if (state.message != null) {
            delay(2400)
            state.dismiss()
        }
    }
    AnimatedVisibility(
        visible = state.message != null,
        enter = fadeIn() + slideInVertically { if (reduce) 0 else it / 2 },
        exit = fadeOut() + slideOutVertically { if (reduce) 0 else it / 2 },
        modifier = modifier
    ) {
        GlassSurface(
            tier = GlassTier.CLEAR,
            shape = RoundedCornerShape(GlassTokens.rCapsule),
            shadow = true,
            sweep = true
        ) {
            AFText(
                text = state.message.orEmpty(),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = AFTheme.type.label,
                color = AFTheme.colors.textHi,
                maxLines = 2,
                onGlass = true
            )
        }
    }
}

/** A selection row used inside sheets: icon, title, optional check. */
@Composable
fun SheetRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = AFTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            AFText(
                title,
                style = AFTheme.type.body,
                color = if (selected) colors.accent else colors.textHi,
                maxLines = 1
            )
            if (subtitle != null) {
                AFText(
                    subtitle,
                    style = AFTheme.type.caption,
                    color = colors.textLo,
                    maxLines = 1
                )
            }
        }
        if (selected) {
            AFIcon(AFIcons.Check, contentDescription = null, tint = colors.accent, size = 18.dp)
        }
        trailing?.invoke()
    }
}

data class GlassPromptField(
    val label: String,
    val placeholder: String = "",
    val secret: Boolean = false,
    val keyboard: androidx.compose.ui.text.input.KeyboardType =
        androidx.compose.ui.text.input.KeyboardType.Text
)

/**
 * A glass form dialog. One to three fields, a clear primary action and nothing else:
 * used for add-on URLs, server credentials and any other short text input.
 */
@Composable
fun GlassPromptDialog(
    title: String,
    fields: List<GlassPromptField>,
    confirmLabel: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    confirmEnabled: (List<String>) -> Boolean = { values ->
        values.firstOrNull()?.isNotBlank() == true
    }
) {
    val values = remember { fields.map { androidx.compose.runtime.mutableStateOf("") } }
    val snapshot = values.map { it.value }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        FullScreenSheetWindow()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                tier = GlassTier.FROSTED,
                shape = RoundedCornerShape(GlassTokens.rPanel),
                shadow = true
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    AFText(title, style = AFTheme.type.title, color = AFTheme.colors.textHi)
                    if (body != null) {
                        Spacer(Modifier.height(8.dp))
                        AFText(body, style = AFTheme.type.bodySmall, color = AFTheme.colors.textMid)
                    }
                    Spacer(Modifier.height(18.dp))
                    fields.forEachIndexed { index, field ->
                        GlassTextField(
                            value = values[index].value,
                            onValueChange = { values[index].value = it },
                            placeholder = field.placeholder.ifBlank { field.label },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (field.secret) {
                                androidx.compose.ui.text.input.PasswordVisualTransformation()
                            } else {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = if (field.secret) {
                                    androidx.compose.ui.text.input.KeyboardType.Password
                                } else {
                                    field.keyboard
                                }
                            )
                        )
                        if (index != fields.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Cancel", onDismiss, Modifier.weight(1f))
                        GlassPrimaryAction(
                            label = confirmLabel,
                            onClick = { onConfirm(snapshot) },
                            modifier = Modifier.weight(1f),
                            enabled = confirmEnabled(snapshot)
                        )
                    }
                }
            }
        }
    }
}
