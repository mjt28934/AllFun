package app.allfun.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The "quiet reading room" palette.
 *
 * These are semantic roles, not shades: a screen asks for `hairline` or `textSecondary` and
 * gets the right value for the current theme. Material roles are derived from them in
 * [Theme.kt] so stock components inherit the same colours.
 */
data class AllFunPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val hairline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val danger: Color,
    val success: Color,
    val markMovies: Color,
    val markBooks: Color,
    val markManga: Color
)

/** A muted, single-hue accent family. Amber is the default; the rest stay in the same key. */
internal data class AccentFamily(
    val accent: Color,
    val onAccent: Color,
    val container: Color
)

internal fun accentFamily(name: String, dark: Boolean): AccentFamily = when (name) {
    "OCEAN" -> if (dark) {
        AccentFamily(Color(0xFF7FB3D9), Color(0xFF0A1620), Color(0xFF1D3040))
    } else {
        AccentFamily(Color(0xFF2E5F80), Color(0xFFFFFFFF), Color(0xFFD6E7F2))
    }
    "EMERALD" -> if (dark) {
        AccentFamily(Color(0xFF8FBE9A), Color(0xFF0D1A11), Color(0xFF1F3326))
    } else {
        AccentFamily(Color(0xFF3F6B4A), Color(0xFFFFFFFF), Color(0xFFD9E8DC))
    }
    "ROSE" -> if (dark) {
        AccentFamily(Color(0xFFE0A0B4), Color(0xFF241016), Color(0xFF3C2029))
    } else {
        AccentFamily(Color(0xFF8C4157), Color(0xFFFFFFFF), Color(0xFFF2DDE3))
    }
    "VIOLET" -> if (dark) {
        AccentFamily(Color(0xFFB49BE8), Color(0xFF170F26), Color(0xFF2E2444))
    } else {
        AccentFamily(Color(0xFF5B4385), Color(0xFFFFFFFF), Color(0xFFE6DDF3))
    }
    else -> if (dark) {
        // The default accent: warm amber, 8.6:1 on the dark background.
        AccentFamily(Color(0xFFE3A04A), Color(0xFF1A1206), Color(0xFF3A2A14))
    } else {
        AccentFamily(Color(0xFF8A5209), Color(0xFFFFF8EC), Color(0xFFF2DEBF))
    }
}

internal fun paletteFor(accent: String, dark: Boolean, amoled: Boolean): AllFunPalette {
    val family = accentFamily(accent, dark)
    return if (dark) {
        AllFunPalette(
            isDark = true,
            bg = if (amoled) Color(0xFF000000) else Color(0xFF0F0E0C),
            surface1 = if (amoled) Color(0xFF0B0A08) else Color(0xFF171511),
            surface2 = if (amoled) Color(0xFF14120F) else Color(0xFF1F1C17),
            surface3 = if (amoled) Color(0xFF1C1915) else Color(0xFF292520),
            hairline = Color(0xFF2E2A24),
            outlineStrong = Color(0xFF433D34),
            textPrimary = Color(0xFFEDE7DB),
            textSecondary = Color(0xFFABA397),
            textTertiary = Color(0xFF918979),
            accent = family.accent,
            onAccent = family.onAccent,
            accentContainer = family.container,
            danger = Color(0xFFE0715F),
            success = Color(0xFF8FB573),
            markMovies = Color(0xFF86A8C4),
            markBooks = Color(0xFFA9B58A),
            markManga = Color(0xFFC99A86)
        )
    } else {
        AllFunPalette(
            isDark = false,
            bg = Color(0xFFF6F2EA),
            surface1 = Color(0xFFFBF8F2),
            surface2 = Color(0xFFEFE9DE),
            surface3 = Color(0xFFFFFFFF),
            hairline = Color(0xFFDDD5C6),
            outlineStrong = Color(0xFFB9AF9C),
            textPrimary = Color(0xFF1E1B16),
            textSecondary = Color(0xFF5E574B),
            textTertiary = Color(0xFF6C6455),
            accent = family.accent,
            onAccent = family.onAccent,
            accentContainer = family.container,
            danger = Color(0xFFB3402F),
            success = Color(0xFF4F6B39),
            markMovies = Color(0xFF456B8A),
            markBooks = Color(0xFF5E6B3F),
            markManga = Color(0xFF8A5B45)
        )
    }
}

/** Fallback used by previews and by any composable read outside [AllFunTheme]. */
val LocalAllFunPalette = staticCompositionLocalOf { paletteFor("AMBER", dark = true, amoled = false) }

/** Shorthand for the semantic palette inside composables. */
object AllFun {
    val colors: AllFunPalette
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalAllFunPalette.current
}
