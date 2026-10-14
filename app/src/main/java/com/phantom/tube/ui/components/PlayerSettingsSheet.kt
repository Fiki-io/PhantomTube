package com.phantom.tube.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import kotlin.math.roundToInt

enum class SettingsSheetPage {
    MAIN,
    QUALITY,
    DOUBLE_TAP_SEEK,
    SPEED,
    SLEEP_TIMER
}

@Composable
fun SleepTimerOption.getLocalizedLabel(): String = when (this) {
    SleepTimerOption.OFF -> stringResource(R.string.settings_sleep_timer_off)
    SleepTimerOption.MINUTES_15 -> stringResource(R.string.settings_sleep_timer_15m)
    SleepTimerOption.MINUTES_30 -> stringResource(R.string.settings_sleep_timer_30m)
    SleepTimerOption.MINUTES_45 -> stringResource(R.string.settings_sleep_timer_45m)
    SleepTimerOption.MINUTES_60 -> stringResource(R.string.settings_sleep_timer_60m)
    SleepTimerOption.END_OF_VIDEO -> stringResource(R.string.settings_sleep_timer_end)
}

@Composable
fun getLocalizedQualityLabel(code: String): String = when (code) {
    "auto" -> stringResource(R.string.settings_quality_auto)
    "highres" -> stringResource(R.string.settings_quality_highest) + " (4K / 2K)"
    "tiny" -> stringResource(R.string.settings_quality_saver)
    "hd2160" -> "2160p (4K UHD)"
    "hd1440" -> "1440p (QHD)"
    "hd1080" -> "1080p (Full HD)"
    "hd720" -> "720p (HD)"
    "large" -> "480p"
    "medium" -> "360p"
    "small" -> "240p"
    else -> code
}

data class VideoQualityOption(
    val code: String,
    val label: String
)

val ALL_STANDARD_QUALITIES = listOf(
    VideoQualityOption("auto", "Otomatis (Disarankan)"),
    VideoQualityOption("highres", "Kualitas Tertinggi (4K / 2K)"),
    VideoQualityOption("hd2160", "2160p (4K UHD)"),
    VideoQualityOption("hd1440", "1440p (QHD)"),
    VideoQualityOption("hd1080", "1080p (Full HD)"),
    VideoQualityOption("hd720", "720p (HD)"),
    VideoQualityOption("large", "480p"),
    VideoQualityOption("medium", "360p"),
    VideoQualityOption("small", "240p"),
    VideoQualityOption("tiny", "144p (Hemat Kuota)")
)

enum class SleepTimerOption(val label: String, val seconds: Int?) {
    OFF("Nonaktif", null),
    MINUTES_15("15 Menit", 15 * 60),
    MINUTES_30("30 Menit", 30 * 60),
    MINUTES_45("45 Menit", 45 * 60),
    MINUTES_60("60 Menit", 60 * 60),
    END_OF_VIDEO("Di akhir video", -1)
}

val AVAILABLE_SEEK_DURATIONS = listOf(5, 10, 15, 20, 30)

val AVAILABLE_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

