package app.allfun.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.allfun.data.settings.AccentPalette

/** How much of the glass system is allowed to run. Every level still looks like glass. */
enum class GlassLevel { HIGH, STANDARD, LITE }

val LocalAFColors = staticCompositionLocalOf { darkAFColors(Section.SYSTEM.accent) }
val LocalAFType = staticCompositionLocalOf { afType() }
val LocalGlassLevel = staticCompositionLocalOf { GlassLevel.STANDARD }
val LocalAmbientField = staticCompositionLocalOf { true }
val LocalTiltField = staticCompositionLocalOf { true }
val LocalWorldPalette = staticCompositionLocalOf { worldPalette(Section.SYSTEM) }

/** Read the current material from anywhere without dragging a theme parameter through. */
object AFTheme {
    val colors: AFColors
        @Composable @ReadOnlyComposable get() = LocalAFColors.current

    val type: AFType
        @Composable @ReadOnlyComposable get() = LocalAFType.current

    val level: GlassLevel
        @Composable @ReadOnlyComposable get() = LocalGlassLevel.current

    val ambient: Boolean
        @Composable @ReadOnlyComposable get() = LocalAmbientField.current

    val tilt: Boolean
        @Composable @ReadOnlyComposable get() = LocalTiltField.current
}

/** The palette the user picked still matters: it replaces the section hue when set. */
fun AccentPalette.glassAccent(fallback: Color): Color = when (this) {
    AccentPalette.VIOLET -> fallback
    AccentPalette.OCEAN -> Color(0xFF63B3FF)
    AccentPalette.EMERALD -> Color(0xFF4ADE9A)
    AccentPalette.AMBER -> Color(0xFFF5B44C)
    AccentPalette.ROSE -> Color(0xFFF472B6)
}

fun darkAFColors(accent: Color, amoled: Boolean = false): AFColors = AFColors(
    bg0 = if (amoled) Color.Black else GlassTokens.Bg0,
    bg1 = if (amoled) Color(0xFF070708) else GlassTokens.Bg1,
    bg2 = if (amoled) Color(0xFF0D0D10) else GlassTokens.Bg2,
    textHi = GlassTokens.TextHi,
    textMid = GlassTokens.TextMid,
    textLo = GlassTokens.TextLo,
    hairline = GlassTokens.Hairline,
    accent = accent,
    accentWash = accent.copy(alpha = 0.12f),
    accentContainer = accent.copy(alpha = 0.20f),
    onAccent = Color(0xFF16070C),
    clearTint = if (amoled) GlassTokens.AMOLED_TINT else GlassTokens.CLEAR_TINT,
    frostTint = if (amoled) GlassTokens.AMOLED_TINT else GlassTokens.FROST_TINT,
    isLight = false
)

fun lightAFColors(accent: Color): AFColors = AFColors(
    bg0 = GlassTokens.Bg0Light,
    bg1 = GlassTokens.Bg1Light,
    bg2 = GlassTokens.Bg2Light,
    textHi = GlassTokens.TextHiLight,
    textMid = GlassTokens.TextMidLight,
    textLo = GlassTokens.TextLoLight,
    hairline = GlassTokens.HairlineLight,
    accent = accent,
    accentWash = accent.copy(alpha = 0.14f),
    accentContainer = accent.copy(alpha = 0.22f),
    onAccent = Color.White,
    clearTint = 0.30f,
    clearWhite = 0.30f,
    frostTint = GlassTokens.LIGHT_TINT,
    isLight = true
)

/** Re-provide the palette with this section's hue, for one destination or one detail page. */
@Composable
fun SectionScope(section: Section, content: @Composable () -> Unit) {
    val base = LocalAFColors.current
    val accent = section.accent
    CompositionLocalProvider(
        LocalAFColors provides base.copy(
            accent = accent,
            accentWash = accent.copy(alpha = if (base.isLight) 0.14f else 0.12f),
            accentContainer = accent.copy(alpha = if (base.isLight) 0.22f else 0.20f)
        ),
        LocalWorldPalette provides worldPalette(section),
        content = content
    )
}

/** Small helper so text on glass always carries the same 1dp shadow. */
fun TextStyle.onGlass(): TextStyle = copy(
    shadow = androidx.compose.ui.graphics.Shadow(
        color = Color(0x59000000),
        offset = androidx.compose.ui.geometry.Offset(0f, 1f),
        blurRadius = 2f
    )
)
