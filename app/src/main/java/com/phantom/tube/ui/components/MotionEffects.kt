package com.phantom.tube.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

/**
 * Spesifikasi pegas & kurva transisi terpusat (Single Source of Truth) tanpa overshoot/getaran berlebih.
 */
object IosSpringSpecs {
    val SmoothOutEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

    val Bouncy: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.82f,
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
}

object PhantomMotion {
    val SmoothOutEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    val SmoothInEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

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

    val SheetSlideEnter: EnterTransition =
        slideInVertically(
            animationSpec = tween(durationMillis = 230, easing = SmoothOutEasing),
            initialOffsetY = { fullHeight -> fullHeight }
        ) + fadeIn(animationSpec = tween(durationMillis = 170))

    val SheetSlideExit: ExitTransition =
        slideOutVertically(
            animationSpec = tween(durationMillis = 180, easing = SmoothInEasing),
            targetOffsetY = { fullHeight -> fullHeight }
        ) + fadeOut(animationSpec = tween(durationMillis = 140))

    val PopScaleEnter: EnterTransition =
        fadeIn(tween(140)) + scaleIn(
            initialScale = 0.90f,
            animationSpec = IosSpringSpecs.Bouncy
        )

    val PopScaleExit: ExitTransition =
        fadeOut(tween(130)) + scaleOut(targetScale = 0.92f, animationSpec = tween(130))

    fun slideInFromRight(): EnterTransition = ScreenSlideEnter
    fun slideOutToRight(): ExitTransition = ScreenSlideExit
    fun fadeThroughEnter(): EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(durationMillis = 190, easing = SmoothOutEasing))
    fun fadeThroughExit(): ExitTransition =
        fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing))
}

/**
 * Efek tekan interaktif menggunakan MutableInteractionSource.collectIsPressedAsState()
 * sehingga tidak memblokir scroll LazyColumn / parent gesture detector.
 */
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
        animationSpec = tween(durationMillis = 90),
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

/**
 * Animasi masuk item list yang deterministik dan bebas kedip saat di-scroll cepat.
 */
fun Modifier.animateItemEntrance(
    index: Int = 0,
    delayPerItemMs: Long = 20L
): Modifier = composed {
    var isVisible by rememberSaveable(index) { mutableStateOf(false) }

    LaunchedEffect(index) {
        if (!isVisible) {
            isVisible = true
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 180,
            easing = IosSpringSpecs.SmoothOutEasing
        ),
        label = "entrance_alpha"
    )

    val translationY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 16f,
        animationSpec = tween(
            durationMillis = 190,
            easing = IosSpringSpecs.SmoothOutEasing
        ),
        label = "entrance_y"
    )

    this.graphicsLayer {
        this.alpha = alpha
        this.translationY = translationY
    }
}

@Composable
fun Modifier.floatingTransition(
    offsetRange: Float = 6f,
    durationMillis: Int = 2400
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "floating_infinite")
    val dy by infiniteTransition.animateFloat(
        initialValue = -offsetRange,
        targetValue = offsetRange,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating_dy"
    )
    return this.graphicsLayer {
        translationY = dy
    }
}
