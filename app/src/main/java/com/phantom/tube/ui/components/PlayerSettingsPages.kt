package com.phantom.tube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.R
import com.phantom.tube.core.theme.GlassBorderDim
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.NeonCyan
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurfaceLight
import java.util.Locale
import kotlin.math.roundToInt

enum class SleepTimerOption(val labelRes: Int, val seconds: Int?) {
    OFF(R.string.settings_sleep_timer_off, null),
    MIN_15(R.string.settings_sleep_timer_15m, 15 * 60),
    MIN_30(R.string.settings_sleep_timer_30m, 30 * 60),
    MIN_45(R.string.settings_sleep_timer_45m, 45 * 60),
    MIN_60(R.string.settings_sleep_timer_60m, 60 * 60),
    END_OF_VIDEO(R.string.settings_sleep_timer_end, -1)
}

data class VideoQualityOption(
    val code: String,
    val label: String,
    val badge: String? = null
)

val ALL_STANDARD_QUALITIES = listOf(
    VideoQualityOption("auto", "Auto", "Adaptive"),
    VideoQualityOption("highres", "Highest", "Max"),
    VideoQualityOption("hd2160", "2160p", "4K UHD"),
    VideoQualityOption("hd1440", "1440p", "QHD"),
    VideoQualityOption("hd1080", "1080p", "Full HD"),
    VideoQualityOption("hd720", "720p", "HD"),
    VideoQualityOption("large", "480p", null),
    VideoQualityOption("medium", "360p", null),
    VideoQualityOption("small", "240p", null),
    VideoQualityOption("tiny", "144p", "Saver")
)

@Composable
fun getLocalizedQualityLabel(code: String): String {
    return when (code.lowercase()) {
        "auto" -> stringResource(R.string.settings_quality_auto)
        "highres" -> stringResource(R.string.settings_quality_highest)
        "tiny" -> stringResource(R.string.settings_quality_saver)
        else -> ALL_STANDARD_QUALITIES.firstOrNull { it.code.equals(code, ignoreCase = true) }?.label
            ?: code.uppercase()
    }
}

@Composable
internal fun buildQualityOptionList(availableQualities: List<String>): List<VideoQualityOption> {
    val autoLabel = stringResource(R.string.settings_quality_auto)
    val highestLabel = stringResource(R.string.settings_quality_highest)
    val saverLabel = stringResource(R.string.settings_quality_saver)

    val allPresets = listOf(
        VideoQualityOption("auto", autoLabel, "Adaptive"),
        VideoQualityOption("highres", highestLabel, "Max"),
        VideoQualityOption("hd2160", "2160p", "4K UHD"),
        VideoQualityOption("hd1440", "1440p", "QHD"),
        VideoQualityOption("hd1080", "1080p", "Full HD"),
        VideoQualityOption("hd720", "720p", "HD"),
        VideoQualityOption("large", "480p", null),
        VideoQualityOption("medium", "360p", null),
        VideoQualityOption("small", "240p", null),
        VideoQualityOption("tiny", saverLabel, "Saver")
    )

    if (availableQualities.isEmpty()) return allPresets
    val filtered = allPresets.filter { opt ->
        opt.code == "auto" || availableQualities.any { it.equals(opt.code, ignoreCase = true) }
    }
    return if (filtered.size <= 1) allPresets else filtered
}

@Composable
internal fun QualitySubPage(
    availableQualities: List<String>,
    currentQuality: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    val options = buildQualityOptionList(availableQualities)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        PlayerSheetSubHeader(
            title = stringResource(R.string.player_quality),
            onBack = onBack
        )
        options.forEach { item ->
            val isSelected = item.code.equals(currentQuality, ignoreCase = true)
            PlayerSheetCheckRow(
                label = item.label,
                badge = item.badge,
                isSelected = isSelected,
                onClick = { onSelect(item.code) }
            )
        }
    }
}

