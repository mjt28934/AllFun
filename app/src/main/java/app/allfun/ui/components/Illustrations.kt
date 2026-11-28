package app.allfun.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.LocalAllFunRadii

/** One drawing per empty screen, same family, never a stock icon in a circle. */
enum class EmptyIllustration {
    SHELF,
    PLUG,
    PUZZLE,
    CHECK,
    CARDS,
    LOUPE
}

@Composable
fun EmptyIllustrationArt(kind: EmptyIllustration, modifier: Modifier = Modifier) {
    val colors = AllFun.colors
    val line = colors.outlineStrong
    val accent = colors.accent
    val radius = LocalAllFunRadii.current
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        val thin = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        when (kind) {
            EmptyIllustration.SHELF -> {
                shelf(w, h, line, thin)
                // Three upright spines and one leaning book in the accent.
                book(Offset(w * 0.16f, h * 0.42f), w * 0.09f, h * 0.42f, line, thin)
                book(Offset(w * 0.28f, h * 0.48f), w * 0.08f, h * 0.36f, line, thin)
                book(Offset(w * 0.39f, h * 0.45f), w * 0.10f, h * 0.39f, line, thin)
                val path = Path().apply {
                    moveTo(w * 0.58f, h * 0.84f)
                    lineTo(w * 0.70f, h * 0.36f)
                    lineTo(w * 0.80f, h * 0.84f)
                }
                drawPath(path, accent, style = thin)
                drawLine(accent, Offset(w * 0.58f, h * 0.84f), Offset(w * 0.80f, h * 0.84f), stroke)
            }
            EmptyIllustration.PLUG -> {
                shelf(w, h, line, thin)
                val socket = Size(w * 0.26f, h * 0.30f)
                val topLeft = Offset(w * 0.30f, h * 0.26f)
                drawRoundRect(
                    color = line,
                    topLeft = topLeft,
                    size = socket,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = thin
                )
                // The live prong is the one accent element.
                drawLine(
                    accent,
                    Offset(topLeft.x + socket.width * 0.34f, topLeft.y - h * 0.12f),
                    Offset(topLeft.x + socket.width * 0.34f, topLeft.y + h * 0.04f),
                    stroke * 1.4f
                )
                drawLine(
                    line,
                    Offset(topLeft.x + socket.width * 0.66f, topLeft.y - h * 0.12f),
                    Offset(topLeft.x + socket.width * 0.66f, topLeft.y + h * 0.04f),
                    stroke
                )
                drawLine(
                    line,
                    Offset(topLeft.x + socket.width * 0.5f, topLeft.y + socket.height),
                    Offset(topLeft.x + socket.width * 0.5f, h * 0.84f),
                    stroke
                )
            }
            EmptyIllustration.PUZZLE -> {
                val cell = w * 0.16f
                val gap = w * 0.05f
                val startX = w * 0.14f
                val startY = h * 0.22f
                for (row in 0..1) {
                    for (col in 0..2) {
                        if (row == 1 && col == 2) continue
                        drawRoundRect(
                            color = line,
                            topLeft = Offset(
                                startX + col * (cell + gap),
                                startY + row * (cell + gap)
                            ),
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                            style = thin
                        )
                    }
                }
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(startX + 2 * (cell + gap), startY + cell + gap),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = stroke * 1.4f)
                )
            }
            EmptyIllustration.CHECK -> {
                // An open bracket with a check inside it.
                val left = w * 0.26f
                val right = w * 0.74f
                val top = h * 0.20f
                val bottom = h * 0.80f
                drawLine(line, Offset(left, bottom), Offset(left, top), stroke)
                drawLine(line, Offset(left, top), Offset(w * 0.36f, top), stroke)
                drawLine(line, Offset(right, bottom), Offset(right, top), stroke)
                drawLine(line, Offset(right, top), Offset(w * 0.64f, top), stroke)
                drawLine(line, Offset(left, bottom), Offset(w * 0.36f, bottom), stroke)
                drawLine(line, Offset(right, bottom), Offset(w * 0.64f, bottom), stroke)
                val check = Path().apply {
                    moveTo(w * 0.40f, h * 0.52f)
                    lineTo(w * 0.48f, h * 0.62f)
                    lineTo(w * 0.62f, h * 0.40f)
                }
                drawPath(check, accent, style = Stroke(width = stroke * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            EmptyIllustration.CARDS -> {
                val cardW = w * 0.44f
                val cardH = h * 0.34f
                drawRoundRect(
                    color = line.copy(alpha = 0.55f),
                    topLeft = Offset(w * 0.20f, h * 0.16f),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(radius.badge.value * 2f),
                    style = thin
                )
                drawRoundRect(
                    color = line,
                    topLeft = Offset(w * 0.28f, h * 0.30f),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(radius.badge.value * 2f),
                    style = thin
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(w * 0.36f, h * 0.44f),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(radius.badge.value * 2f),
                    style = Stroke(width = stroke * 1.4f)
                )
                drawLine(
                    accent,
                    Offset(w * 0.44f, h * 0.54f),
                    Offset(w * 0.64f, h * 0.54f),
                    stroke
                )
            }
            EmptyIllustration.LOUPE -> {
                drawCircle(
                    color = accent,
                    radius = h * 0.24f,
                    center = Offset(w * 0.46f, h * 0.44f),
                    style = Stroke(width = stroke * 1.5f)
                )
                drawLine(
                    line,
                    Offset(w * 0.63f, h * 0.62f),
                    Offset(w * 0.78f, h * 0.78f),
                    stroke * 2f
                )
                drawLine(line, Offset(w * 0.36f, h * 0.38f), Offset(w * 0.56f, h * 0.38f), stroke)
                drawLine(line, Offset(w * 0.36f, h * 0.46f), Offset(w * 0.52f, h * 0.46f), stroke)
                drawLine(line, Offset(w * 0.36f, h * 0.54f), Offset(w * 0.48f, h * 0.54f), stroke)
            }
        }
    }
}

private fun DrawScope.shelf(w: Float, h: Float, line: Color, thin: Stroke) {
    drawLine(line, Offset(w * 0.10f, h * 0.84f), Offset(w * 0.90f, h * 0.84f), thin.width)
    drawLine(
        line.copy(alpha = 0.6f),
        Offset(w * 0.10f, h * 0.88f),
        Offset(w * 0.90f, h * 0.88f),
        thin.width
    )
}

private fun DrawScope.book(
    topLeft: Offset,
    width: Float,
    height: Float,
    line: Color,
    thin: Stroke
) {
    drawRoundRect(
        color = line,
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = thin
    )
    drawLine(
        line.copy(alpha = 0.55f),
        Offset(topLeft.x + width * 0.30f, topLeft.y + height * 0.16f),
        Offset(topLeft.x + width * 0.30f, topLeft.y + height * 0.84f),
        thin.width
    )
}
