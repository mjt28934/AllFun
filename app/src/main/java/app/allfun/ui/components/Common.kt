package app.allfun.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.data.model.MediaStub
import app.allfun.ui.countLabel
import app.allfun.ui.percentLabel
import app.allfun.ui.theme.Cyan
import app.allfun.ui.theme.LocalReduceMotion
import app.allfun.ui.theme.Motion
import app.allfun.ui.theme.Rose
import app.allfun.ui.theme.Violet
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

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

/**
 * Cover with a real cache key and a short cross-fade. The fallback is drawn by the same
 * composable, so a missing image never leaves a blank hole.
 */
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
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(180)
                .memoryCacheKey(url)
                .diskCacheKey(url)
                .build(),
            contentDescription = null,
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
        listOf(Color(0xFF241D45), Color(0xFF4C3A96)),
        listOf(Color(0xFF0B3238), Color(0xFF17606B)),
        listOf(Color(0xFF39122A), Color(0xFF8A2B55)),
        listOf(Color(0xFF33240F), Color(0xFF8A5A18))
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
            color = Color.White.copy(alpha = 0.78f)
        )
    }
}

/** Progress bar whose value glides to the stored position instead of snapping. */
@Composable
fun AnimatedProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    height: Int = 4
) {
    val reduceMotion = LocalReduceMotion.current
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(520, easing = Motion.EaseOut),
        label = "progress"
    )
    LinearProgressIndicator(
        progress = { animated },
        color = color,
        trackColor = trackColor,
        drawStopIndicator = {},
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .semantics {
                stateDescription = "${(target * 100).roundToInt()}% complete"
            }
    )
}

@Composable
fun MediaCard(
    media: MediaEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    width: Int = 136,
    fillWidth: Boolean = false,
    metaOverride: String? = null,
    showType: Boolean = true,
    downloadLabel: String? = null,
    /** Extra honest line under the author, e.g. download size and date. */
    caption: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val meta = metaOverride ?: media.author
        ?: buildString {
            if (media.totalUnits > 0) append(countLabel(media.totalUnits, media.unitLabel))
        }
    val percent = percentLabel(progress)

    Column(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width.dp))
            .pressScale(interaction)
            .focusOutline(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClickLabel = "Open ${media.title}",
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
                url = media.coverUrl,
                title = media.title,
                modifier = Modifier.fillMaxSize()
            )
            // A short scrim keeps the format badge readable without hiding the artwork.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.42f),
                            1f to Color.Transparent
                        )
                    )
            )
            if (showType) {
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
                        imageVector = media.kind.icon(),
                        contentDescription = null,
                        tint = media.kind.accentColor(),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
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
                        .padding(9.dp)
                        .size(15.dp)
                )
            }
            if (downloadLabel != null) {
                Text(
                    text = downloadLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, end = 8.dp, bottom = 11.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.66f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.32f))
                ) {
                    AnimatedProgress(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        height = 4
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = media.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!meta.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (percent.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = percent,
                style = MaterialTheme.typography.labelSmall,
                color = Cyan,
                maxLines = 1
            )
        }
        if (!caption.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A source result that is not in the database yet (Browse / discovery shelves). */
@Composable
fun DiscoveryCard(
    stub: MediaStub,
    sourceName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 132
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(width.dp)
            .pressScale(interaction)
            .focusOutline()
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClickLabel = "Open ${stub.title}",
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
                url = stub.coverUrl,
                title = stub.title,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stub.title,
            style = MaterialTheme.typography.titleSmall,
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
        StatusPill(text = sourceName, tint = MaterialTheme.colorScheme.secondary)
    }
}

/**
 * List-mode library row. Keeps cover, title, metadata and progress readable at large
 * font scales; the whole row is one 56dp+ touch target.
 */
@Composable
fun MediaListRow(
    media: MediaEntity,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null,
    downloadLabel: String? = null,
    onRemoveDownload: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val reduceMotion = LocalReduceMotion.current
    val favoriteColor by animateColorAsState(
        targetValue = if (media.favorite) Rose else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(Motion.FAST),
        label = "favoriteColor"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusOutline(MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClickLabel = "Open ${media.title}",
                onClick = onClick
            )
            .heightIn(min = 92.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(87.dp)
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
                Spacer(Modifier.height(7.dp))
                AnimatedProgress(
                    progress = progress,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    height = 4
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = percentLabel(progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan
                )
            }
            if (downloadLabel != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = downloadLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
                    tint = favoriteColor
                )
            }
        }
        if (onRemoveDownload != null) {
            IconButton(onClick = onRemoveDownload) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove downloaded files for ${media.title}",
                    tint = MaterialTheme.colorScheme.error
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
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = actionLabel, onClick = onAction)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
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
    modifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(actionLabel)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onSecondaryAction,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(secondaryActionLabel)
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
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
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
