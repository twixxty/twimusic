package app.vitune.compose.routing

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

// Material 3 Expressive Easing
private val M3EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val M3EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

private const val M3_ENTER_DURATION = 350
private const val M3_EXIT_DURATION = 250

val defaultStacking = ContentTransform(
    initialContentExit = scaleOut(
        targetScale = 0.92f,
        animationSpec = tween(M3_EXIT_DURATION, easing = M3EmphasizedAccelerate)
    ) + fadeOut(
        animationSpec = tween(M3_EXIT_DURATION - 50, easing = M3EmphasizedAccelerate)
    ),
    targetContentEnter = scaleIn(
        initialScale = 0.96f,
        animationSpec = tween(M3_ENTER_DURATION, easing = M3EmphasizedDecelerate)
    ) + fadeIn(
        animationSpec = tween(M3_ENTER_DURATION, easing = M3EmphasizedDecelerate)
    ),
    targetContentZIndex = 1f
)

val defaultUnstacking = ContentTransform(
    initialContentExit = scaleOut(
        targetScale = 1.06f,
        animationSpec = tween(M3_EXIT_DURATION, easing = M3EmphasizedAccelerate)
    ) + fadeOut(
        animationSpec = tween(M3_EXIT_DURATION - 50, easing = M3EmphasizedAccelerate)
    ),
    targetContentEnter = scaleIn(
        initialScale = 0.94f,
        animationSpec = tween(M3_ENTER_DURATION, easing = M3EmphasizedDecelerate)
    ) + fadeIn(
        animationSpec = tween(M3_ENTER_DURATION, easing = M3EmphasizedDecelerate)
    ),
    targetContentZIndex = 0f
)

val defaultStill = ContentTransform(
    initialContentExit = scaleOut(
        targetScale = 0.96f,
        animationSpec = tween(200, easing = M3EmphasizedAccelerate)
    ) + fadeOut(
        animationSpec = tween(150, easing = M3EmphasizedAccelerate)
    ),
    targetContentEnter = scaleIn(
        initialScale = 0.98f,
        animationSpec = tween(280, easing = M3EmphasizedDecelerate)
    ) + fadeIn(
        animationSpec = tween(280, easing = M3EmphasizedDecelerate)
    ),
    targetContentZIndex = 1f
)

val TransitionScope<*>.isStacking: Boolean
    get() = initialState == null && targetState != null

val TransitionScope<*>.isUnstacking: Boolean
    get() = initialState != null && targetState == null

val TransitionScope<*>.isStill: Boolean
    get() = initialState == null && targetState == null

