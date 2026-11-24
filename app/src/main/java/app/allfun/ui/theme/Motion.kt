package app.allfun.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * One place for the app's motion language. Screens use these durations so navigation,
 * sheets and content reveals share the same rhythm instead of each inventing a timing.
 *
 * Everything here is ignored by callers when [LocalReduceMotion] is true.
 */
object Motion {
    /** Press feedback, ripple hand-off, small colour changes. */
    const val FAST = 140

    /** Standard enter/exit for content, cards and sheets. */
    const val MEDIUM = 220

    /** Larger surfaces: screen transitions and the navigation indicator. */
    const val SLOW = 320

    /** Decelerating curve for things that appear. */
    val EaseOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Symmetric curve for things that move from one place to another. */
    val EaseInOut: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST, easing = EaseOut)

    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = EaseOut)

    fun <T> slow(): FiniteAnimationSpec<T> = tween(SLOW, easing = EaseInOut)

    /**
     * Critically damped settle for anything that changes size or position: it keeps the
     * speed of a tween but arrives without the slight stop-start a fixed duration has.
     */
    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    /** Same settle with a little give, for selection changes that should feel physical. */
    fun <T> bounce(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
}
