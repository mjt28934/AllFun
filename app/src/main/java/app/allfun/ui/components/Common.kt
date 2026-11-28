package app.allfun.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.ui.countLabel
import app.allfun.ui.percentLabel
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.AllFunType
import app.allfun.ui.theme.LocalAllFunRadii
import app.allfun.ui.theme.LocalReduceMotion
import app.allfun.ui.theme.Motion
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
    return viewModel<ViewModel>(key = key, factory = factory) as T
}

fun MediaKind.icon(): ImageVector = when (this) {
    MediaKind.MANGA -> Icons.Outlined.AutoStories
    MediaKind.NOVEL -> Icons.AutoMirrored.Outlined.MenuBook
    MediaKind.MOVIE, MediaKind.SERIES -> Icons.Outlined.Movie
}

@Composable
fun MediaKind.accentColor(): Color = AllFun.colors.accent

/** The 6 dp dot beside a title's meta line. Never a fill or a background. */
@Composable
fun MediaKind.markColor(): Color = when (this) {
    MediaKind.MANGA -> AllFun.colors.markManga
    MediaKind.NOVEL -> AllFun.colors.markBooks
    MediaKind.MOVIE, MediaKind.SERIES -> AllFun.colors.markMovies
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

/** A flat cover stand-in: initials in the serif face, no gradient, no glow. */
@Composable
fun FallbackCover(title: String, modifier: Modifier = Modifier) {
    val colors = AllFun.colors
    val radii = LocalAllFunRadii.current
    Box(
        modifier = modifier
            .background(colors.surface2)
            .border(1.dp, colors.hairline, RoundedCornerShape(radii.cover)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.take(2).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textTertiary
        )
    }
}

/**
 * A flat 2 dp bar: accent on a hairline track. The value glides to its stored position.
 */
@Composable
fun AnimatedProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AllFun.colors.accent,
    trackColor: Color = AllFun.colors.hairline,
    height: Int = 2
) {
    val reduceMotion = LocalReduceMotion.current
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(420, easing = Motion.EaseOut),
        label = "progress"
    )
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .semantics {
                stateDescription = "${(target * 100).roundToInt()}% complete"
            }
    ) {
        drawRect(color = trackColor)
        if (animated > 0f) {
            drawRect(
                color = color,
                size = androidx.compose.ui.geometry.Size(size.width * animated, size.height)
            )
        }
    }
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
    val colors = AllFun.colors
    val radii = LocalAllFunRadii.current
    val interaction = remember { MutableInteractionSource() }
    val meta = metaOverride ?: media.author
        ?: buildString {
            if (media.totalUnits > 0) append(countLabel(media.totalUnits, media.unitLabel))
        }
    val percent = percentLabel(progress)
    val coverShape = RoundedCornerShape(radii.cover)

    Column(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width.dp))
            .pressScale(interaction)
            .focusOutline(coverShape)
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
                .clip(coverShape)
                .background(colors.surface2)
                .border(
                    width = 1.dp,
                    color = if (colors.isDark) Color.White.copy(alpha = 0.08f) else colors.hairline,
                    shape = coverShape
                )
        ) {
            CoverImage(
                url = media.coverUrl,
                title = media.title,
                modifier = Modifier.fillMaxSize()
            )
            if (media.favorite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    tint = colors.onAccent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(radii.badge))
                        .background(colors.accent)
                        .padding(3.dp)
                        .size(11.dp)
                )
            }
            if (downloadLabel != null) {
                Text(
                    text = downloadLabel,
                    style = AllFunType.mono,
                    fontSize = 10.sp,
                    color = colors.onAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(radii.badge))
                        .background(colors.accent)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            if (progress != null && progress > 0f) {
                AnimatedProgress(
                    progress = progress,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    height = 2
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = media.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!meta.isNullOrBlank() || showType) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showType) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(media.kind.markColor())
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = meta.orEmpty(),
                    style = AllFunType.mono,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (percent.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = percent,
                style = AllFunType.mono,
                color = colors.accent,
                maxLines = 1
            )
        }
        if (!caption.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                style = AllFunType.mono,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    val colors = AllFun.colors
    val radii = LocalAllFunRadii.current
    val interaction = remember { MutableInteractionSource() }
    val reduceMotion = LocalReduceMotion.current
    val favoriteColor by animateColorAsState(
        targetValue = if (media.favorite) colors.accent else colors.textTertiary,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(Motion.FAST),
        label = "favoriteColor"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusOutline(RoundedCornerShape(radii.group))
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClickLabel = "Open ${media.title}",
                onClick = onClick
            )
            .heightIn(min = 72.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(radii.cover))
                .background(colors.surface2)
        ) {
            CoverImage(media.coverUrl, media.title, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                media.author,
                media.kind.label,
                media.totalUnits.takeIf { it > 0 }?.let { countLabel(it, media.unitLabel) }
            ).joinToString(" · ")
            Text(
                text = meta,
                style = AllFunType.mono,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (downloadLabel != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = downloadLabel,
                    style = AllFunType.mono,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (progress != null && progress > 0f) {
                Spacer(Modifier.height(6.dp))
                AnimatedProgress(progress = progress, height = 2)
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
                    tint = favoriteColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (onRemoveDownload != null) {
            IconButton(onClick = onRemoveDownload) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove downloaded files for ${media.title}",
                    tint = colors.danger,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Section header: overline text, optional subtitle and a text action. */
@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    subtitle: String? = null
) {
    val colors = AllFun.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Overline(title)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = actionLabel, onClick = onAction)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent,
                    maxLines = 1
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Empty state: left-aligned, starts below the header, one illustration, one heading, one
 * sentence and one action. The illustration is per-screen, never a stock icon in a circle.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    illustration: EmptyIllustration = EmptyIllustration.SHELF,
    footnote: String? = null
) {
    val colors = AllFun.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 120.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.Start
    ) {
        EmptyIllustrationArt(
            kind = illustration,
            modifier = Modifier.size(width = 120.dp, height = 80.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth(0.85f)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            AllFunButton(text = actionLabel, onClick = onAction)
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(Modifier.height(8.dp))
            AllFunButton(
                text = secondaryActionLabel,
                onClick = onSecondaryAction,
                variant = AllFunButtonVariant.TEXT
            )
        }
        if (!footnote.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                maxLines = 2
            )
        }
    }
}

val ScreenPadding = 20.dp

@Composable
fun ThumbRow(
    items: List<MediaEntity>,
    progressFor: (MediaEntity) -> Float? = { null },
    onOpen: (MediaEntity) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
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
