package com.phantom.tube.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.R
import com.phantom.tube.core.theme.GlassBorderDim
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.YouTubeSurface
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private enum class PlayerSheetPage(val depth: Int) {
    MAIN(0),
    QUALITY(1),
    SPEED(1),
    DOUBLE_TAP_SEEK(1),
    SLEEP_TIMER(1)
}

@Composable
fun PlayerSettingsSheet(
    visible: Boolean,
    isFullscreen: Boolean = false,
    currentSpeed: Float,
    currentQuality: String,
    availableQualities: List<String> = emptyList(),
    doubleTapSeekSeconds: Int = 10,
    isLoopEnabled: Boolean = false,
    isAutoplayNext: Boolean = true,
    isAudioOnly: Boolean = false,
    sleepTimerRemainingSec: Int? = null,
    sleepTimerOption: SleepTimerOption = SleepTimerOption.OFF,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onQualitySelected: (String) -> Unit,
    onDoubleTapSeekSelected: (Int) -> Unit = {},
    onSleepTimerSelected: (SleepTimerOption) -> Unit = {},
    onLoopToggle: (Boolean) -> Unit = {},
    onAutoplayToggle: (Boolean) -> Unit = {},
    onAudioOnlyToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(PlayerSheetPage.MAIN) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val dismissThresholdPx = with(density) { PhantomMotion.SheetDismissThresholdDp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (!visible) {
            currentPage = PlayerSheetPage.MAIN
        } else {
            dragOffsetY.snapTo(0f)
        }
    }

    BackHandler(enabled = visible) {
        if (currentPage != PlayerSheetPage.MAIN) {
            currentPage = PlayerSheetPage.MAIN
        } else {
            onDismiss()
        }
    }

    fun settleOrDismiss(velocityY: Float = 0f) {
        if (dragOffsetY.value > dismissThresholdPx || velocityY > 1100f) {
            onDismiss()
        } else {
            scope.launch {
                dragOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = IosSpringSpecs.Gentle
                )
            }
        }
    }

    val nestedScrollConnection = remember(dismissThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y < 0f && dragOffsetY.value > 0f) {
                    val consumedY = available.y.coerceAtLeast(-dragOffsetY.value)
                    scope.launch {
                        dragOffsetY.snapTo((dragOffsetY.value + consumedY).coerceAtLeast(0f))
                    }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0f) {
                    scope.launch {
                        dragOffsetY.snapTo((dragOffsetY.value + available.y).coerceAtLeast(0f))
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffsetY.value > 0f) {
                    settleOrDismiss(velocityY = available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val handleDragModifier = Modifier.pointerInput(dismissThresholdPx) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                scope.launch {
                    dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f))
                }
            },
            onDragEnd = {
                settleOrDismiss()
            },
            onDragCancel = {
                scope.launch {
                    dragOffsetY.animateTo(0f, IosSpringSpecs.Gentle)
                }
            }
        )
    }

    val enterTransition = if (isFullscreen) {
        fadeIn(tween(180)) + slideInHorizontally(
            initialOffsetX = { it / 2 },
            animationSpec = IosSpringSpecs.GentleOffset
        )
    } else {
        PhantomMotion.SheetSlideEnter
    }

    val exitTransition = if (isFullscreen) {
        fadeOut(tween(150)) + slideOutHorizontally(
            targetOffsetX = { it / 2 },
            animationSpec = IosSpringSpecs.GentleOffset
        )
    } else {
        PhantomMotion.SheetSlideExit
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier.fillMaxSize()
    ) {
        val screenHeightDp = configuration.screenHeightDp.dp
        val screenWidthDp = configuration.screenWidthDp.dp
        val maxPortraitSheetHeight = (screenHeightDp * 0.84f).coerceAtLeast(280.dp)
        val maxLandscapeSheetWidth = (screenWidthDp * 0.54f).coerceIn(280.dp, 420.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = if (isFullscreen) Alignment.CenterEnd else Alignment.BottomCenter
        ) {
            val sheetShape = if (isFullscreen) {
                RoundedCornerShape(22.dp)
            } else {
                RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            }

            Surface(
                modifier = Modifier
                    .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                    .then(
                        if (isFullscreen) {
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .widthIn(min = 280.dp, max = maxLandscapeSheetWidth)
                                .fillMaxHeight()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = 560.dp)
                                .heightIn(max = maxPortraitSheetHeight)
                        }
                    )
                    .clip(sheetShape)
                    .border(0.75.dp, GlassBorderLight, sheetShape)
                    .pointerInput(Unit) {
                        detectTapGestures { /* Consume surface tap */ }
                    }
                    .nestedScroll(nestedScrollConnection),
                color = YouTubeSurface,
                shape = sheetShape
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(handleDragModifier)
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(TextMuted.copy(alpha = 0.45f))
                        )
                    }

                    AnimatedContent(
                        targetState = currentPage,
                        transitionSpec = {
                            if (targetState.depth > initialState.depth) {
                                PhantomMotion.slideInFromRight() togetherWith PhantomMotion.slideOutToRight()
                            } else {
                                PhantomMotion.fadeThroughEnter() togetherWith PhantomMotion.slideOutToRight()
                            }
                        },
                        label = "player_settings_sheet_nav"
                    ) { page ->
                        when (page) {
                            PlayerSheetPage.MAIN -> {
                                PlayerSettingsMainPage(
                                    currentQuality = currentQuality,
                                    availableQualities = availableQualities,
                                    currentSpeed = currentSpeed,
                                    doubleTapSeekSeconds = doubleTapSeekSeconds,
                                    isLoopEnabled = isLoopEnabled,
                                    isAutoplayNext = isAutoplayNext,
                                    isAudioOnly = isAudioOnly,
                                    sleepTimerOption = sleepTimerOption,
                                    sleepTimerRemainingSec = sleepTimerRemainingSec,
                                    headerDragModifier = handleDragModifier,
                                    onOpenQuality = { currentPage = PlayerSheetPage.QUALITY },
                                    onOpenSpeed = { currentPage = PlayerSheetPage.SPEED },
                                    onOpenSeek = { currentPage = PlayerSheetPage.DOUBLE_TAP_SEEK },
                                    onOpenSleepTimer = { currentPage = PlayerSheetPage.SLEEP_TIMER },
                                    onLoopToggle = onLoopToggle,
                                    onAutoplayToggle = onAutoplayToggle,
                                    onAudioOnlyToggle = onAudioOnlyToggle,
                                    onDismiss = onDismiss
                                )
                            }

                            PlayerSheetPage.QUALITY -> {
                                QualitySubPage(
                                    availableQualities = availableQualities,
                                    currentQuality = currentQuality,
                                    onSelect = {
                                        onQualitySelected(it)
                                        onDismiss()
                                    },
                                    onBack = { currentPage = PlayerSheetPage.MAIN }
                                )
                            }

                            PlayerSheetPage.SPEED -> {
                                SpeedSubPage(
                                    currentSpeed = currentSpeed,
                                    onSelect = {
                                        onSpeedSelected(it)
                                    },
                                    onBack = { currentPage = PlayerSheetPage.MAIN }
                                )
                            }

                            PlayerSheetPage.DOUBLE_TAP_SEEK -> {
                                SeekDurationSubPage(
                                    currentSeekSeconds = doubleTapSeekSeconds,
                                    onSelect = {
                                        onDoubleTapSeekSelected(it)
                                        onDismiss()
                                    },
                                    onBack = { currentPage = PlayerSheetPage.MAIN }
                                )
                            }

                            PlayerSheetPage.SLEEP_TIMER -> {
                                SleepTimerSubPage(
                                    selectedOption = sleepTimerOption,
                                    remainingSec = sleepTimerRemainingSec,
                                    onSelect = {
                                        onSleepTimerSelected(it)
                                        onDismiss()
                                    },
                                    onBack = { currentPage = PlayerSheetPage.MAIN }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSettingsMainPage(
    currentQuality: String,
    availableQualities: List<String>,
    currentSpeed: Float,
    doubleTapSeekSeconds: Int,
    isLoopEnabled: Boolean,
    isAutoplayNext: Boolean,
    isAudioOnly: Boolean,
    sleepTimerOption: SleepTimerOption,
    sleepTimerRemainingSec: Int?,
    headerDragModifier: Modifier = Modifier,
    onOpenQuality: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSeek: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onLoopToggle: (Boolean) -> Unit,
    onAutoplayToggle: (Boolean) -> Unit,
    onAudioOnlyToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val qualityList = buildQualityOptionList(availableQualities)
    val qualityLabel = qualityList.firstOrNull { it.code.equals(currentQuality, ignoreCase = true) }?.label
        ?: currentQuality.uppercase()

    val speedLabel = if (kotlin.math.abs(currentSpeed - 1.0f) < 0.01f) {
        stringResource(R.string.settings_speed_normal)
    } else {
        String.format(Locale.US, "%.2fx", currentSpeed)
            .trimEnd('0')
            .trimEnd('.')
            .let { if (!it.endsWith("x")) "${it}x" else it }
    }

    val sleepValueLabel = if (sleepTimerRemainingSec != null && sleepTimerRemainingSec > 0) {
        val mins = sleepTimerRemainingSec / 60
        val secs = sleepTimerRemainingSec % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    } else {
        stringResource(sleepTimerOption.labelRes)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(headerDragModifier)
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_player_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                ),
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = TextPrimary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = GlassBorderDim
        )

        PlayerSheetActionItem(
            icon = Icons.Rounded.Hd,
            label = stringResource(R.string.player_quality),
            value = if (isAudioOnly) stringResource(R.string.settings_audio_only_player_title) else qualityLabel,
            valueAccent = !currentQuality.equals("auto", ignoreCase = true) || isAudioOnly,
            onClick = onOpenQuality
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = GlassBorderDim
        )

        PlayerSheetActionItem(
            icon = Icons.Rounded.Speed,
            label = stringResource(R.string.player_speed),
            value = speedLabel,
            valueAccent = kotlin.math.abs(currentSpeed - 1.0f) >= 0.01f,
            onClick = onOpenSpeed
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = GlassBorderDim
        )

        PlayerSheetActionItem(
            icon = Icons.Rounded.FastForward,
            label = stringResource(R.string.settings_double_tap_seek),
            value = stringResource(R.string.settings_seek_seconds, doubleTapSeekSeconds.toString()),
            valueAccent = doubleTapSeekSeconds != 10,
            onClick = onOpenSeek
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = GlassBorderDim
        )

        PlayerSheetActionItem(
            icon = Icons.Rounded.Bedtime,
            label = stringResource(R.string.settings_sleep_timer),
            value = sleepValueLabel,
            valueAccent = sleepTimerOption != SleepTimerOption.OFF,
            onClick = onOpenSleepTimer
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = GlassBorderDim
        )

        PlayerSheetToggleItem(
            icon = Icons.Rounded.Headphones,
            label = stringResource(R.string.settings_audio_only),
            subtitle = stringResource(R.string.settings_audio_only_desc),
            checked = isAudioOnly,
            onCheckedChange = onAudioOnlyToggle
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = GlassBorderDim
        )

        PlayerSheetToggleItem(
            icon = Icons.Rounded.Repeat,
            label = stringResource(R.string.settings_loop_video),
            subtitle = stringResource(R.string.settings_loop_video_desc),
            checked = isLoopEnabled,
            onCheckedChange = onLoopToggle
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = GlassBorderDim
        )

        PlayerSheetToggleItem(
            icon = Icons.Rounded.PlayCircle,
            label = stringResource(R.string.player_auto_play),
            subtitle = stringResource(R.string.settings_auto_play_next_desc),
            checked = isAutoplayNext,
            onCheckedChange = onAutoplayToggle
        )
    }
}
