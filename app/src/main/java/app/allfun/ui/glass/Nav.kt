package app.allfun.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allfun.ui.theme.LocalReduceMotion

data class GlassTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val activeIcon: ImageVector,
    val section: Section
)

/**
 * Shared hide-on-scroll state. The shell owns the nav; a scrolling screen reports its
 * position here, and the nav slides away without any screen needing a nav parameter.
 */
class GlassNavState {
    var hidden by mutableStateOf(false)
        private set

    private var lastPosition = 0

    fun onScroll(firstVisibleIndex: Int, firstVisibleOffset: Int) {
        val position = firstVisibleIndex * 100_000 + firstVisibleOffset
        val delta = position - lastPosition
        if (delta > 48 && position > 0) hidden = true
        if (delta < -48) hidden = false
        lastPosition = position
    }

    fun show() {
        hidden = false
    }

    fun hide() {
        hidden = true
    }

    fun reset() {
        hidden = false
        lastPosition = 0
    }
}

val LocalGlassNavState = staticCompositionLocalOf { GlassNavState() }

/**
 * The floating nav. One glass capsule, one travelling blob, five equal cells. The active
 * label fades in under its icon; the blob's accent is the destination's own hue.
 */
@Composable
fun GlassNav(
    tabs: List<GlassTab>,
    currentRoute: String?,
    onSelect: (GlassTab) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return
    val colors = AFTheme.colors
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val selected = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val accent = tabs[selected].section.accent

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val cell = maxWidth / tabs.size
        val blobX by animateDpAsState(
            targetValue = cell * selected,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "navBlob"
        )
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            tier = GlassTier.CLEAR,
            shape = RoundedCornerShape(32.dp),
            shadow = true,
            sweep = true
        ) {
            Box(Modifier.fillMaxWidth().height(64.dp)) {
                Box(
                    Modifier
                        .offset(x = blobX + 4.dp, y = 6.dp)
                        .width((cell - 8.dp).coerceAtLeast(40.dp))
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(accent.copy(alpha = 0.14f))
                )
                Row(Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = index == selected
                        val labelAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = androidx.compose.animation.core.tween(if (reduce) 0 else 180),
                            label = "navLabel"
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (!isSelected) {
                                        haptics.tick()
                                        onSelect(tab)
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AFIcon(
                                icon = if (isSelected) tab.activeIcon else tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) accent else colors.textMid,
                                size = 22.dp
                            )
                            Box(Modifier.height(14.dp), contentAlignment = Alignment.Center) {
                                AFText(
                                    text = tab.label,
                                    style = AFTheme.type.caption.copy(fontSize = 10.sp),
                                    color = accent,
                                    maxLines = 1,
                                    modifier = Modifier.graphicsLayer { alpha = labelAlpha }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 96dp fade under the last row so the nav never cuts a poster in half. */
@Composable
fun BottomScrim(modifier: Modifier = Modifier) {
    val colors = AFTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to colors.bg0.copy(alpha = 0f),
                    1f to colors.bg0.copy(alpha = 0.9f)
                )
            )
    )
}

/** Empty box used where a nav-sized spacer is needed. */
@Composable
fun NavSpace(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(96.dp))
}

/** Touch target size helper so no glass control is smaller than the platform minimum. */
val MinTouch = 48.dp

/** Convenience: a square glass button of the standard visual size. */
@Composable
fun GlassSquareButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    GlassSurface(
        modifier = modifier.size(size),
        tier = GlassTier.INSET,
        shape = RoundedCornerShape(size * 0.32f),
        selected = active,
        onClick = onClick,
        onClickLabel = contentDescription
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            AFIcon(
                icon = icon,
                contentDescription = contentDescription,
                tint = if (active) AFTheme.colors.accent else AFTheme.colors.textHi,
                size = size * 0.5f
            )
        }
    }
}
