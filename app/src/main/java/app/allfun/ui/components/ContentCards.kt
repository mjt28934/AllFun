package app.allfun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.ContentItem
import app.allfun.data.content.ProviderState
import app.allfun.data.content.ProviderStatus
import app.allfun.data.content.format
import app.allfun.data.model.MediaDetails
import app.allfun.ui.theme.Cyan
import app.allfun.ui.theme.Rose
import app.allfun.ui.theme.Violet

fun ContentCategory.icon(): ImageVector = when (this) {
    ContentCategory.MOVIES -> Icons.Outlined.Movie
    ContentCategory.BOOKS -> Icons.AutoMirrored.Outlined.MenuBook
    ContentCategory.MANGA -> Icons.Outlined.AutoStories
}

fun ContentCategory.accent(): Color = when (this) {
    ContentCategory.MOVIES -> Rose
    ContentCategory.BOOKS -> Cyan
    ContentCategory.MANGA -> Violet
}

/**
 * The single card used by every category, search result and detail selector. It keeps
 * cover, title, provider name and progress identical no matter which extension supplied
 * the title; only the category colour changes.
 */
@Composable
fun ContentCard(
    item: ContentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    details: MediaDetails? = null,
    width: Int = 136,
    fillWidth: Boolean = false,
    /** Shown over the cover when the title has finished downloads, e.g. "Offline". */
    downloadLabel: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val format = item.format(details)
    Column(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width.dp))
            .pressScale(interaction)
            .focusOutline()
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClickLabel = "Open ${item.title}",
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            CoverImage(
                url = item.stub.coverUrl,
                title = item.title,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.40f),
                            1f to Color.Transparent
                        )
                    )
            )
            if (!format.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.46f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.category.icon(),
                        contentDescription = null,
                        tint = item.category.accent(),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = format,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
            if (item.alternateCount > 0) {
                Text(
                    text = "+${item.alternateCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.46f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            if (downloadLabel != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, end = 8.dp, bottom = 11.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.66f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = downloadLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.32f))
                ) {
                    AnimatedProgress(progress = progress, height = 4)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(item.category.accent())
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = item.sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (item.alternateCount > 0) {
                Text(
                    text = " · +${item.alternateCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Compact, honest per-provider status line used under category and search results. */
@Composable
fun ProviderStatusLine(status: ProviderStatus, onRetry: (() -> Unit)? = null) {
    var showDetails by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        when (status.state) {
                            ProviderState.WORKING -> MaterialTheme.colorScheme.secondary
                            ProviderState.NO_CONTENT -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(status.sourceName)
                        append(" · ")
                        append(status.state.label)
                        status.message?.takeIf { it.isNotBlank() }?.let {
                            append(" — ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status.available) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (showDetails && status.diagnostics != null) {
                    Text(
                        text = status.diagnostics,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (status.diagnostics != null) {
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Hide" else "Details")
                }
            }
            if (!status.available && onRetry != null) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
