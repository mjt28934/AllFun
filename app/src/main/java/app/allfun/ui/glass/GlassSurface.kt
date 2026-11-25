package app.allfun.ui.glass

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.allfun.ui.theme.LocalReduceMotion

enum class GlassTier {
    /** L3 controls: nav, capsules, primary action. */
    CLEAR,

    /** L2 panels and sheets. */
    FROSTED,

    /** Controls inside a panel: no blur, rim and refraction only. */
    INSET
}

/**
 * The single material primitive. Every surface in the app is one of three tiers of this
 * composable, so rim, sheen, grain, corner and press behaviour can never drift apart.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    tier: GlassTier = GlassTier.CLEAR,
    shape: Shape = RoundedCornerShape(GlassTokens.rPanel),
    accent: Color = AFTheme.colors.accent,
    palette: WorldPalette = LocalWorldPalette.current,
    selected: Boolean = false,
    enabled: Boolean = true,
    pressScale: Boolean = true,
    shadow: Boolean = false,
    sweep: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AFTheme.colors
    val level = AFTheme.level
    val reduceMotion = LocalReduceMotion.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && pressScale && !reduceMotion) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "glassScale"
    )
    val sweepProgress = remember { Animatable(0f) }
    LaunchedEffect(sweep) {
        if (sweep && !reduceMotion && level == GlassLevel.HIGH) {
            sweepProgress.snapTo(0f)
            sweepProgress.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    val click = if (onClick != null) {
        Modifier.clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .then(
                if (shadow) {
                    Modifier.glassShadow(
                        shape = shape,
                        color = colors.shadow,
                        blur = if (level == GlassLevel.LITE) 14.dp else 24.dp,
                        dy = if (level == GlassLevel.LITE) 5.dp else 8.dp
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .drawBehind {
                drawGlassBody(
                    tier = tier,
                    colors = colors,
                    palette = palette,
                    shape = shape,
                    level = level,
                    selected = selected,
                    pressed = pressed,
                    sweep = sweepProgress.value,
                    accent = accent
                )
            }
            .then(click)
    ) {
        content()
    }
}

private fun Outline.toComposePath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(this@toComposePath.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(this@toComposePath.roundRect) }
    is Outline.Generic -> path
}

private fun DrawScope.drawGlassBody(
    tier: GlassTier,
    colors: AFColors,
    palette: WorldPalette,
    shape: Shape,
    level: GlassLevel,
    selected: Boolean,
    pressed: Boolean,
    sweep: Float,
    accent: Color
) {
    val path = shape.createOutline(size, LayoutDirection.Ltr, this).toComposePath()
    clipPath(path) {
        when (tier) {
            GlassTier.FROSTED -> {
                if (level != GlassLevel.LITE) {
                    drawWorldLens(palette, Offset(0f, -size.height * 0.05f))
                }
                drawRect(colors.bg0.copy(alpha = colors.frostTint))
                drawRect(if (colors.isLight) Color.Black.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.05f))
            }
            GlassTier.CLEAR -> {
                drawRect(colors.bg0.copy(alpha = colors.clearTint))
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (colors.isLight) 0.34f else 0.07f),
                        1f to Color.White.copy(alpha = if (colors.isLight) 0.16f else 0.01f)
                    )
                )
            }
            GlassTier.INSET -> {
                drawRect(if (colors.isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.06f))
            }
        }
        if (selected) {
            drawRect(accent.copy(alpha = if (colors.isLight) 0.16f else 0.13f))
        }
        if (pressed) {
            drawRect(Color.White.copy(alpha = if (colors.isLight) 0.18f else 0.05f))
        }
        if (tier != GlassTier.INSET && level != GlassLevel.LITE) {
            val grain = if (tier == GlassTier.FROSTED) 0.025f else 0.012f
            drawGrain(if (level == GlassLevel.HIGH) grain else grain * 0.6f)
        }

        // 2dp edge sheen along the lit top edge.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (colors.isLight) 0.55f else 0.13f),
                1f to Color.Transparent
            ),
            size = Size(size.width, 10.dp.toPx())
        )
        // 1dp inner shadow so the surface reads as a cavity, not a sticker.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = if (colors.isLight) 0.10f else 0.22f),
                1f to Color.Transparent
            ),
            size = Size(size.width, 2.dp.toPx())
        )

        if (sweep in 0f..1f && sweep > 0f) {
            val cx = size.width * (sweep * 1.7f - 0.35f)
            val band = 48.dp.toPx()
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = if (colors.isLight) 0.18f else 0.09f),
                    1f to Color.Transparent,
                    start = Offset(cx - band, 0f),
                    end = Offset(cx + band, size.height * 0.6f)
                )
            )
        }
    }

    // Rim: a 2dp stroke clipped to the shape leaves a 1dp inner hairline.
    val rim = if (colors.isLight) {
        Brush.linearGradient(
            0f to Color(0x66000000),
            0.42f to Color(0x1F000000),
            0.78f to Color(0x0D000000),
            1f to Color(0x1F000000)
        )
    } else {
        Brush.linearGradient(
            0f to Color(0x75FFFFFF),
            0.38f to Color(0x24FFFFFF),
            0.72f to Color(0x0DFFFFFF),
            1f to Color(0x1FFFFFFF)
        )
    }
    clipPath(path) {
        drawPath(path, brush = rim, style = Stroke(width = 2.dp.toPx()))
    }
}

/** One soft outer shadow. Never stacked: a second shadow muddies the glass. */
fun Modifier.glassShadow(shape: Shape, color: Color, blur: Dp, dy: Dp): Modifier = drawBehind {
    val path: Path = shape.createOutline(size, LayoutDirection.Ltr, this).toComposePath()
    val paint = Paint().apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
        isAntiAlias = true
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.translate(0f, dy.toPx())
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        canvas.nativeCanvas.restore()
    }
}

/**
 * A group of rows on one frosted panel. The overline header sits on the world above it, so
 * the panel itself never has to invent a second type hierarchy.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    header: String? = null,
    shape: Shape = RoundedCornerShape(GlassTokens.rPanel),
    contentPadding: PaddingValues = PaddingValues(GlassTokens.panelPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier) {
        if (header != null) {
            AFText(
                text = header.uppercase(),
                style = AFTheme.type.overline,
                color = AFTheme.colors.textLo,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
        GlassSurface(tier = GlassTier.FROSTED, shape = shape) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}
