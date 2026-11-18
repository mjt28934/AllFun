package app.allfun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    width: Int = 132,
    fillWidth: Boolean = false
) {
    val format = item.format(details) ?: item.category.shortLabel
    Column(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width.dp))
            .clickable(onClickLabel = "Open ${item.title}", onClick = onClick)
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
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.42f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.84f)
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.category.icon(),
                    contentDescription = null,
                    tint = item.category.accent(),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = format,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
            if (item.alternateCount > 0) {
                Text(
                    text = "+${item.alternateCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = Cyan,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (!item.stub.subtitle.isNullOrBlank()) {
            Text(
                text = item.stub.subtitle.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = item.category.accent(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (item.alternateCount > 0) {
                Text(
                    text = " · +${item.alternateCount} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Large entry card for the Home screen's three categories. */
@Composable
fun CategoryCard(
    category: ContentCategory,
    count: Int,
    sourceCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = category.accent()
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon(),
                    contentDescription = null,
                    tint = accent
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = category.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("$count saved")
                        append(" · ")
                        append(
                            if (sourceCount == 1) "1 source" else "$sourceCount sources"
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

/** Compact, honest per-provider status line used under category and search results. */
@Composable
fun ProviderStatusLine(status: ProviderStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (status.available) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildString {
                append(status.sourceName)
                status.message?.takeIf { it.isNotBlank() }?.let {
                    append(" — ")
                    append(it)
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (status.available) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
