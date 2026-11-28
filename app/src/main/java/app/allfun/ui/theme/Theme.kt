package app.allfun.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.allfun.data.settings.Settings
import app.allfun.data.settings.ThemeMode

/** True when the user asked for less motion; every animated surface checks this. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Maps the semantic palette onto Material's roles so stock components (dialogs, menus,
 * text fields) inherit the same colours as the custom ones.
 */
private fun AllFunPalette.toScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = accent,
        inversePrimary = accent,
        secondary = textSecondary,
        onSecondary = bg,
        secondaryContainer = surface2,
        onSecondaryContainer = textPrimary,
        tertiary = markManga,
        onTertiary = onAccent,
        tertiaryContainer = surface2,
        onTertiaryContainer = textPrimary,
        background = bg,
        onBackground = textPrimary,
        surface = bg,
        onSurface = textPrimary,
        surfaceVariant = surface2,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface1,
        surfaceContainer = surface2,
        surfaceContainerHigh = surface3,
        surfaceContainerHighest = surface3,
        surfaceTint = accent,
        inverseSurface = textPrimary,
        inverseOnSurface = bg,
        error = danger,
        onError = onAccent,
        errorContainer = if (isDark) Color(0xFF3A1E19) else Color(0xFFF3DDD8),
        onErrorContainer = if (isDark) Color(0xFFF3D9D3) else Color(0xFF4A1109),
        outline = outlineStrong,
        outlineVariant = hairline,
        scrim = Color.Black
    )
}

/** Material You is opt-in: its roles are folded back into the semantic palette. */
private fun paletteFromScheme(scheme: ColorScheme, marks: AllFunPalette): AllFunPalette {
    val dark = scheme.background.luminance() < 0.5f
    return AllFunPalette(
        isDark = dark,
        bg = scheme.background,
        surface1 = scheme.surfaceContainerLow,
        surface2 = scheme.surfaceContainer,
        surface3 = scheme.surfaceContainerHigh,
        hairline = scheme.outlineVariant,
        outlineStrong = scheme.outline,
        textPrimary = scheme.onSurface,
        textSecondary = scheme.onSurfaceVariant,
        textTertiary = scheme.onSurfaceVariant.copy(alpha = 0.78f),
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        accentContainer = scheme.primaryContainer,
        danger = scheme.error,
        success = marks.success,
        markMovies = marks.markMovies,
        markBooks = marks.markBooks,
        markManga = marks.markManga
    )
}

/** Called only behind the SDK 31 check in [AllFunTheme]. */
@android.annotation.TargetApi(Build.VERSION_CODES.S)
private fun dynamicScheme(context: android.content.Context, dark: Boolean): ColorScheme =
    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

/**
 * A colour that travels to its new value instead of jumping. Switching theme or accent
 * blends across every screen rather than flashing a new palette into place.
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
    val useDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val palette = remember(dark, settings.accent, settings.amoledBlack, useDynamic) {
        val fixed = paletteFor(settings.accent.name, dark, settings.amoledBlack)
        if (!useDynamic) {
            fixed
        } else {
            val dynamic = dynamicScheme(context, dark)
            paletteFromScheme(dynamic, fixed)
        }
    }
    val scheme = palette.toScheme()
    val reduceMotion = settings.reduceMotion || systemReduceMotion()
    val density = LocalDensity.current
    val fontScale = settings.appTextSize.scale
    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalAllFunPalette provides palette,
        LocalAllFunRadii provides radiiFor(settings.cornerStyle),
        LocalDensity provides if (fontScale == 1f) density
        else Density(density.density, density.fontScale * fontScale)
    ) {
        MaterialTheme(
            colorScheme = animatedScheme(scheme),
            typography = AllFunTypography,
            shapes = materialShapesFor(settings.cornerStyle),
            content = content
        )
    }
}
