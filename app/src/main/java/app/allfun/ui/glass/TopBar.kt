package app.allfun.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.allfun.ui.glass.AFIcons.Back

/** One floating capsule of actions, pinned under the status bar. */
@Composable
fun GlassTopCapsule(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    GlassSurface(
        modifier = modifier.height(52.dp),
        tier = GlassTier.CLEAR,
        shape = RoundedCornerShape(26.dp),
        shadow = true
    ) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (onBack != null) {
                AFIconButton(
                    icon = Back,
                    contentDescription = "Back",
                    onClick = onBack
                )
            }
            actions()
        }
    }
}

/** Big display title on the world, with the count line beneath it. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    overline: String? = null
) {
    Column(modifier) {
        if (overline != null) {
            AFText(
                text = overline.uppercase(),
                style = AFTheme.type.overline,
                color = AFTheme.colors.accent
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        }
        AFText(
            text = title,
            style = AFTheme.type.display,
            color = AFTheme.colors.textHi,
            maxLines = 2,
            onGlass = true
        )
        if (subtitle != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            AFText(
                text = subtitle,
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo,
                maxLines = 1
            )
        }
    }
}

/** Fades the world out under a pinned bar so scrolling content never collides with it. */
@Composable
fun TopScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(108.dp)
            .padding(top = 0.dp)
            .then(
                Modifier.background(
                    Brush.verticalGradient(
                        0f to AFTheme.colors.bg0.copy(alpha = 0.92f),
                        0.55f to AFTheme.colors.bg0.copy(alpha = 0.55f),
                        1f to Color.Transparent
                    )
                )
            )
    )
}

/** Keeps a pinned top bar clear of the status bar. */
@Composable
fun Modifier.glassTopInset(): Modifier = this.statusBarsPadding()
