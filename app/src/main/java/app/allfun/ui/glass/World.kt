package app.allfun.ui.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.random.Random

/**
 * The world the glass bends: a pinned field of three soft blobs over bg0. It never scrolls,
 * never loops and is regenerated only when the section hue changes.
 */
data class WorldPalette(
    val primary: Color,
    val secondary: Color,
    val warm: Color
)

fun worldPalette(section: Section): WorldPalette = when (section) {
    Section.MOVIES -> WorldPalette(
        Color(0xFFFF5E8A), Color(0xFF243B66), Color(0xFF3A2218)
    )
    Section.BOOKS -> WorldPalette(
        Color(0xFF2CC5C0), Color(0xFF173B33), Color(0xFF3A2C18)
    )
    Section.MANGA -> WorldPalette(
        Color(0xFF8B7CFF), Color(0xFF1C2350), Color(0xFF2A1B33)
    )
    Section.LIBRARY -> WorldPalette(
        Color(0xFFE7B36B), Color(0xFF2E2A3F), Color(0xFF38231A)
    )
    Section.SYSTEM -> WorldPalette(
        Color(0xFF8B7CFF), Color(0xFF1A2330), Color(0xFF2A2118)
    )
}

/** A fixed, resolution-independent layout so the field reads the same on every screen. */
fun DrawScope.drawAmbientField(
    palette: WorldPalette,
    intensity: Float = 1f,
    seed: Int = 0,
    width: Float = size.width,
    height: Float = size.height,
    origin: Offset = Offset.Zero
) {
    drawRect(
        color = Color(0xFF08090C),
        topLeft = origin,
        size = Size(width, height)
    )

    fun blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
        val r = maxOf(width, height) * radius
        val center = Offset(origin.x + width * cx, origin.y + height * cy)
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = alpha * intensity),
                0.5f to color.copy(alpha = alpha * intensity * 0.42f),
                1f to color.copy(alpha = 0f),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )
    }

    val drift = (seed % 5) * 0.012f
    blob(palette.primary, 0.84f - drift, 0.00f, 0.72f, 0.30f)
    blob(palette.secondary, 0.02f, 0.46f, 0.80f, 0.28f)
    blob(palette.warm, 0.72f, 0.98f, 0.62f, 0.24f)
    blob(palette.primary, 0.40f, 0.64f, 0.44f, 0.08f)

    // A broad cool light from above. Glass only reads as glass when something reflects.
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF9FB4D8).copy(alpha = 0.07f * intensity),
            0.55f to Color.Transparent,
            startY = origin.y,
            endY = origin.y + height * 0.75f
        ),
        topLeft = origin,
        size = Size(width, height * 0.75f)
    )

    // A soft diagonal shaft gives the lens a gradient edge to bend, instead of a flat field.
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.34f to Color.White.copy(alpha = 0.05f * intensity),
            0.6f to Color.Transparent,
            start = Offset(origin.x, origin.y + height * 0.04f),
            end = Offset(origin.x + width, origin.y + height * 0.62f)
        ),
        topLeft = origin,
        size = Size(width, height)
    )

    // Vignette: the field falls away at the edges so chrome stays legible over it.
    drawRect(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.40f * intensity),
            center = Offset(origin.x + width * 0.5f, origin.y + height * 0.32f),
            radius = maxOf(width, height) * 0.85f
        ),
        topLeft = origin,
        size = Size(width, height)
    )
}

private fun grainBitmap(): Bitmap {
    val size = 96
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val rnd = Random(11)
    val pixels = IntArray(size * size)
    for (i in pixels.indices) {
        val v = rnd.nextInt(256)
        pixels[i] = android.graphics.Color.argb(255, v, v, v)
    }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    return bmp
}

/** 2% monochrome grain. Stops the blurred field from banding on cheap panels. */
fun DrawScope.drawGrain(alpha: Float = 0.03f) {
    val shader = grainShaderOrNull() ?: return
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.shader = shader
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        }
        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}

private var cachedGrain: BitmapShader? = null

private fun grainShaderOrNull(): BitmapShader? {
    cachedGrain?.let { return it }
    return runCatching {
        BitmapShader(grainBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            .also { cachedGrain = it }
    }.getOrNull()
}

/**
 * The field currently behind the screen, in root coordinates. Glass surfaces read this to
 * sample the world at their own position: a panel then shows the part of the field it
 * actually covers, magnified a little, which is what sells it as a lens instead of a tint.
 */
data class WorldField(
    val palette: WorldPalette,
    val intensity: Float = 1f,
    val origin: Offset = Offset.Zero,
    val width: Float = 0f,
    val height: Float = 0f
) {
    val valid: Boolean get() = width > 0f && height > 0f
}

val LocalWorldField = staticCompositionLocalOf<WorldField?> { null }

/**
 * Full-screen backdrop. The field is static, so it is drawn once per size change and the
 * content scrolls over it.
 */
@Composable
fun WorldBackdrop(
    section: Section,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = remember(section) { worldPalette(section) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawAmbientField(palette, intensity = intensity)
            drawGrain(0.035f)
        }
        CompositionLocalProvider(
            LocalWorldField provides WorldField(
                palette = palette,
                intensity = intensity,
                origin = bounds.topLeft,
                width = bounds.width,
                height = bounds.height
            )
        ) {
            content()
        }
    }
}

/**
 * Draws the world as seen through a panel: magnified about the panel's centre so the edges
 * sample further out than the middle. This is the refraction, done arithmetically because the
 * field is deterministic. No backdrop read, no second blur pass.
 */
fun DrawScope.drawWorldLens(
    field: WorldField?,
    palette: WorldPalette,
    panelOrigin: Offset,
    zoom: Float = 1.08f
) {
    if (field == null || !field.valid) {
        // Dialogs and previews have no backdrop: bend a local copy of the same field.
        drawAmbientField(palette, intensity = 0.9f)
        return
    }
    val center = Offset(size.width / 2f, size.height / 2f)
    withTransform({
        scale(zoom, zoom, pivot = center)
        translate(field.origin.x - panelOrigin.x, field.origin.y - panelOrigin.y)
    }) {
        drawAmbientField(
            palette = field.palette,
            intensity = field.intensity,
            width = field.width,
            height = field.height
        )
    }
}

/** Vertical hairline used between rows inside a panel. */
fun DrawScope.drawHairline(color: Color, startX: Float = 0f, endX: Float = size.width) {
    drawLine(
        color = color,
        start = Offset(startX, 0f),
        end = Offset(endX, 0f),
        strokeWidth = 1f
    )
}
