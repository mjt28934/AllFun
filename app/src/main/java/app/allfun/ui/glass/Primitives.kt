package app.allfun.ui.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.allfun.ui.theme.LocalReduceMotion

/**
 * Text on the glass. One wrapper so the 1dp shadow, tabular numerals and the display face
 * are applied consistently instead of at every call site.
 */
@Composable
fun AFText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AFTheme.type.body,
    color: Color = AFTheme.colors.textHi,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    onGlass: Boolean = false
) {
    AFText(
        text = AnnotatedString(text),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        fontWeight = fontWeight,
        onGlass = onGlass
    )
}

@Composable
fun AFText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = AFTheme.type.body,
    color: Color = AFTheme.colors.textHi,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    onGlass: Boolean = false
) {
    val base = if (onGlass) style.onGlass() else style
    val resolved = base.copy(
        color = color,
        textAlign = textAlign ?: base.textAlign,
        fontWeight = fontWeight ?: base.fontWeight
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = resolved,
        maxLines = maxLines,
        overflow = overflow
    )
}

/** Monoline glyph, 24dp, tinted by the caller. No coloured tiles, ever. */
@Composable
fun AFIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = AFTheme.colors.textHi,
    size: Dp = 24.dp
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint)
    )
}

/**
 * A bare icon action with a 48dp touch target and a 40dp visual. When [active] is set the
 * glyph gets the inset treatment instead of a second sheet of glass.
 */
@Composable
fun AFIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tint: Color = if (active) AFTheme.colors.accent else AFTheme.colors.textHi,
    enabled: Boolean = true,
    visualSize: Dp = 40.dp
) {
    val reduceMotion = LocalReduceMotion.current
    Box(
        modifier = modifier
            .size(48.dp)
            .then(
                if (active) {
                    Modifier
                        .background(
                            AFTheme.colors.accentWash,
                            CircleShape
                        )
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AFIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            size = 22.dp
        )
    }
}

/** A standalone glass circle, used beside the primary action on detail pages. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 48.dp
) {
    GlassSurface(
        modifier = modifier.size(size),
        tier = GlassTier.CLEAR,
        shape = CircleShape,
        selected = active,
        onClick = onClick,
        onClickLabel = contentDescription
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            AFIcon(
                icon = icon,
                contentDescription = contentDescription,
                tint = if (active) AFTheme.colors.accent else AFTheme.colors.textHi,
                size = size * 0.46f
            )
        }
    }
}

/** 1dp hairline, inset past the icon like a list separator should be. */
@Composable
fun AFDivider(modifier: Modifier = Modifier, startInset: Dp = 0.dp, color: Color = AFTheme.colors.hairline) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = startInset)
            .background(color)
    )
}

/** Text-only action with a full 48dp target. The glass equivalent of a text button. */
@Composable
fun AFTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AFTheme.colors.accent,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        AFText(
            text = label,
            style = AFTheme.type.label,
            color = if (enabled) color else color.copy(alpha = 0.4f),
            maxLines = 1
        )
    }
}

/** Overline label that sits on the world above a panel. */
@Composable
fun AFOverline(text: String, modifier: Modifier = Modifier) {
    AFText(
        text = text.uppercase(),
        modifier = modifier,
        style = AFTheme.type.overline,
        color = AFTheme.colors.textLo
    )
}

@Composable
fun AFVerticalGap(height: Dp = 12.dp) = Spacer(Modifier.height(height))
