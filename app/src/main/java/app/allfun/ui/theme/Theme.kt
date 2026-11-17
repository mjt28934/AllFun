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

val Ink = Color(0xFF0B0B10)
val InkRaised = Color(0xFF14141C)
val InkCard = Color(0xFF1B1B25)
val Violet = Color(0xFF7C5CFF)
val Cyan = Color(0xFF22D3EE)
val Rose = Color(0xFFF472B6)
val Paper = Color(0xFFF4F1EA)

/** True when the user asked for less motion; every animated surface checks this. */
val LocalReduceMotion = staticCompositionLocalOf { false }

private val DarkScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF120E24),
    primaryContainer = Color(0xFF2B2350),
    onPrimaryContainer = Color(0xFFE4DEFF),
    secondary = Cyan,
    onSecondary = Color(0xFF001F24),
    secondaryContainer = Color(0xFF083A42),
    onSecondaryContainer = Color(0xFFC7F5FC),
    tertiary = Rose,
    onTertiary = Color(0xFF2A0719),
    background = Ink,
    onBackground = Color(0xFFEFEEF7),
    surface = Ink,
    onSurface = Color(0xFFEFEEF7),
    surfaceVariant = InkCard,
    onSurfaceVariant = Color(0xFFBDBBCB),
    surfaceContainer = InkRaised,
    surfaceContainerHigh = Color(0xFF21212D),
    surfaceContainerHighest = Color(0xFF2A2A38),
    outline = Color(0xFF6E6E84),
    outlineVariant = Color(0xFF2F2F3E)
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
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun AllFunTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalReduceMotion provides settings.reduceMotion) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
