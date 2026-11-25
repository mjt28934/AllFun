package app.allfun.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.allfun.ui.theme.LocalReduceMotion
import kotlin.math.roundToInt

/** 56dp capsule with the accent under it. One per screen. */
@Composable
fun GlassPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true
) {
    val colors = AFTheme.colors
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "primaryScale"
    )
    Box(
        modifier = modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.26f))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        1f to Color.Transparent
                    )
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0f to colors.accent.copy(alpha = 0.75f),
                        1f to colors.accent.copy(alpha = 0.25f)
                    ),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled && !loading
            ) {
                haptics.medium()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (loading) {
                AFSpinner(size = 18.dp, color = colors.textHi, stroke = 2.dp)
            } else if (icon != null) {
                AFIcon(icon, contentDescription = null, tint = colors.textHi, size = 20.dp)
            }
            AFText(
                text = label,
                style = AFTheme.type.heading,
                color = colors.textHi,
                maxLines = 1,
                onGlass = true
            )
        }
    }
}

/** 48dp clear capsule for the second action. */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val colors = AFTheme.colors
    val haptics = rememberHaptics()
    GlassSurface(
        modifier = modifier.height(48.dp),
        tier = GlassTier.CLEAR,
        shape = CircleShape,
        enabled = enabled,
        onClick = {
            haptics.tick()
            onClick()
        }
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                AFIcon(icon, null, tint = colors.textMid, size = 18.dp)
            }
            AFText(label, style = AFTheme.type.label, color = colors.textHi, maxLines = 1)
        }
    }
}

/** 52x32 track, 28dp lens that grows while held. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = AFTheme.colors
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "switchProgress"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "thumbScale"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "thumbOffset"
    )
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .background(
                if (checked) colors.accent.copy(alpha = 0.22f)
                else if (colors.isLight) Color.Black.copy(alpha = 0.07f)
                else Color.White.copy(alpha = 0.07f)
            )
            .drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = if (colors.isLight) 0.6f else 0.20f),
                        1f to Color.Transparent
                    ),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled && onCheckedChange != null
            ) {
                haptics.tick()
                onCheckedChange?.invoke(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .graphicsLayer {
                    scaleX = if (reduce) 1f else thumbScale
                    scaleY = if (reduce) 1f else thumbScale
                }
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White,
                        1f to Color(0xFFE8E6E1)
                    )
                )
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.9f),
                            1f to Color.Transparent,
                            center = Offset(size.width * 0.35f, size.height * 0.3f),
                            radius = size.minDimension * 0.7f
                        )
                    )
                    drawCircle(color = Color.Black.copy(alpha = 0.10f), style = Stroke(1.dp.toPx()))
                }
        )
    }
}

/** A sliding blob between equally weighted segments. */
@Composable
fun GlassSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val colors = AFTheme.colors
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(
                if (colors.isLight) Color.Black.copy(alpha = 0.05f)
                else Color.White.copy(alpha = 0.05f)
            )
            .drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = if (colors.isLight) 0.5f else 0.16f),
                        1f to Color.Transparent
                    ),
                    style = Stroke(1.dp.toPx())
                )
            }
    ) {
        val segment = maxWidth / options.size
        val blobX by animateDpAsState(
            targetValue = segment * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segmentBlob"
        )
        Box(
            Modifier
                .offset(x = blobX, y = 3.dp)
                .width(segment)
                .height(34.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.18f))
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0f to colors.accent.copy(alpha = 0.6f),
                            1f to colors.accent.copy(alpha = 0.12f)
                        ),
                        style = Stroke(1.dp.toPx())
                    )
                }
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (index != selectedIndex) {
                                haptics.tick()
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AFText(
                        text = option,
                        style = AFTheme.type.label,
                        color = if (index == selectedIndex) colors.textHi else colors.textLo,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Small selectable capsule. */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int? = null,
    icon: ImageVector? = null
) {
    val colors = AFTheme.colors
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(
                if (selected) colors.accent.copy(alpha = 0.18f)
                else if (colors.isLight) Color.Black.copy(alpha = 0.04f)
                else Color.White.copy(alpha = 0.05f)
            )
            .drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = if (colors.isLight) 0.5f else 0.18f),
                        1f to Color.Transparent
                    ),
                    style = Stroke(1.dp.toPx())
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptics.tick()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                AFIcon(
                    icon,
                    null,
                    tint = if (selected) colors.accent else colors.textMid,
                    size = 16.dp
                )
            }
            AFText(
                text = label,
                style = AFTheme.type.label,
                color = if (selected) colors.accent else colors.textMid,
                maxLines = 1
            )
            if (badge != null && badge > 0) {
                AFText(
                    text = badge.toString(),
                    style = AFTheme.type.numeric,
                    color = if (selected) colors.accent else colors.textLo
                )
            }
        }
    }
}

