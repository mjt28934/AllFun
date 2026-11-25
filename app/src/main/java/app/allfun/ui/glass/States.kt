package app.allfun.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.allfun.ui.theme.LocalReduceMotion

/** A shape that matches the final layout exactly, swept by one light pass. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val colors = AFTheme.colors
    val reduce = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerPhase"
    )
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                drawRect(if (colors.isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.05f))
                if (!reduce) {
                    val band = size.width * 0.55f
                    val start = -band + (size.width + band * 2f) * phase
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = if (colors.isLight) 0.22f else 0.07f),
                            1f to Color.Transparent,
                            start = Offset(start, 0f),
                            end = Offset(start + band, size.height)
                        )
                    )
                }
            }
    )
}

@Composable
fun SkeletonPosterGrid(
    modifier: Modifier = Modifier,
    columns: Int = 3,
    rows: Int = 2
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(GlassTokens.gap)) {
                repeat(columns) {
                    Column(Modifier.weight(1f)) {
                        ShimmerBox(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                            shape = RoundedCornerShape(GlassTokens.rPoster)
                        )
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.8f).height(10.dp))
                        Spacer(Modifier.height(5.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.45f).height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonRows(count: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) {
            ShimmerBox(Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(14.dp))
        }
    }
}

/** One display line, one muted line, at most one action. No icon in a circle. */
@Composable
fun GlassEmpty(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AFText(
            text = title,
            style = AFTheme.type.title,
            color = AFTheme.colors.textHi,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        AFText(
            text = body,
            style = AFTheme.type.body,
            color = AFTheme.colors.textLo,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            GlassPrimaryAction(actionLabel, onAction)
        }
    }
}

@Composable
fun EndOfList(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 26.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 1.dp)
                .background(AFTheme.colors.hairline)
        )
        Spacer(Modifier.height(12.dp))
        AFText(text, style = AFTheme.type.caption, color = AFTheme.colors.textLo)
    }
}