@Composable
internal fun SpeedSubPage(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onBack: () -> Unit
) {
    var sliderSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        PlayerSheetSubHeader(
            title = stringResource(R.string.player_speed),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .border(0.5.dp, GlassBorderLight, RoundedCornerShape(16.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format(Locale.US, "%.2fx", sliderSpeed)
                    .trimEnd('0')
                    .trimEnd('.')
                    .let { if (!it.endsWith("x")) "${it}x" else it },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = YouTubeRed
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        val next = ((sliderSpeed - 0.05f).coerceAtLeast(0.25f) * 20).roundToInt() / 20f
                        sliderSpeed = next
                        onSelect(next)
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(YouTubeSurfaceLight)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Slider(
                    value = sliderSpeed,
                    onValueChange = { raw ->
                        sliderSpeed = (raw * 20).roundToInt() / 20f
                    },
                    onValueChangeFinished = {
                        onSelect(sliderSpeed)
                    },
                    valueRange = 0.25f..2.0f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = YouTubeRed,
                        activeTrackColor = YouTubeRed,
                        inactiveTrackColor = GlassBorderLight
                    )
                )

                IconButton(
                    onClick = {
                        val next = ((sliderSpeed + 0.05f).coerceAtMost(2.0f) * 20).roundToInt() / 20f
                        sliderSpeed = next
                        onSelect(next)
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(YouTubeSurfaceLight)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        presets.forEach { speed ->
            val isSelected = kotlin.math.abs(speed - currentSpeed) < 0.01f
            val label = if (speed == 1.0f) {
                stringResource(R.string.settings_speed_normal)
            } else {
                "${speed}x"
            }
            PlayerSheetCheckRow(
                label = label,
                isSelected = isSelected,
                onClick = {
                    sliderSpeed = speed
                    onSelect(speed)
                }
            )
        }
    }
}

@Composable
internal fun SeekDurationSubPage(
    currentSeekSeconds: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit
) {
    val options = listOf(5, 10, 15, 20, 30, 60)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        PlayerSheetSubHeader(
            title = stringResource(R.string.settings_double_tap_seek),
            onBack = onBack
        )
        options.forEach { sec ->
            val label = if (sec == 10) {
                stringResource(R.string.settings_seek_seconds_default, sec.toString())
            } else {
                stringResource(R.string.settings_seek_seconds, sec.toString())
            }
            PlayerSheetCheckRow(
                label = label,
                isSelected = currentSeekSeconds == sec,
                onClick = { onSelect(sec) }
            )
        }
    }
}

@Composable
internal fun SleepTimerSubPage(
    selectedOption: SleepTimerOption,
    remainingSec: Int?,
    onSelect: (SleepTimerOption) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        PlayerSheetSubHeader(
            title = stringResource(R.string.settings_sleep_timer),
            subtitle = if (remainingSec != null && remainingSec > 0) {
                val mins = remainingSec / 60
                val secs = remainingSec % 60
                stringResource(
                    R.string.settings_sleep_timer_stopping_in,
                    String.format(Locale.US, "%02d:%02d", mins, secs)
                )
            } else null,
            onBack = onBack
        )
        SleepTimerOption.entries.forEach { option ->
            PlayerSheetCheckRow(
                label = stringResource(option.labelRes),
                isSelected = selectedOption == option,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
internal fun PlayerSheetSubHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                ),
                color = TextPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp,
        color = GlassBorderDim
    )
}

@Composable
internal fun PlayerSheetCheckRow(
    label: String,
    badge: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 15.5.sp
                    ),
                    color = TextPrimary
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GlassSurface)
                            .border(
                                0.5.dp,
                                GlassBorderLight,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextSecondary
                        )
                    }
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.player_selected),
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 22.dp),
            thickness = 0.5.dp,
            color = GlassBorderDim
        )
    }
}

@Composable
internal fun PlayerSheetActionItem(
    icon: ImageVector,
    label: String,
    value: String,
    valueAccent: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 13.5.sp
            ),
            color = if (valueAccent) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 160.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun PlayerSheetToggleItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0D0D0D),
                checkedTrackColor = TextPrimary,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassSurface,
                uncheckedBorderColor = GlassBorderLight
            )
        )
    }
}