/** Tier C track with a glass thumb; commits once the finger lifts. */
@Composable
fun GlassSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AFTheme.colors
    val haptics = rememberHaptics()
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val currentChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun emit(x: Float) {
                        val f = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        currentChange(valueRange.start + f * span)
                    }
                    emit(down.position.x)
                    drag(down.id) { change ->
                        emit(change.position.x)
                        change.consume()
                    }
                    haptics.tick()
                    currentFinished()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center)
        ) {
            val radius = size.height / 2f
            drawRoundRect(
                color = if (colors.isLight) Color.Black.copy(alpha = 0.10f)
                else Color.White.copy(alpha = 0.10f),
                cornerRadius = CornerRadius(radius)
            )
            val w = (size.width * fraction).coerceAtLeast(size.height)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0f to colors.accent.copy(alpha = 0.55f),
                    1f to colors.accent
                ),
                size = Size(w, size.height),
                cornerRadius = CornerRadius(radius)
            )
        }
        Box(
            Modifier
                .offset(x = (maxWidth - 20.dp) * fraction)
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White,
                        1f to Color(0xFFE8E6E1)
                    )
                )
                .drawBehind {
                    drawCircle(color = Color.Black.copy(alpha = 0.16f), style = Stroke(1.dp.toPx()))
                }
        )
    }
}

/** Tier B input, 52dp tall, accent ring only while focused. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    shape: RoundedCornerShape = RoundedCornerShape(GlassTokens.rInput)
) {
    val colors = AFTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = AFTheme.type.body.copy(color = colors.textHi),
        cursorBrush = SolidColor(colors.accent),
        interactionSource = source,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        modifier = modifier,
        decorationBox = { inner ->
            GlassSurface(
                tier = GlassTier.FROSTED,
                shape = shape,
                selected = focused
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (leading != null) {
                        AFIcon(leading, null, tint = colors.textLo, size = 20.dp)
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            AFText(
                                text = placeholder,
                                style = AFTheme.type.body,
                                color = colors.textLo,
                                maxLines = 1
                            )
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            }
        }
    )
}

/** 4dp bar: inset track, accent fill, specular dot at the head. */
@Composable
fun GlassProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    accent: Color = AFTheme.colors.accent
) {
    val colors = AFTheme.colors
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = if (colors.isLight) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f),
            cornerRadius = CornerRadius(radius)
        )
        if (animated > 0f) {
            val w = (size.width * animated).coerceAtLeast(size.height)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0f to accent.copy(alpha = 0.85f),
                    1f to accent
                ),
                size = Size(w, size.height),
                cornerRadius = CornerRadius(radius)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = radius * 0.9f,
                center = Offset(w - radius, size.height / 2f)
            )
        }
    }
}

@Composable
fun GlassBadge(text: String, modifier: Modifier = Modifier, tone: Color? = null) {
    val colors = AFTheme.colors
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(GlassTokens.rBadge))
            .background(
                (tone ?: colors.textHi).copy(alpha = 0.10f)
            ),
        contentAlignment = Alignment.Center
    ) {
        AFText(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp),
            style = AFTheme.type.caption,
            color = tone ?: colors.textMid,
            maxLines = 1
        )
    }
}

/** Arc spinner. Avoids pulling a Material widget into a glass surface. */
@Composable
fun AFSpinner(
    size: Dp = 18.dp,
    color: Color = AFTheme.colors.accent,
    stroke: Dp = 2.dp
) {
    val reduce = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerAngle"
    )
    Canvas(Modifier.size(size)) {
        drawArc(
            color = color.copy(alpha = 0.18f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = if (reduce) 0f else angle,
            sweepAngle = 110f,
            useCenter = false,
            style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** Row of text and a status dot, always icon + word, never colour alone. */
@Composable
fun StatusLine(
    text: String,
    tone: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tone)
        )
        Spacer(Modifier.width(8.dp))
        AFText(text, style = AFTheme.type.caption, color = AFTheme.colors.textMid, maxLines = 1)
    }
}

/** Progress value as a whole number, for labels. */
fun Float.asPercent(): String = "${(this * 100f).roundToInt()}%"
