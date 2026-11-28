package app.allfun.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.AllFunFonts
import app.allfun.ui.theme.AllFunType
import app.allfun.ui.theme.LocalAllFunRadii

/** One button, four shapes. No tonal pills, no elevation, no shadow. */
enum class AllFunButtonVariant { PRIMARY, SECONDARY, COMPACT, TEXT }

@Composable
fun AllFunButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AllFunButtonVariant = AllFunButtonVariant.PRIMARY,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fullWidth: Boolean = false
) {
    val colors = AllFun.colors
    val radii = LocalAllFunRadii.current
    val height = if (variant == AllFunButtonVariant.COMPACT) 36.dp else 44.dp
    val shape = RoundedCornerShape(radii.button)
    val visual = Modifier
        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = height)
        .then(if (variant == AllFunButtonVariant.COMPACT) Modifier.widthIn(min = 72.dp) else Modifier)
        .clip(shape)
        .then(
            when (variant) {
                AllFunButtonVariant.PRIMARY -> Modifier.background(colors.accent)
                AllFunButtonVariant.SECONDARY, AllFunButtonVariant.COMPACT ->
                    Modifier.border(1.dp, colors.outlineStrong, shape)
                AllFunButtonVariant.TEXT -> Modifier
            }
        )
        .clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
        .padding(
            horizontal = if (variant == AllFunButtonVariant.TEXT) 4.dp else 16.dp,
            vertical = 0.dp
        )
        .alpha(if (enabled) 1f else 0.45f)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when (variant) {
                    AllFunButtonVariant.PRIMARY -> colors.onAccent
                    else -> colors.textPrimary
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = if (variant == AllFunButtonVariant.COMPACT) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            fontWeight = FontWeight.Medium,
            color = when (variant) {
                AllFunButtonVariant.PRIMARY -> colors.onAccent
                AllFunButtonVariant.TEXT -> colors.accent
                else -> colors.textPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    // The 48 dp touch target is the outer box; the visible button keeps its own height.
    Box(
        modifier = modifier.minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = visual,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/** 32 dp chip with a padded hit area; selected chips are a container, never a border. */
@Composable
fun AllFunChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AllFun.colors
    val radii = LocalAllFunRadii.current
    val shape = RoundedCornerShape(radii.chip)
    Box(
        modifier = modifier.minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(shape)
                .then(
                    if (selected) Modifier.background(colors.accentContainer)
                    else Modifier.border(1.dp, colors.hairline, shape)
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.accent else colors.textSecondary,
                maxLines = 1
            )
        }
    }
}

/** Search field: 48 dp, surface2, hairline, accent border only on focus. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onSearch: (() -> Unit)? = null
) {
    val colors = AllFun.colors
    val radii = LocalAllFunRadii.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(radii.field)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(colors.surface2)
            .border(1.dp, if (focused) colors.accent else colors.hairline, shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                ),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            }
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Clear search",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** A quiet form field: hairline border, accent on focus, label above the value. */
@Composable
fun AllFunTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val colors = AllFun.colors
    val radii = LocalAllFunRadii.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(radii.field)
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            interactionSource = interaction,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(colors.surface2)
                .border(1.dp, if (focused) colors.accent else colors.hairline, shape)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            }
        )
    }
}

/** Text tabs with an accent underline; used for Installed / Discover / Updates. */
@Composable
fun AllFunTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AllFun.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Tab, onClick = { onSelect(index) })
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                    maxLines = 1
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (selected) 24.dp else 0.dp)
                        .background(if (selected) colors.accent else Color.Transparent)
                )
            }
        }
    }
}

/** Section header: small caps in the tertiary colour, optional text action. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = AllFunType.overline,
        color = AllFun.colors.textTertiary,
        modifier = modifier
    )
}

/**
 * Screen header: serif title, one meta line, at most three 24 dp actions on the right.
 * The shell adds the status-bar inset; this adds the 16 dp rhythm below it.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    onBack: (() -> Unit)? = null,
    /** True once the screen has scrolled: the title drops from 32 sp to 20 sp. */
    collapsed: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = AllFun.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (collapsed) 56.dp else 0.dp)
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = if (collapsed) 8.dp else 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = when {
                        collapsed -> MaterialTheme.typography.titleLarge.copy(
                            fontFamily = AllFunFonts.Serif,
                            fontSize = 20.sp,
                            lineHeight = 24.sp
                        )
                        onBack != null -> MaterialTheme.typography.headlineMedium
                        else -> MaterialTheme.typography.displaySmall
                    },
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!meta.isNullOrBlank() && !collapsed) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
fun TopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = AllFun.colors.textSecondary,
    enabled: Boolean = true
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .alpha(if (enabled) 1f else 0.4f)
        )
    }
}

/** A grouped list on surface2 with 1 dp hairlines inset from the start. */
@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AllFun.colors
    val radii = LocalAllFunRadii.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Overline(
                text = title,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(radii.group))
                .background(colors.surface2),
            content = content
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val colors = AllFun.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!value.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
        if (divider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 1.dp,
                color = colors.hairline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFunSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AllFun.colors
    val radii = LocalAllFunRadii.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface3,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.60f),
        shape = RoundedCornerShape(topStart = radii.sheet, topEnd = radii.sheet),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.outlineStrong)
            )
        }
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}
