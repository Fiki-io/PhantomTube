package com.phantom.tube.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

object IosSpringSpecs {
    val SmoothOutEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

    val Bouncy: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.80f,
        stiffness = Spring.StiffnessMedium
    )

    val Gentle: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val GentleOffset: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )

    val DockMorph: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.78f,
        stiffness = 430f
    )

    val DockMorphDp: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = 0.78f,
        stiffness = 430f,
        visibilityThreshold = Dp.VisibilityThreshold
    )

    val DockPillGlide: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 410f
    )

    val DockPillFollow: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.88f,
        stiffness = 1600f
    )

    val ColorFade: FiniteAnimationSpec<Color> = spring(
        dampingRatio = 0.86f,
        stiffness = 460f
    )

    val TopBarSize: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 440f,
        visibilityThreshold = IntSize.VisibilityThreshold
    )

    val TopBarDp: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 440f,
        visibilityThreshold = Dp.VisibilityThreshold
    )

    val SheetSpringOffset: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.84f,
        stiffness = 380f,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )

    val SheetSpringFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 390f
    )
}

object PhantomMotion {
    val SmoothOutEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    val SmoothInEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
    val SheetDismissThresholdDp = 96.dp
    const val DockScrollThresholdPx = 22f
    const val ImageFadeInDurationMs = 180
    const val PressAlphaDurationMs = 90

    val ScreenSlideEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = tween(durationMillis = 240, easing = SmoothOutEasing),
            initialOffsetX = { fullWidth -> fullWidth / 4 }
        ) + fadeIn(animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing))

    val ScreenSlideExit: ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 190, easing = SmoothInEasing),
            targetOffsetX = { fullWidth -> fullWidth / 4 }
        ) + fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing))

    val OverlayScreenEnter: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = IosSpringSpecs.GentleOffset
        ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = SmoothOutEasing))

    val OverlayScreenExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = IosSpringSpecs.GentleOffset
        ) + fadeOut(animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing))

    val DockBarEnter: EnterTransition =
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = IosSpringSpecs.GentleOffset
        ) + scaleIn(
            initialScale = 0.92f,
            animationSpec = IosSpringSpecs.DockMorph
        ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = SmoothOutEasing))

    val DockBarExit: ExitTransition =
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = IosSpringSpecs.GentleOffset
        ) + scaleOut(
            targetScale = 0.92f,
            animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing))

    val MiniPlayerEnter: EnterTransition =
        slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = IosSpringSpecs.GentleOffset
        ) + scaleIn(
            initialScale = 0.95f,
            animationSpec = IosSpringSpecs.Bouncy
        ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = SmoothOutEasing))

    val MiniPlayerExit: ExitTransition =
        slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = IosSpringSpecs.GentleOffset
        ) + scaleOut(
            targetScale = 0.95f,
            animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing))

    val TopBarEnter: EnterTransition =
        expandVertically(
            animationSpec = IosSpringSpecs.TopBarSize
        ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing))

    val TopBarExit: ExitTransition =
        shrinkVertically(
            animationSpec = IosSpringSpecs.TopBarSize
        ) + fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing))

    val SheetSlideEnter: EnterTransition =
        slideInVertically(
            animationSpec = IosSpringSpecs.SheetSpringOffset,
            initialOffsetY = { fullHeight -> fullHeight }
        ) + fadeIn(animationSpec = tween(durationMillis = 180, easing = SmoothOutEasing))

    val SheetSlideExit: ExitTransition =
        slideOutVertically(
            animationSpec = IosSpringSpecs.GentleOffset,
            targetOffsetY = { fullHeight -> fullHeight }
        ) + fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing))

    val SheetSideEnter: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = IosSpringSpecs.SheetSpringOffset
        ) + fadeIn(animationSpec = tween(durationMillis = 180, easing = SmoothOutEasing))

    val SheetSideExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = IosSpringSpecs.GentleOffset
        ) + fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing))

    val SheetScrimEnter: EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = 200, easing = SmoothOutEasing))

    val SheetScrimExit: ExitTransition =
        fadeOut(animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing))

    val PopScaleEnter: EnterTransition =
        fadeIn(tween(140)) + scaleIn(
            initialScale = 0.90f,
            animationSpec = IosSpringSpecs.Bouncy
        )

    val PopScaleExit: ExitTransition =
        fadeOut(tween(130)) + scaleOut(targetScale = 0.92f, animationSpec = tween(130))

    val IconSwapTransition: ContentTransform =
        (scaleIn(initialScale = 0.72f, animationSpec = IosSpringSpecs.Bouncy) +
            fadeIn(animationSpec = tween(durationMillis = 140, easing = SmoothOutEasing))) togetherWith
            (scaleOut(targetScale = 0.72f, animationSpec = tween(durationMillis = 110, easing = FastOutLinearInEasing)) +
                fadeOut(animationSpec = tween(durationMillis = 100, easing = FastOutLinearInEasing)))

    val ContentStateTransition: ContentTransform =
        (fadeIn(animationSpec = tween(durationMillis = 200, easing = SmoothOutEasing)) +
            scaleIn(initialScale = 0.985f, animationSpec = IosSpringSpecs.Gentle)) togetherWith
            fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing))

    fun slideInFromRight(): EnterTransition = ScreenSlideEnter
    fun slideOutToRight(): ExitTransition = ScreenSlideExit
    fun fadeThroughEnter(): EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(durationMillis = 190, easing = SmoothOutEasing))
    fun fadeThroughExit(): ExitTransition =
        fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing))
}

fun Modifier.iosBounceClick(
    scaleDown: Float = 0.96f,
    alphaDown: Float = 0.90f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = IosSpringSpecs.Bouncy,
        label = "bounce_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) alphaDown else 1f,
        animationSpec = tween(durationMillis = PhantomMotion.PressAlphaDurationMs),
        label = "bounce_alpha"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

