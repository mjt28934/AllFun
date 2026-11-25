package app.allfun.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allfun.data.settings.AccentPalette
import app.allfun.data.settings.CornerStyle
import app.allfun.data.settings.Settings
import app.allfun.data.settings.ThemeMode

val Ink = Color(0xFF0A0A0F)
val InkRaised = Color(0xFF13131B)
val InkCard = Color(0xFF1B1B26)
val Violet = Color(0xFF8A6BFF)
val Cyan = Color(0xFF2DD4E8)
val Rose = Color(0xFFF472B6)
val Paper = Color(0xFFF4F1EA)
val Amber = Color(0xFFF5B44C)

/** A calm, slightly raised tone used for borders and dividers. */
val InkLine = Color(0xFF2A2A38)

/** True when the user asked for less motion; every animated surface checks this. */
val LocalReduceMotion = staticCompositionLocalOf { false }

private val DarkScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF130E26),
    primaryContainer = Color(0xFF2C2354),
    onPrimaryContainer = Color(0xFFE9E3FF),
    secondary = Cyan,
    onSecondary = Color(0xFF002025),
    secondaryContainer = Color(0xFF073840),
    onSecondaryContainer = Color(0xFFC9F6FC),
    tertiary = Rose,
    onTertiary = Color(0xFF2A0719),
    tertiaryContainer = Color(0xFF45122B),
    onTertiaryContainer = Color(0xFFFFD9E8),
    background = Ink,
    onBackground = Color(0xFFF2F1F8),
    surface = Ink,
    onSurface = Color(0xFFF2F1F8),
    surfaceVariant = InkCard,
    onSurfaceVariant = Color(0xFFB9B7C9),
    surfaceContainerLowest = Color(0xFF06060A),
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = Color(0xFF1F1F2B),
    surfaceContainerHighest = Color(0xFF282836),
    outline = Color(0xFF73738A),
    outlineVariant = InkLine,
    error = Color(0xFFFF8A9B),
    onError = Color(0xFF3A0710),
    errorContainer = Color(0xFF4A111C),
    onErrorContainer = Color(0xFFFFD9DE),
    scrim = Color(0xFF000000)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3FE0),
    onPrimary = Color.White,
    secondary = Color(0xFF0E7490),
    tertiary = Color(0xFFBE185D),
    background = Color(0xFFFDFCFF),
    surface = Color(0xFFFDFCFF),
    surfaceVariant = Color(0xFFEDEBF5),
    onSurfaceVariant = Color(0xFF4C4A59),
    surfaceContainer = Color(0xFFF4F2FA),
    surfaceContainerHigh = Color(0xFFECE9F4),
    outline = Color(0xFF74717F)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.1).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

/** One accent reads as a family: primary trio plus the matching secondary tone. */
private data class Accent(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color
)

private fun accentFor(palette: AccentPalette, dark: Boolean): Accent = when (palette) {
    AccentPalette.VIOLET -> if (dark) {
        Accent(Violet, Color(0xFF130E26), Color(0xFF2C2354), Color(0xFFE9E3FF), Cyan)
    } else {
        Accent(
            Color(0xFF5B3FE0), Color.White, Color(0xFFE7E0FF), Color(0xFF1D0F5C),
            Color(0xFF0E7490)
        )
    }
    AccentPalette.OCEAN -> if (dark) {
        Accent(
            Color(0xFF63B3FF), Color(0xFF04121F), Color(0xFF10314D), Color(0xFFD8EDFF),
            Color(0xFF4FD1C5)
        )
    } else {
        Accent(
            Color(0xFF0B63B7), Color.White, Color(0xFFD7EAFF), Color(0xFF04264A),
            Color(0xFF0F766E)
        )
    }
    AccentPalette.EMERALD -> if (dark) {
        Accent(
            Color(0xFF4ADE9A), Color(0xFF04241A), Color(0xFF0D3A28), Color(0xFFD2FBE5),
            Color(0xFF6EE7F9)
        )
    } else {
        Accent(
            Color(0xFF0F7A4D), Color.White, Color(0xFFD3F5E4), Color(0xFF03301C),
            Color(0xFF0E7490)
        )
    }
    AccentPalette.AMBER -> if (dark) {
        Accent(
            Color(0xFFF5B44C), Color(0xFF2A1A00), Color(0xFF3D2A08), Color(0xFFFFE7C2),
            Color(0xFFF59E7C)
        )
    } else {
        Accent(
            Color(0xFF8A5A00), Color.White, Color(0xFFFFEBC7), Color(0xFF2C1A00),
            Color(0xFFB45309)
        )
    }
    AccentPalette.ROSE -> if (dark) {
        Accent(
            Color(0xFFF472B6), Color(0xFF2A0719), Color(0xFF43122A), Color(0xFFFFD9E8),
            Color(0xFFA78BFA)
        )
    } else {
        Accent(
            Color(0xFFBE185D), Color.White, Color(0xFFFFD9E5), Color(0xFF3F0018),
            Color(0xFF7C3AED)
        )
    }
}

private fun ColorScheme.withAccent(accent: Accent): ColorScheme = copy(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    secondary = accent.secondary
)

