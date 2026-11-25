package app.allfun.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import app.allfun.ui.theme.LocalReduceMotion
import kotlin.math.abs
import kotlin.random.Random

data class PosterBadge(
    val icon: ImageVector,
    val contentDescription: String,
    val tone: Color? = null
)

/**
 * L1 artwork. Never blurred, never tinted: the poster is the one opaque thing on the
 * screen. Press lifts it, long-press opens the quick menu.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badges: List<PosterBadge> = emptyList(),
    progress: Float? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val colors = AFTheme.colors
    val reduce = LocalReduceMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.97f else 1f,
        animationSpec = tween(if (reduce) 0 else 140),
        label = "posterPress"
    )
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(GlassTokens.rPoster))
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            PosterArtwork(title = title, imageUrl = imageUrl, modifier = Modifier.fillMaxSize())
            // 1dp inner stroke keeps dark art from bleeding into the world.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.10f),
                            style = Stroke(1.dp.toPx())
                        )
                    }
            )
            if (badges.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    badges.take(3).forEach { badge ->
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color.Black.copy(alpha = 0.42f))
                                .drawBehind {
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.22f),
                                        style = Stroke(1.dp.toPx())
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AFIcon(
                                badge.icon,
                                contentDescription = badge.contentDescription,
                                tint = badge.tone ?: Color.White,
                                size = 12.dp
                            )
                        }
                    }
                }
            }
            if (progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    GlassProgress(progress, height = 3.dp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        AFText(
            text = title,
            style = AFTheme.type.label.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = colors.textHi,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            AFText(
                text = subtitle,
                style = AFTheme.type.caption,
                color = colors.textLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Loads the cover; until it arrives, a typographic poster stands in, never clipped initials. */
@Composable
fun PosterArtwork(title: String, imageUrl: String?, modifier: Modifier = Modifier) {
    val preview = LocalInspectionMode.current
    Box(modifier) {
        TypographicPoster(title, Modifier.fillMaxSize())
        if (imageUrl != null && !preview) {
            val painter = rememberAsyncImagePainter(model = imageUrl)
            val state = painter.state
            val alpha by animateFloatAsState(
                targetValue = if (state is AsyncImagePainter.State.Success) 1f else 0f,
                animationSpec = tween(160),
                label = "posterFade"
            )
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha },
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** Deterministic poster from the title hash: never clipped initials, never an empty box. */
@Composable
fun TypographicPoster(title: String, modifier: Modifier = Modifier) {
    val colors = AFTheme.colors
    val seed = remember(title) { abs(title.hashCode()) }
    val random = remember(seed) { Random(seed) }
    val hueShift = remember(seed) { random.nextInt(3) }
    val second = when (hueShift) {
        0 -> colors.accent.copy(alpha = 0.38f)
        1 -> Color(0xFF2CC5C0).copy(alpha = 0.30f)
        else -> Color(0xFF8B7CFF).copy(alpha = 0.34f)
    }
    val angle = remember(seed) { 35f + (seed % 50) }
    Box(
        modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        second,
                        colors.bg2,
                        colors.bg0
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(angle * 10f, angle * 14f)
                )
            )
            .drawBehind {
                // A soft light behind the type, so the poster reads as lit from the top-left.
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        1f to Color.Transparent,
                        center = Offset(size.width * 0.28f, size.height * 0.18f),
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.28f, size.height * 0.18f)
                )
                // A faint rule grid, like a printed jacket.
                val path = Path()
                path.moveTo(0f, size.height * 0.72f)
                path.lineTo(size.width, size.height * 0.72f)
                drawPath(path, Color.White.copy(alpha = 0.08f), style = Stroke(1.dp.toPx()))
            }
    ) {
        AFText(
            text = title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            style = AFTheme.type.displaySmall.copy(fontSize = 19.sp, lineHeight = 22.sp),
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}
