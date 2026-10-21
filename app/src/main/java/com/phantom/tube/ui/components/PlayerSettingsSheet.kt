package com.phantom.tube.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.R
import com.phantom.tube.core.theme.GlassBorderDim
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeSurface
import java.util.Locale

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

    LaunchedEffect(visible) {
        if (!visible) {
            currentPage = PlayerSheetPage.MAIN
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = IosSpringSpecs.GentleOffset
        ),
        exit = fadeOut(tween(150)) + slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = IosSpringSpecs.GentleOffset
        ),
        modifier = modifier.fillMaxSize()
    ) {
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
                RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            } else {
                RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            }

            Surface(
                modifier = Modifier
                    .then(
                        if (isFullscreen) {
                            Modifier
                                .widthIn(max = 420.dp)
                                .fillMaxWidth(0.52f)
                                .fillMaxHeight()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 540.dp)
                        }
                    )
                    .clip(sheetShape)
                    .border(0.75.dp, GlassBorderLight, sheetShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                color = YouTubeSurface,
                shape = sheetShape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
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
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_player_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = TextPrimary
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(GlassSurface)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        HorizontalDivider(color = GlassBorderDim)

        PlayerSheetActionItem(
            icon = Icons.Rounded.Hd,
            label = stringResource(R.string.player_quality),
            value = if (isAudioOnly) stringResource(R.string.settings_audio_only_player_title) else qualityLabel,
            valueAccent = !currentQuality.equals("auto", ignoreCase = true) || isAudioOnly,
            onClick = onOpenQuality
        )

        PlayerSheetActionItem(
            icon = Icons.Rounded.Speed,
            label = stringResource(R.string.player_speed),
            value = speedLabel,
            valueAccent = kotlin.math.abs(currentSpeed - 1.0f) >= 0.01f,
            onClick = onOpenSpeed
        )

        PlayerSheetActionItem(
            icon = Icons.Rounded.FastForward,
            label = stringResource(R.string.settings_double_tap_seek),
            value = stringResource(R.string.settings_seek_seconds, doubleTapSeekSeconds.toString()),
            valueAccent = doubleTapSeekSeconds != 10,
            onClick = onOpenSeek
        )

        PlayerSheetActionItem(
            icon = Icons.Rounded.Bedtime,
            label = stringResource(R.string.settings_sleep_timer),
            value = sleepValueLabel,
            valueAccent = sleepTimerOption != SleepTimerOption.OFF,
            onClick = onOpenSleepTimer
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = GlassBorderDim
        )

        PlayerSheetToggleItem(
            icon = Icons.Rounded.Headphones,
            label = stringResource(R.string.settings_audio_only),
            subtitle = stringResource(R.string.settings_audio_only_desc),
            checked = isAudioOnly,
            onCheckedChange = onAudioOnlyToggle
        )

        PlayerSheetToggleItem(
            icon = Icons.Rounded.Repeat,
            label = stringResource(R.string.settings_loop_video),
            subtitle = stringResource(R.string.settings_loop_video_desc),
            checked = isLoopEnabled,
            onCheckedChange = onLoopToggle
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
