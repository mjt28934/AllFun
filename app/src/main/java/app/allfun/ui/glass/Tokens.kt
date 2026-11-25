package app.allfun.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allfun.R

/**
 * The one place every liquid-glass value lives. Screens never invent a colour, radius or
 * duration: they read [AFTheme.colors], [AFTheme.type] and the constants below.
 */
object GlassTokens {

    // Warm neutrals. Not grey: a trace of red keeps the world from feeling like a terminal.
    val Bg0 = Color(0xFF0A0B0E)
    val Bg1 = Color(0xFF101217)
    val Bg2 = Color(0xFF171A21)
    val Bg0Light = Color(0xFFF2EFE9)
    val Bg1Light = Color(0xFFFBF9F4)
    val Bg2Light = Color(0xFFE7E3DA)

    val TextHi = Color(0xFFF1EFEA)
    val TextMid = Color(0xFFB0ADA6)
    val TextLo = Color(0xFF7A7873)
    val TextHiLight = Color(0xFF16171B)
    val TextMidLight = Color(0xFF4E4F55)
    val TextLoLight = Color(0xFF7B7B80)

    val Hairline = Color(0x14FFFFFF)
    val HairlineLight = Color(0x1A000000)

    // Section hues. One accent is visible per screen; these never appear as gradients.
    val MoviesAccent = Color(0xFFFF5E8A)
    val BooksAccent = Color(0xFF2CC5C0)
    val MangaAccent = Color(0xFF8B7CFF)
    val LibraryAccent = Color(0xFFE7B36B)
    val SystemAccent = Color(0xFFB9B4AA)

    val Ok = Color(0xFF5BD08A)
    val Slow = Color(0xFFF2B84B)
    val Failed = Color(0xFFFF6B6B)

    // Glass body tints, as alpha over whatever sits behind the surface. Lower than a
    // classic frosted card: Apple's glass stays readable because the edge bends light, not
    // because the fill hides the world.
    const val CLEAR_TINT = 0.18f
    const val CLEAR_WHITE = 0.04f
    const val FROST_TINT = 0.48f
    const val FROST_WHITE = 0.05f
    const val AMOLED_TINT = 0.55f
    const val LIGHT_TINT = 0.36f

    // Spacing scale (4dp base).
    val gutter = 20.dp
    val gap = 12.dp
    val sectionGap = 32.dp
    val panelPadding = 16.dp

    // Concentric radii: inner = outer - padding.
    val rPanel = 26.dp
    val rSheet = 34.dp
    val rMenu = 24.dp
    val rInput = 20.dp
    val rPoster = 10.dp
    val rCapsule = 100.dp
    val rBadge = 10.dp

    // Motion. Glass only springs; plain fades use the short cubic curve.
    const val SPRING_DAMPING = 0.8f
    const val SPRING_RESPONSE = 0.35f
    const val FADE_FAST = 120
    const val FADE = 200
    const val FADE_SLOW = 320
    const val STAGGER_MS = 30
    const val STAGGER_MAX = 12
}

/** Which section's hue is allowed to be visible right now. */
enum class Section(val accent: Color) {
    MOVIES(GlassTokens.MoviesAccent),
    BOOKS(GlassTokens.BooksAccent),
    MANGA(GlassTokens.MangaAccent),
    LIBRARY(GlassTokens.LibraryAccent),
    SYSTEM(GlassTokens.SystemAccent)
}

/**
 * A resolved palette for one frame. Light mode inverts the neutral ladder and the glass
 * body, but keeps the section accent so a screen is still recognisable at a glance.
 */
data class AFColors(
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val textHi: Color,
    val textMid: Color,
    val textLo: Color,
    val hairline: Color,
    val accent: Color,
    val accentWash: Color,
    val accentContainer: Color,
    val onAccent: Color,
    val ok: Color = GlassTokens.Ok,
    val slow: Color = GlassTokens.Slow,
    val failed: Color = GlassTokens.Failed,
    val clearTint: Float = GlassTokens.CLEAR_TINT,
    val clearWhite: Float = GlassTokens.CLEAR_WHITE,
    val frostTint: Float = GlassTokens.FROST_TINT,
    val isLight: Boolean = false
) {
    /** The darkest or lightest tone a shadow can use on this surface. */
    val shadow: Color get() = if (isLight) Color(0x33000000) else Color(0x4D000000)
}

@OptIn(ExperimentalTextApi::class)
private fun interTight(vararg settings: FontVariation.Setting) = Font(
    R.font.inter_tight,
    weight = FontWeight.Normal,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(*settings)
)

@OptIn(ExperimentalTextApi::class)
private fun fraunces(vararg settings: FontVariation.Setting) = Font(
    R.font.fraunces,
    weight = FontWeight.Normal,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(*settings)
)

/** UI face: Inter Tight, tabular numerals everywhere a number is compared. */
@OptIn(ExperimentalTextApi::class)
val InterTight = FontFamily(
    interTight(FontVariation.weight(400)),
    interTight(FontVariation.weight(500)),
    interTight(FontVariation.weight(600)),
    interTight(FontVariation.weight(700))
)

/** Display face: Fraunces at a high optical size, soft and slightly wonky. */
@OptIn(ExperimentalTextApi::class)
val Fraunces = FontFamily(
    fraunces(FontVariation.weight(400), FontVariation.opticalSizing(72.sp)),
    fraunces(FontVariation.weight(500), FontVariation.opticalSizing(72.sp)),
    fraunces(FontVariation.weight(600), FontVariation.opticalSizing(60.sp))
)

data class AFType(
    val display: TextStyle,
    val displaySmall: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val numeric: TextStyle
)

fun afType(serifDisplay: Boolean = true): AFType {
    val display = if (serifDisplay) Fraunces else InterTight
    val tabular = "tnum"
    return AFType(
        display = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.W500,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.8).sp
        ),
        displaySmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.W500,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.4).sp
        ),
        title = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.W500,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.3).sp
        ),
        heading = TextStyle(
            fontFamily = InterTight,
            fontWeight = FontWeight.W600,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.1).sp
        ),
        body = TextStyle(
            fontFamily = InterTight,
            fontWeight = FontWeight.W400,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        ),
        bodySmall = TextStyle(
            fontFamily = InterTight,
            fontWeight = FontWeight.W400,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.05.sp
        ),
        label = TextStyle(
            fontFamily = InterTight,
            fontWeight = FontWeight.W500,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.05.sp,
            fontFeatureSettings = tabular
        ),
        caption = TextStyle(
            fontFamily = InterTight,
            fontWeight = FontWeight.W400,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.1.sp
        ),
        overline = TextStyle(
            fontFamily = InterTight,
            fontWeight = FontWeight.W600,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.2.sp
        ),
        numeric = TextStyle(
            fontFamily = InterTight,
            fontWeight = FontWeight.W500,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = tabular
        )
    )
}
