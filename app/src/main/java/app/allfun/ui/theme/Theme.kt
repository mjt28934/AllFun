package app.allfun.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        dark -> DarkScheme
        else -> LightScheme
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
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
