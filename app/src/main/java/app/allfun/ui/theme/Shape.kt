package app.allfun.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.allfun.data.settings.CornerStyle

/**
 * Component radii, kept separate from Material's [Shapes] so a cover stays at 4 dp even
 * when a sheet is at 20 dp. The user's corner-style setting shifts the whole scale.
 */
data class AllFunRadii(
    val cover: Dp,
    val badge: Dp,
    val chip: Dp,
    val button: Dp,
    val field: Dp,
    val group: Dp,
    val sheet: Dp
)

fun radiiFor(style: CornerStyle): AllFunRadii = when (style) {
    CornerStyle.ROUNDED -> AllFunRadii(4.dp, 4.dp, 8.dp, 10.dp, 12.dp, 12.dp, 20.dp)
    CornerStyle.SOFT -> AllFunRadii(6.dp, 6.dp, 12.dp, 14.dp, 16.dp, 16.dp, 24.dp)
    CornerStyle.SHARP -> AllFunRadii(2.dp, 2.dp, 4.dp, 6.dp, 6.dp, 6.dp, 8.dp)
}

val LocalAllFunRadii = staticCompositionLocalOf { radiiFor(CornerStyle.ROUNDED) }

/** Material shapes for the stock components that still use them. */
fun materialShapesFor(style: CornerStyle): Shapes = when (style) {
    CornerStyle.ROUNDED -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp)
    )
    CornerStyle.SOFT -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(24.dp)
    )
    CornerStyle.SHARP -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(10.dp)
    )
}
