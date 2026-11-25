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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
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
    seed: Int = 0
) {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF0A0B0E))

    fun blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
        val r = maxOf(w, h) * radius
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = alpha * intensity),
                0.55f to color.copy(alpha = alpha * intensity * 0.35f),
                1f to color.copy(alpha = 0f),
                center = Offset(w * cx, h * cy),
                radius = r
            ),
            radius = r,
            center = Offset(w * cx, h * cy)
        )
    }

    val drift = (seed % 5) * 0.012f
    blob(palette.primary, 0.86f - drift, 0.02f, 0.62f, 0.20f)
    blob(palette.secondary, 0.06f, 0.44f, 0.70f, 0.30f)
    blob(palette.warm, 0.70f, 0.92f, 0.55f, 0.22f)
    blob(palette.primary, 0.38f, 0.66f, 0.40f, 0.07f)
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
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawAmbientField(palette, intensity = intensity)
            drawGrain(0.035f)
        }
        content()
    }
}

/**
 * A clipped, slightly refracted copy of the field, used as the body of a frosted panel. The
 * panel is not sampling the screen: it draws the same field, scaled and shifted, which is
 * what makes the glass feel continuous without a live backdrop read.
 */
fun DrawScope.drawWorldLens(palette: WorldPalette, shift: Offset, zoom: Float = 1.06f) {
    inset(-size.width * (zoom - 1f) / 2f - shift.x, -size.height * (zoom - 1f) / 2f - shift.y) {
        drawAmbientField(palette, intensity = 0.9f)
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