/** Corner style changes only the radius scale, so every screen follows the same look. */
private fun shapesFor(style: CornerStyle): Shapes = when (style) {
    CornerStyle.ROUNDED -> AppShapes
    CornerStyle.SOFT -> Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(19.dp)
    )
    CornerStyle.SHARP -> Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    )
}

/**
 * A colour that travels to its new value instead of jumping. Switching accent palette or
 * light/dark now blends across every screen rather than flashing a new theme into place.
 */
@Composable
private fun animatedColor(target: Color, label: String): Color =
    animateColorAsState(
        targetValue = target,
        animationSpec = tween(Motion.MEDIUM, easing = Motion.EaseInOut),
        label = label
    ).value

@Composable
private fun animatedScheme(target: ColorScheme): ColorScheme {
    if (LocalReduceMotion.current) return target
    // Every role is animated, not just the accents: a dark-to-light switch would otherwise
    // show new dark text over a still-dark background for a fifth of a second.
    return target.copy(
        primary = animatedColor(target.primary, "primary"),
        onPrimary = animatedColor(target.onPrimary, "onPrimary"),
        primaryContainer = animatedColor(target.primaryContainer, "primaryContainer"),
        onPrimaryContainer = animatedColor(target.onPrimaryContainer, "onPrimaryContainer"),
        inversePrimary = animatedColor(target.inversePrimary, "inversePrimary"),
        secondary = animatedColor(target.secondary, "secondary"),
        onSecondary = animatedColor(target.onSecondary, "onSecondary"),
        secondaryContainer = animatedColor(target.secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = animatedColor(
            target.onSecondaryContainer,
            "onSecondaryContainer"
        ),
        tertiary = animatedColor(target.tertiary, "tertiary"),
        onTertiary = animatedColor(target.onTertiary, "onTertiary"),
        tertiaryContainer = animatedColor(target.tertiaryContainer, "tertiaryContainer"),
        onTertiaryContainer = animatedColor(target.onTertiaryContainer, "onTertiaryContainer"),
        background = animatedColor(target.background, "background"),
        onBackground = animatedColor(target.onBackground, "onBackground"),
        surface = animatedColor(target.surface, "surface"),
        onSurface = animatedColor(target.onSurface, "onSurface"),
        surfaceVariant = animatedColor(target.surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = animatedColor(target.onSurfaceVariant, "onSurfaceVariant"),
        surfaceTint = animatedColor(target.surfaceTint, "surfaceTint"),
        inverseSurface = animatedColor(target.inverseSurface, "inverseSurface"),
        inverseOnSurface = animatedColor(target.inverseOnSurface, "inverseOnSurface"),
        error = animatedColor(target.error, "error"),
        onError = animatedColor(target.onError, "onError"),
        errorContainer = animatedColor(target.errorContainer, "errorContainer"),
        onErrorContainer = animatedColor(target.onErrorContainer, "onErrorContainer"),
        outline = animatedColor(target.outline, "outline"),
        outlineVariant = animatedColor(target.outlineVariant, "outlineVariant"),
        scrim = animatedColor(target.scrim, "scrim"),
        surfaceBright = animatedColor(target.surfaceBright, "surfaceBright"),
        surfaceDim = animatedColor(target.surfaceDim, "surfaceDim"),
        surfaceContainer = animatedColor(target.surfaceContainer, "surfaceContainer"),
        surfaceContainerHigh = animatedColor(target.surfaceContainerHigh, "surfaceContainerHigh"),
        surfaceContainerHighest = animatedColor(
            target.surfaceContainerHighest,
            "surfaceContainerHighest"
        ),
        surfaceContainerLow = animatedColor(target.surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainerLowest = animatedColor(
            target.surfaceContainerLowest,
            "surfaceContainerLowest"
        )
    )
}

/**
 * The user's explicit setting or the platform's "remove animations" developer option.
 * Android exposes that as an animator duration scale of zero.
 */
@Composable
private fun systemReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
fun AllFunTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val useDynamic =
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        useDynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme.withAccent(accentFor(settings.accent, dark = true))
        else -> LightScheme.withAccent(accentFor(settings.accent, dark = false))
    }
    // OLED mode only removes the layers that would otherwise glow: the wallpaper-derived
    // containers stay as they are when Material You is on, so text keeps its contrast.
    val scheme = if (dark && settings.amoledBlack) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = if (useDynamic) base.surfaceContainerLow else Color(0xFF07070A),
            surfaceContainer = if (useDynamic) base.surfaceContainer else Color(0xFF0B0B10),
            surfaceContainerHigh = if (useDynamic) base.surfaceContainerHigh else Color(0xFF121218),
            surfaceContainerHighest = if (useDynamic) base.surfaceContainerHighest else Color(0xFF181820)
        )
    } else {
        base
    }
    val reduceMotion = settings.reduceMotion || systemReduceMotion()
    val density = LocalDensity.current
    val fontScale = settings.appTextSize.scale
    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalDensity provides if (fontScale == 1f) density
        else Density(density.density, density.fontScale * fontScale)
    ) {
        MaterialTheme(
            colorScheme = animatedScheme(scheme),
            typography = AppTypography,
            shapes = shapesFor(settings.cornerStyle),
            content = content
        )
    }
}
