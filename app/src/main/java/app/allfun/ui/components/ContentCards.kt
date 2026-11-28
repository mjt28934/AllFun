package app.allfun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import app.allfun.ui.theme.AllFun

fun ContentCategory.icon(): ImageVector = when (this) {
    ContentCategory.MOVIES -> Icons.Outlined.Movie
    ContentCategory.BOOKS -> Icons.AutoMirrored.Outlined.MenuBook
    ContentCategory.MANGA -> Icons.Outlined.AutoStories
}

@Composable
fun ContentCategory.accent(): Color = AllFun.colors.accent

@Composable
private fun ContentCategory.markColor(): Color = when (this) {
    ContentCategory.MOVIES -> AllFun.colors.markMovies
    ContentCategory.BOOKS -> AllFun.colors.markBooks
    ContentCategory.MANGA -> AllFun.colors.markManga
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
                .clip(RoundedCornerShape(4.dp))
                .background(AllFun.colors.surface2)
                .border(
                    width = 1.dp,
                    color = if (AllFun.colors.isDark) Color.White.copy(alpha = 0.08f)
                    else AllFun.colors.hairline,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            CoverImage(
                url = item.stub.coverUrl,
                title = item.title,
                modifier = Modifier.fillMaxSize()
            )
            if (!format.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AllFun.colors.surface1.copy(alpha = 0.96f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.category.icon(),
                        contentDescription = null,
                        tint = AllFun.colors.accent,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = format,
                        style = MaterialTheme.typography.labelSmall,
                        color = AllFun.colors.textPrimary,
                        maxLines = 1
                    )
                }
            }
            if (item.alternateCount > 0) {
                Text(
                    text = "+${item.alternateCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AllFun.colors.textPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AllFun.colors.surface1.copy(alpha = 0.96f))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            if (downloadLabel != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AllFun.colors.surface1.copy(alpha = 0.96f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        tint = AllFun.colors.textSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = downloadLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = AllFun.colors.textPrimary,
                        maxLines = 1
                    )
                }
            }
            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(AllFun.colors.hairline)
                ) {
                    AnimatedProgress(progress = progress, height = 2)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = AllFun.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(item.category.markColor())
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = item.sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = AllFun.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (item.alternateCount > 0) {
                Text(
                    text = " · +${item.alternateCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AllFun.colors.textSecondary,
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
                            ProviderState.WORKING -> AllFun.colors.accent
                            ProviderState.NO_CONTENT -> AllFun.colors.outlineStrong
                            else -> AllFun.colors.danger
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
                        AllFun.colors.textSecondary
                    } else {
                        AllFun.colors.danger
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (showDetails && status.diagnostics != null) {
                    Text(
                        text = status.diagnostics,
                        style = MaterialTheme.typography.labelSmall,
                        color = AllFun.colors.textSecondary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (status.diagnostics != null) {
                AllFunButton(
                    text = if (showDetails) "Hide details" else "Details",
                    onClick = { showDetails = !showDetails },
                    variant = AllFunButtonVariant.TEXT
                )
            }
            if (!status.available && onRetry != null) {
                AllFunButton(
                    text = "Retry",
                    onClick = onRetry,
                    variant = AllFunButtonVariant.TEXT
                )
            }
        }
    }
}
