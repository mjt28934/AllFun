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
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.allfun.ui.theme.LocalReduceMotion
import kotlin.math.min

enum class GlassTier {
    /** L3 controls: nav, capsules, primary action. */
    CLEAR,

    /** L2 panels and sheets. */
    FROSTED,

    /** Controls inside a panel: tint, rim and refraction only. */
    INSET
}

/**
 * The single material primitive. Every surface in the app is one of three tiers of this
 * composable, so refraction, specular edge and press behaviour can never drift apart.
 *
 * The body samples the screen's world field at the surface's own position (see [WorldField]),
 * so a panel shows the part of the backdrop it covers, magnified. The edge band magnifies
 * harder, which is the lens bend; a sweep-gradient rim carries the light.
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
    tint: Color? = null,
    tintAlpha: Float = 0f,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AFTheme.colors
    val level = AFTheme.level
    val reduceMotion = LocalReduceMotion.current
    val field = LocalWorldField.current
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
    // Read only in the draw phase: moving glass re-draws, it never recomposes.
    val origin = remember { mutableStateOf(Offset.Zero) }

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
                        blur = if (level == GlassLevel.LITE) 16.dp else 28.dp,
                        dy = if (level == GlassLevel.LITE) 6.dp else 10.dp
                    )
                } else {
                    Modifier
                }
            )
            .onGloballyPositioned { origin.value = it.boundsInRoot().topLeft }
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
                    accent = accent,
                    field = field,
                    panelOrigin = origin.value,
                    tint = tint,
                    tintAlpha = tintAlpha
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
    accent: Color,
    field: WorldField?,
    panelOrigin: Offset,
    tint: Color?,
    tintAlpha: Float
) {
    val path = shape.createOutline(size, LayoutDirection.Ltr, this).toComposePath()
    val inset = min(size.width, size.height) * 0.18f
    val edge = min(
        if (tier == GlassTier.CLEAR) 16.dp.toPx() else 22.dp.toPx(),
        inset
    )
    val ring = if (tier != GlassTier.INSET && level != GlassLevel.LITE) {
        edgeRing(path, shape, edge)
    } else {
        null
    }
    val hot = if (colors.isLight) 0.5f else if (tier == GlassTier.FROSTED) 0.06f else 0.11f

    clipPath(path) {
        when (tier) {
            GlassTier.FROSTED -> {
                if (level != GlassLevel.LITE) {
                    drawWorldLens(field, palette, panelOrigin, zoom = 1.05f)
                }
            }

            GlassTier.CLEAR -> {
                if (level != GlassLevel.LITE) {
                    drawWorldLens(field, palette, panelOrigin, zoom = 1.12f)
                }
            }

            GlassTier.INSET -> Unit
        }

        // The bend: the outer band samples further out than the middle does. Drawn before the
        // body tint so a tinted control stays tinted to its edge instead of growing a clear rim.
        if (ring != null) {
            clipPath(ring) {
                if (level == GlassLevel.LITE) {
                    drawRect(palette.primary.copy(alpha = 0.10f))
                } else {
                    drawWorldLens(
                        field = field,
                        palette = palette,
                        panelOrigin = panelOrigin,
                        zoom = if (tier == GlassTier.CLEAR) 1.45f else 1.26f
                    )
                }
            }
            drawPath(
                path = ring,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (colors.isLight) 0.38f else 0.10f),
                    0.45f to Color.White.copy(alpha = if (colors.isLight) 0.06f else 0.02f),
                    1f to Color.White.copy(alpha = if (colors.isLight) 0.10f else 0.03f)
                )
            )
        }

        when (tier) {
            GlassTier.FROSTED -> {
                // Frost: enough neutral wash for 4.5:1 text over a moving field.
                drawRect(colors.bg0.copy(alpha = colors.frostTint))
                drawRect(
                    if (colors.isLight) Color.White.copy(alpha = 0.16f)
                    else Color.White.copy(alpha = 0.035f)
                )
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (colors.isLight) 0.30f else 0.07f),
                        0.5f to Color.Transparent
                    )
                )
            }

            GlassTier.CLEAR -> {
                drawRect(colors.bg0.copy(alpha = colors.clearTint * 0.7f))
                drawRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = if (colors.isLight) 0.34f else 0.09f),
                        0.5f to Color.White.copy(alpha = if (colors.isLight) 0.10f else 0.015f),
                        1f to Color.Transparent,
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.85f, size.height)
                    )
                )
            }

            GlassTier.INSET -> {
                drawRect(
                    if (colors.isLight) Color.Black.copy(alpha = 0.05f)
                    else Color.White.copy(alpha = 0.06f)
                )
            }
        }

        if (tint != null && tintAlpha > 0f) {
            drawRect(tint.copy(alpha = tintAlpha))
        }
        if (selected) {
            drawRect(accent.copy(alpha = if (colors.isLight) 0.16f else 0.12f))
        }

        // Thickness: a soft shadow under the lit top edge and a deeper one at the bottom.
        if (tier != GlassTier.INSET) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = if (colors.isLight) 0.06f else 0.20f),
                    1f to Color.Transparent
                ),
                size = Size(size.width, min(size.height * 0.2f, 8.dp.toPx()))
            )
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = if (colors.isLight) 0.04f else 0.15f)
                ),
                topLeft = Offset(0f, size.height * 0.55f),
                size = Size(size.width, size.height * 0.45f)
            )
        }

        if (tier != GlassTier.INSET && level != GlassLevel.LITE) {
            drawGrain(if (tier == GlassTier.FROSTED) 0.022f else 0.012f)
        }

        // A broad light source caught in the upper-left corner.
        if (tier != GlassTier.INSET) {
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = hot),
                    0.45f to Color.White.copy(alpha = hot * 0.3f),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.18f, 0f),
                    radius = maxOf(size.width, size.height) * 0.9f
                )
            )
        }

        if (pressed) {
            drawRect(Color.White.copy(alpha = if (colors.isLight) 0.16f else 0.06f))
        }

        if (sweep in 0f..1f && sweep > 0f) {
            val cx = size.width * (sweep * 1.7f - 0.35f)
            val band = 56.dp.toPx()
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = if (colors.isLight) 0.14f else 0.07f),
                    1f to Color.Transparent,
                    start = Offset(cx - band, 0f),
                    end = Offset(cx + band, size.height * 0.6f)
                )
            )
        }
    }

    if (tier == GlassTier.INSET) {
        // No sweep here: an inset control is a tint, not a second sheet of glass.
        clipPath(path) {
            drawPath(
                path = path,
                color = if (colors.isLight) Color(0x16000000) else Color(0x1FFFFFFF),
                style = Stroke(width = 2.dp.toPx())
            )
        }
        return
    }

    // Specular rim. A vertical gradient keeps the lit edge on top and the shaded edge at the
    // bottom, which is how a convex lens catches a light above it. A linear gradient is used
    // instead of a sweep so the highlight can never rotate on a wide or narrow control.
    val rim = if (colors.isLight) {
        Brush.verticalGradient(
            0f to Color(0x4D000000),
            0.45f to Color(0x1F000000),
            1f to Color(0x14000000)
        )
    } else {
        Brush.verticalGradient(
            0f to Color(0xA6FFFFFF),
            0.3f to Color(0x4DFFFFFF),
            0.65f to Color(0x1FFFFFFF),
            1f to Color(0x29FFFFFF)
        )
    }
    clipPath(path) {
        drawPath(path, brush = rim, style = Stroke(width = 1.8.dp.toPx()))
    }
}

/**
 * A ring of [inset] around the shape's edge, built as the difference between the outer
 * outline and a concentric inner one, so corners keep the same band width.
 */
private fun DrawScope.edgeRing(outer: Path, shape: Shape, inset: Float): Path? {
    if (inset <= 0.5f || size.width <= inset * 2.2f || size.height <= inset * 2.2f) return null
    val reduced = Size(size.width - inset * 2f, size.height - inset * 2f)
    val rounded = shape as? RoundedCornerShape
    val innerShape = rounded?.let {
        val outerRadius = it.topStart.toPx(size, this)
        RoundedCornerShape(CornerSize((outerRadius - inset).coerceAtLeast(0f)))
    } ?: shape
    val inner = innerShape.createOutline(reduced, LayoutDirection.Ltr, this).toComposePath()
    inner.translate(Offset(inset, inset))
    val ring = Path()
    return if (ring.op(outer, inner, PathOperation.Difference)) ring else null
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
