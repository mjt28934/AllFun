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
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.data.model.MediaStub
import app.allfun.ui.countLabel
import app.allfun.ui.percentLabel
import app.allfun.ui.theme.Cyan
import app.allfun.ui.theme.Rose
import app.allfun.ui.theme.Violet

/** Builds a ViewModel with an explicit factory, no DI framework needed. */
@Composable
fun <T : ViewModel> rememberViewModel(key: String? = null, create: () -> T): T {
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
    @Suppress("UNCHECKED_CAST")
    return viewModel<ViewModel>(key = key, factory = factory) as T
}

fun MediaKind.icon(): ImageVector = when (this) {
    MediaKind.MANGA -> Icons.Outlined.AutoStories
    MediaKind.NOVEL -> Icons.AutoMirrored.Outlined.MenuBook
    MediaKind.MOVIE, MediaKind.SERIES -> Icons.Outlined.Movie
}

fun MediaKind.accentColor(): Color = when (this) {
    MediaKind.MANGA -> Violet
    MediaKind.NOVEL -> Cyan
    MediaKind.MOVIE, MediaKind.SERIES -> Rose
}

@Composable
fun CoverImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    if (url.isNullOrBlank()) {
        FallbackCover(title, modifier)
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = { FallbackCover(title, Modifier.fillMaxSize()) },
            error = { FallbackCover(title, Modifier.fillMaxSize()) }
        )
    }
}

@Composable
fun FallbackCover(title: String, modifier: Modifier = Modifier) {
    val seed = (title.hashCode() and 0x7fffffff)
    val palettes = listOf(
        listOf(Color(0xFF2B2350), Color(0xFF7C5CFF)),
        listOf(Color(0xFF083A42), Color(0xFF22D3EE)),
        listOf(Color(0xFF3A0E24), Color(0xFFF472B6)),
        listOf(Color(0xFF3B2A12), Color(0xFFF59E0B))
    )
    val palette = palettes[seed % palettes.size]
    Box(
        modifier = modifier.background(Brush.linearGradient(palette)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.take(2).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun MediaCard(
    media: MediaEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    width: Int = 140,
    fillWidth: Boolean = false,
    metaOverride: String? = null,
    showType: Boolean = true
) {
    val meta = metaOverride ?: media.author
        ?: buildString {
            if (media.totalUnits > 0) append(countLabel(media.totalUnits, media.unitLabel))
        }
    Column(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width.dp))
            .clickable(onClickLabel = "Open ${media.title}", onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            CoverImage(
                url = media.coverUrl,
                title = media.title,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f)
                        )
                    )
            )
            if (showType) {
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
                        imageVector = media.kind.icon(),
                        contentDescription = null,
                        tint = media.kind.accentColor(),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = media.kind.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            if (media.favorite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    tint = Rose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
            Text(
                text = media.title,
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
        Text(
            text = meta.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val percent = percentLabel(progress)
        if (percent.isNotBlank()) {
            Text(
                text = percent,
                style = MaterialTheme.typography.labelSmall,
                color = Cyan,
                maxLines = 1
            )
        }
    }
}

/** A source result that is not in the database yet (Browse / Home shelves). */
@Composable
fun DiscoveryCard(
    stub: MediaStub,
    sourceName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 132
) {
    Column(
        modifier = modifier
            .width(width.dp)
            .clickable(onClickLabel = "Open ${stub.title}", onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            CoverImage(
                url = stub.coverUrl,
                title = stub.title,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stub.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!stub.subtitle.isNullOrBlank()) {
            Text(
                text = stub.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = sourceName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * List-mode library row. Keeps the cover, title, metadata and progress readable at
 * large font scales; the whole row is one 56dp+ touch target.
 */
@Composable
fun MediaListRow(
    media: MediaEntity,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open ${media.title}", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(84.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            CoverImage(media.coverUrl, media.title, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (media.favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Favorite",
                        tint = Rose,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            val meta = listOfNotNull(
                media.author,
                media.kind.label,
                media.totalUnits.takeIf { it > 0 }?.let { countLabel(it, media.unitLabel) }
            ).joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress != null && progress > 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = Cyan,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = percentLabel(progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan
                )
            }
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (media.favorite) Icons.Filled.Favorite
                    else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (media.favorite) {
                        "Remove ${media.title} from favorites"
                    } else {
                        "Add ${media.title} to favorites"
                    },
                    tint = if (media.favorite) Rose else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = actionLabel, onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

val ScreenPadding = 16.dp

@Composable
fun ThumbRow(
    items: List<MediaEntity>,
    progressFor: (MediaEntity) -> Float? = { null },
    onOpen: (MediaEntity) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items.size) { index ->
            val media = items[index]
            MediaCard(
                media = media,
                onClick = { onOpen(media) },
                progress = progressFor(media)
            )
        }
    }
}