/**
 * Standard YouTube Player Settings Bottom Sheet.
 * Clean, flat list UI without bulky card boxes, with transparent backdrop so the video
 * continues playing unobstructed at the top of the screen.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PlayerSettingsSheet(
    visible: Boolean,
    isFullscreen: Boolean,
    currentSpeed: Float,
    currentQuality: String = "auto",
    availableQualities: List<String> = emptyList(),
    doubleTapSeekSeconds: Int,
    isLoopEnabled: Boolean,
    isAutoplayNext: Boolean,
    isAudioOnly: Boolean,
    sleepTimerRemainingSec: Int?,
    sleepTimerOption: SleepTimerOption,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onQualitySelected: (String) -> Unit = {},
    onDoubleTapSeekSelected: (Int) -> Unit,
    onSleepTimerSelected: (SleepTimerOption) -> Unit,
    onLoopToggle: (Boolean) -> Unit,
    onAutoplayToggle: (Boolean) -> Unit,
    onAudioOnlyToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(SettingsSheetPage.MAIN) }
    var sheetOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = sheetOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "settings_sheet_offset"
    )

    LaunchedEffect(visible) {
        if (visible) {
            currentPage = SettingsSheetPage.MAIN
            sheetOffsetY = 0f
        }
    }

    val handleDragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                sheetOffsetY = (sheetOffsetY + dragAmount).coerceAtLeast(0f)
            },
            onDragEnd = {
                if (sheetOffsetY > 140f) {
                    onDismiss()
                } else {
                    sheetOffsetY = 0f
                }
            },
            onDragCancel = {
                sheetOffsetY = 0f
            }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(animationSpec = tween(100)) + slideOutVertically(animationSpec = tween(150, easing = FastOutLinearInEasing)) { it },
        modifier = modifier.fillMaxSize()
    ) {
        // Scrim background: tap empty area to dismiss. Transparent so top video is unobstructed!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            val sheetModifier = if (isFullscreen) {
                Modifier
                    .fillMaxHeight(0.88f)
                    .width(380.dp)
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.70f)
                    .align(Alignment.BottomCenter)
            }

            val sheetShape = if (isFullscreen) RoundedCornerShape(16.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            Box(
                modifier = sheetModifier
                    .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                    .clip(sheetShape)
                    .background(Color(0xFF212121))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!isFullscreen) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(handleDragModifier)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 4.dp)
                                    .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = currentPage,
                        transitionSpec = {
                            if (targetState != SettingsSheetPage.MAIN) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { -it } + fadeOut()
                                )
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { it } + fadeOut()
                                )
                            }
                        },
                        label = "settings_page_transition"
                    ) { page ->
                        when (page) {
                            SettingsSheetPage.MAIN -> {
                                MainSettingsContent(
                                    currentSpeed = currentSpeed,
                                    currentQuality = currentQuality,
                                    doubleTapSeekSeconds = doubleTapSeekSeconds,
                                    isLoopEnabled = isLoopEnabled,
                                    isAutoplayNext = isAutoplayNext,
                                    isAudioOnly = isAudioOnly,
                                    sleepTimerRemainingSec = sleepTimerRemainingSec,
                                    sleepTimerOption = sleepTimerOption,
                                    onNavigate = { currentPage = it },
                                    onLoopToggle = onLoopToggle,
                                    onAutoplayToggle = onAutoplayToggle,
                                    onAudioOnlyToggle = onAudioOnlyToggle,
                                    onClose = onDismiss
                                )
                            }
                            SettingsSheetPage.QUALITY -> {
                                QualitySettingsContent(
                                    currentQuality = currentQuality,
                                    availableQualities = availableQualities,
                                    onSelectQuality = {
                                        onQualitySelected(it)
                                        currentPage = SettingsSheetPage.MAIN
                                    },
                                    onBack = { currentPage = SettingsSheetPage.MAIN }
                                )
                            }
                            SettingsSheetPage.DOUBLE_TAP_SEEK -> {
                                DoubleTapSeekSettingsContent(
                                    currentSeconds = doubleTapSeekSeconds,
                                    onSelectSeconds = {
                                        onDoubleTapSeekSelected(it)
                                        currentPage = SettingsSheetPage.MAIN
                                    },
                                    onBack = { currentPage = SettingsSheetPage.MAIN }
                                )
                            }
                            SettingsSheetPage.SPEED -> {
                                SpeedSettingsContent(
                                    currentSpeed = currentSpeed,
                                    onSelectSpeed = {
                                        onSpeedSelected(it)
                                        currentPage = SettingsSheetPage.MAIN
                                    },
                                    onBack = { currentPage = SettingsSheetPage.MAIN }
                                )
                            }
                            SettingsSheetPage.SLEEP_TIMER -> {
                                SleepTimerSettingsContent(
                                    currentOption = sleepTimerOption,
                                    remainingSec = sleepTimerRemainingSec,
                                    onSelectOption = {
                                        onSleepTimerSelected(it)
                                        currentPage = SettingsSheetPage.MAIN
                                    },
                                    onBack = { currentPage = SettingsSheetPage.MAIN }
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
private fun MainSettingsContent(
    currentSpeed: Float,
    currentQuality: String,
    doubleTapSeekSeconds: Int,
    isLoopEnabled: Boolean,
    isAutoplayNext: Boolean,
    isAudioOnly: Boolean,
    sleepTimerRemainingSec: Int?,
    sleepTimerOption: SleepTimerOption,
    onNavigate: (SettingsSheetPage) -> Unit,
    onLoopToggle: (Boolean) -> Unit,
    onAutoplayToggle: (Boolean) -> Unit,
    onAudioOnlyToggle: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_player_title),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(
            color = Color(0xFF2E2E2E),
            thickness = 1.dp,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                val qualitySubtitle = getLocalizedQualityLabel(currentQuality)
                SettingsNavigationRow(
                    icon = Icons.Default.HighQuality,
                    title = stringResource(R.string.player_quality),
                    subtitle = qualitySubtitle,
                    onClick = { onNavigate(SettingsSheetPage.QUALITY) }
                )
            }

            item {
                val speedLabel = if (currentSpeed == 1.0f) stringResource(R.string.settings_speed_normal) else "${currentSpeed}x"
                SettingsNavigationRow(
                    icon = Icons.Default.Speed,
                    title = stringResource(R.string.player_speed),
                    subtitle = speedLabel,
                    onClick = { onNavigate(SettingsSheetPage.SPEED) }
                )
            }

            item {
                val sleepSubtitle = if (sleepTimerRemainingSec != null && sleepTimerRemainingSec > 0) {
                    "${stringResource(R.string.settings_sleep_timer_active)} (${formatSeconds(sleepTimerRemainingSec.toLong())})"
                } else {
                    sleepTimerOption.getLocalizedLabel()
                }
                SettingsNavigationRow(
                    icon = Icons.Default.Bedtime,
                    title = stringResource(R.string.settings_sleep_timer),
                    subtitle = sleepSubtitle,
                    onClick = { onNavigate(SettingsSheetPage.SLEEP_TIMER) }
                )
            }

            item {
                SettingsNavigationRow(
                    icon = Icons.Default.FastForward,
                    title = stringResource(R.string.settings_double_tap_seek),
                    subtitle = stringResource(R.string.settings_seek_seconds, doubleTapSeekSeconds),
                    onClick = { onNavigate(SettingsSheetPage.DOUBLE_TAP_SEEK) }
                )
            }

            item {
                HorizontalDivider(
                    color = Color(0xFF2E2E2E),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsSwitchRow(
                    icon = Icons.Default.Repeat,
                    title = stringResource(R.string.settings_loop_video),
                    subtitle = stringResource(R.string.settings_loop_video_desc),
                    checked = isLoopEnabled,
                    onCheckedChange = onLoopToggle
                )
            }

            item {
                SettingsSwitchRow(
                    icon = Icons.Default.PlayCircle,
                    title = stringResource(R.string.player_auto_play),
                    subtitle = stringResource(R.string.settings_auto_play_next_desc),
                    checked = isAutoplayNext,
                    onCheckedChange = onAutoplayToggle
                )
            }

            item {
                SettingsSwitchRow(
                    icon = Icons.Default.Headphones,
                    title = stringResource(R.string.settings_audio_only_player_title),
                    subtitle = stringResource(R.string.settings_audio_only_player_desc),
                    checked = isAudioOnly,
                    onCheckedChange = onAudioOnlyToggle
                )
            }
        }
    }
}

@Composable
private fun DoubleTapSeekSettingsContent(
    currentSeconds: Int,
    onSelectSeconds: (Int) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_double_tap_seek),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(
            color = Color(0xFF2E2E2E),
            thickness = 1.dp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(AVAILABLE_SEEK_DURATIONS) { seconds ->
                val isSelected = seconds == currentSeconds
                val label = if (seconds == 10) stringResource(R.string.settings_seek_seconds_default, seconds) else stringResource(R.string.settings_seek_seconds, seconds)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSeconds(seconds) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) YouTubeRed else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.player_selected),
                            tint = YouTubeRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedSettingsContent(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.player_speed),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(
            color = Color(0xFF2E2E2E),
            thickness = 1.dp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(AVAILABLE_SPEEDS) { speed ->
                val isSelected = (speed == currentSpeed)
                val label = if (speed == 1.0f) stringResource(R.string.settings_speed_normal) else "${speed}x"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSpeed(speed) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) YouTubeRed else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.player_selected),
                            tint = YouTubeRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerSettingsContent(
    currentOption: SleepTimerOption,
    remainingSec: Int?,
    onSelectOption: (SleepTimerOption) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_sleep_timer),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(
            color = Color(0xFF2E2E2E),
            thickness = 1.dp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (remainingSec != null && remainingSec > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2C2C))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_sleep_timer_active),
                            color = YouTubeRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_sleep_timer_stopping_in, formatSeconds(remainingSec.toLong())),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { onSelectOption(SleepTimerOption.OFF) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.settings_sleep_timer_cancel),
                            tint = YouTubeRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(SleepTimerOption.values()) { option ->
                val isSelected = option == currentOption
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectOption(option) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.getLocalizedLabel(),
                        color = if (isSelected) YouTubeRed else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.player_selected),
                            tint = YouTubeRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = YouTubeRed,
                uncheckedThumbColor = Color(0xFF9E9E9E),
                uncheckedTrackColor = Color(0xFF383838),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun QualitySettingsContent(
    currentQuality: String,
    availableQualities: List<String>,
    onSelectQuality: (String) -> Unit,
    onBack: () -> Unit
) {
    val options = remember(availableQualities) {
        if (availableQualities.isNotEmpty()) {
            val list = mutableListOf(VideoQualityOption("auto", "Otomatis (Disarankan)"))
            val availableSet = availableQualities.toSet()
            ALL_STANDARD_QUALITIES.filter { it.code != "auto" && availableSet.contains(it.code) }.forEach {
                list.add(it)
            }
            if (list.size == 1) {
                ALL_STANDARD_QUALITIES
            } else {
                list
            }
        } else {
            ALL_STANDARD_QUALITIES
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.player_quality),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(
            color = Color(0xFF2E2E2E),
            thickness = 1.dp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(options) { opt ->
                val isSelected = (opt.code == currentQuality)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectQuality(opt.code) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getLocalizedQualityLabel(opt.code),
                        color = if (isSelected) YouTubeRed else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.player_selected),
                            tint = YouTubeRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatSeconds(totalSec: Long): String {
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
