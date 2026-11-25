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

    /** Hero transitions and shared-axis movement between major surfaces. */
    const val EMPHASIZED = 440

    /** Decelerating curve for things that appear. */
    val EaseOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Symmetric curve for things that move from one place to another. */
    val EaseInOut: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /**
     * The long, soft tail Material uses for hero surfaces. It starts quickly and keeps
     * settling, which reads as weight rather than as a fixed-length slide.
     */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST, easing = EaseOut)

    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = EaseOut)

    fun <T> slow(): FiniteAnimationSpec<T> = tween(SLOW, easing = EaseInOut)

    fun <T> emphasized(): FiniteAnimationSpec<T> = tween(EMPHASIZED, easing = Emphasized)

    /**
     * Critically damped settle for anything that changes size or position: it keeps the
     * speed of a tween but arrives without the slight stop-start a fixed duration has.
     */
    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    /** Same settle with a little give, for selection changes that should feel physical. */
    fun <T> bounce(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)

    /** A tighter, faster settle for things that follow a finger, such as a sheet handle. */
    fun <T> snappy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)

    /**
     * Delay used to cascade a list into place. Capped so a long page never waits seconds
     * before its last row appears; the first rows carry the effect, the rest just arrive.
     */
    fun stagger(index: Int, step: Long = 26, cap: Long = 260): Long =
        (index.coerceAtLeast(0) * step).coerceAtMost(cap)
}
