package app.allfun.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.LocalReduceMotion
import app.allfun.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Subtle press feedback shared by every tappable card. The card scales down a little
 * instead of jumping, and does nothing at all when the user asked for reduced motion.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.972f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = if (reduceMotion) snap() else Motion.settle(),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A visible focus ring for keyboard and D-pad users. Compose's clickable already makes
 * cards focusable; without this the focused card is indistinguishable from the rest.
 */
@Composable
fun Modifier.focusOutline(shape: Shape = MaterialTheme.shapes.medium): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) {
                Modifier.border(2.dp, AllFun.colors.accent, shape)
            } else {
                Modifier
            }
        )
}

/**
 * Fades and lifts a shelf into place once, when its real data arrives. It runs on first
 * composition only, so scrolling never restarts it.
 */
@Composable
fun ContentReveal(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) {
            EnterTransition.None
        } else {
            fadeIn(tween(Motion.MEDIUM, easing = Motion.EaseOut)) +
                slideInVertically(tween(Motion.MEDIUM, easing = Motion.EaseOut)) { it / 12 }
        },
        exit = ExitTransition.None,
        modifier = modifier
    ) {
        content()
    }
}

/** Shared shimmer brush; a flat tone instead of movement when motion is reduced. */
@Composable
fun rememberShimmerBrush(): Brush {
    val base = AllFun.colors.surface2
    val highlight = AllFun.colors.surface3
    if (LocalReduceMotion.current) {
        return Brush.linearGradient(listOf(base, base))
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -420f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(1450, easing = LinearEasing)
        ),
        label = "shimmerOffset"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + 480f, 0f)
    )
}

/** A poster-shaped placeholder that matches the real card proportions. */
@Composable
fun PosterSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 2
) {
    val brush = rememberShimmerBrush()
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.medium)
                .background(brush)
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(brush)
        )
        if (lines > 1) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(brush)
            )
        }
    }
}

/**
 * Horizontal shelf placeholder used while a category or search is actually loading.
 * It is never shown when no request is running.
 */
@Composable
fun ShelfSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 4,
    posterWidth: Int = 128
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) {
            PosterSkeleton(Modifier.width(posterWidth.dp))
        }
    }
}

/** Grid placeholder with the same adaptive columns as the real content grid. */
@Composable
fun GridSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 9,
    minCellWidth: Dp = 124.dp
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        userScrollEnabled = false
    ) {
        items(count) { PosterSkeleton() }
    }
}

/** Small, labelled status chip. The text carries the meaning, not only the colour. */
@Composable
fun StatusPill(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** An icon inside a tinted, rounded square; the app's small visual signature. */
@Composable
fun IconBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Int = 44
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(MaterialTheme.shapes.small)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5f).dp)
        )
    }
}

/**
 * Cascades a card into place once, when it first reaches the viewport. The delay is
 * derived from its position, so a grid resolves as a wave instead of every poster
 * appearing on the same frame. Long lists only stagger their first rows: after that the
 * effect would be a scroll tax rather than a flourish.
 */
@Composable
fun Modifier.staggeredReveal(
    index: Int,
    from: Dp = 18.dp,
    maxStaggerIndex: Int = 11
): Modifier {
    if (LocalReduceMotion.current) return this
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(Motion.stagger(index.coerceAtMost(maxStaggerIndex)))
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(Motion.MEDIUM + 60, easing = Motion.EaseOut)
        )
    }
    return graphicsLayer {
        val value = anim.value
        alpha = value
        translationY = (1f - value) * from.toPx()
        val scale = 0.985f + 0.015f * value
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A slow breathing scale for live indicators (a running download, a playing item). It is
 * deliberately small: the point is "this is moving", not a flashing badge.
 */
@Composable
fun Modifier.livePulse(active: Boolean, maxScale: Float = 1.12f): Modifier {
    if (!active || LocalReduceMotion.current) return this
    val transition = rememberInfiniteTransition(label = "livePulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(920, easing = Motion.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulseScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
