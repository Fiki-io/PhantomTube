package com.phantom.tube.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phantom.tube.BuildConfig
import com.phantom.tube.R
import com.phantom.tube.core.theme.YouTubeDark
import com.phantom.tube.core.util.LocaleHelper
import com.phantom.tube.core.util.RegionHelper
import com.phantom.tube.core.util.findActivity
import com.phantom.tube.core.util.openExternalUrl
import com.phantom.tube.core.util.sendFeedbackEmail
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.data.settings.PhantomPreferences
import com.phantom.tube.ui.screens.settings.components.SettingsConfirmDialog
import com.phantom.tube.ui.screens.settings.components.SettingsDivider
import com.phantom.tube.ui.screens.settings.components.SettingsFeedbackDialog
import com.phantom.tube.ui.screens.settings.components.SettingsGroupCard
import com.phantom.tube.ui.screens.settings.components.SettingsSectionHeader
import com.phantom.tube.ui.screens.settings.components.SettingsSingleChoiceDialog
import com.phantom.tube.ui.screens.settings.components.SettingsSubScreenHeader
import com.phantom.tube.ui.screens.settings.components.SettingsToggleRow
import com.phantom.tube.ui.screens.settings.components.SettingsValueRow
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    preferences: PhantomPreferences,
    repository: PhantomRepository,
    onBackClick: () -> Unit,
    onCheckUpdateClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val appLanguage by preferences.appLanguage.collectAsState()
    val contentCountry by preferences.contentCountry.collectAsState()
    val defaultQuality by preferences.defaultQuality.collectAsState()
    val defaultSpeed by preferences.defaultSpeed.collectAsState()
    val doubleTapSeekSeconds by preferences.doubleTapSeekSeconds.collectAsState()
    val bgPlayback by preferences.backgroundPlaybackEnabled.collectAsState()
    val autoPip by preferences.autoPipEnabled.collectAsState()
    val miniPlayerStyle by preferences.miniPlayerStyle.collectAsState()

    val sbEnabled by preferences.sponsorBlockEnabled.collectAsState()
    val sbAutoSkip by preferences.sponsorBlockAutoSkip.collectAsState()
    val skipSponsor by preferences.skipSponsor.collectAsState()
    val skipSelfPromo by preferences.skipSelfPromo.collectAsState()
    val skipInteraction by preferences.skipInteraction.collectAsState()
    val skipIntro by preferences.skipIntro.collectAsState()
    val skipOutro by preferences.skipOutro.collectAsState()

    val pauseWatchHistory by preferences.pauseWatchHistory.collectAsState()

    var cacheSizeBytes by remember {
        mutableLongStateOf(preferences.getCacheSizeBytes(context))
    }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showRegionDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSeekDialog by remember { mutableStateOf(false) }
    var showMiniPlayerStyleDialog by remember { mutableStateOf(false) }
    var showClearWatchHistoryConfirm by remember { mutableStateOf(false) }
    var showClearSearchHistoryConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBackClick)

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val qualityOptions = listOf(
        "auto" to stringResource(R.string.settings_quality_auto),
        "highres" to stringResource(R.string.settings_quality_highest),
        "hd1080" to "1080p Full HD",
        "hd720" to "720p HD",
        "large" to "480p",
        "medium" to "360p",
        "tiny" to stringResource(R.string.settings_quality_saver)
    )
    val currentQualityLabel = qualityOptions.firstOrNull { it.first == defaultQuality }?.second
        ?: defaultQuality.uppercase()

    val currentLanguageLabel = LocaleHelper.SUPPORTED_LANGUAGES
        .firstOrNull { it.code == appLanguage }?.displayName ?: "English"

    val currentRegionLabel = RegionHelper.SUPPORTED_REGIONS
        .firstOrNull { it.code == contentCountry }?.name ?: contentCountry

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YouTubeDark)
    ) {
        com.phantom.tube.ui.components.PhantomStatusBarStrip()

        SettingsSubScreenHeader(
            title = stringResource(R.string.settings_title),
            onBack = onBackClick
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = navBarPadding + 32.dp)
        ) {
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_general))
                SettingsGroupCard {
                    SettingsValueRow(
                        icon = Icons.Rounded.Language,
                        title = stringResource(R.string.settings_app_language),
                        valueText = currentLanguageLabel,
                        onClick = { showLanguageDialog = true }
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        icon = Icons.Rounded.Public,
                        title = stringResource(R.string.settings_content_region),
                        valueText = currentRegionLabel,
                        onClick = { showRegionDialog = true }
                    )
                }
            }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_playback))
                SettingsGroupCard {
                    SettingsValueRow(
                        icon = Icons.Rounded.Hd,
                        title = stringResource(R.string.settings_default_quality),
                        valueText = currentQualityLabel,
                        onClick = { showQualityDialog = true }
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        icon = Icons.Rounded.Speed,
                        title = stringResource(R.string.settings_default_speed),
                        valueText = if (defaultSpeed == 1.0f) {
                            stringResource(R.string.settings_speed_normal)
                        } else {
                            "${defaultSpeed}x"
                        },
                        onClick = { showSpeedDialog = true }
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        icon = Icons.Rounded.FastForward,
                        title = stringResource(R.string.settings_double_tap_seek),
                        valueText = stringResource(R.string.settings_seek_seconds, doubleTapSeekSeconds.toString()),
                        onClick = { showSeekDialog = true }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Headphones,
                        title = stringResource(R.string.settings_bg_playback),
                        subtitle = stringResource(R.string.settings_bg_playback_desc),
                        checked = bgPlayback,
                        onCheckedChange = { preferences.setBackgroundPlaybackEnabled(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.PictureInPictureAlt,
                        title = stringResource(R.string.settings_auto_pip),
                        subtitle = stringResource(R.string.settings_auto_pip_desc),
                        checked = autoPip,
                        onCheckedChange = { preferences.setAutoPipEnabled(it) }
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        icon = Icons.Rounded.FitScreen,
                        title = stringResource(R.string.settings_miniplayer_style),
                        valueText = if (miniPlayerStyle == com.phantom.tube.data.settings.PhantomPreferences.MINIPLAYER_STYLE_FLOATING) {
                            stringResource(R.string.settings_miniplayer_floating)
                        } else {
                            stringResource(R.string.settings_miniplayer_bar)
                        },
                        onClick = { showMiniPlayerStyleDialog = true }
                    )
                }
            }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_sponsorblock))
                SettingsGroupCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Shield,
                        title = stringResource(R.string.settings_sponsorblock_enable),
                        subtitle = stringResource(R.string.settings_sponsorblock_desc),
                        checked = sbEnabled,
                        onCheckedChange = { preferences.setSponsorBlockEnabled(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.settings_sponsorblock_auto_skip),
                        subtitle = if (sbAutoSkip) {
                            stringResource(R.string.settings_sponsorblock_auto_skip_desc)
                        } else {
                            stringResource(R.string.settings_sponsorblock_manual_skip_desc)
                        },
                        checked = sbAutoSkip,
                        enabled = sbEnabled,
                        onCheckedChange = { preferences.setSponsorBlockAutoSkip(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Shield,
                        title = stringResource(R.string.settings_skip_sponsor),
                        checked = skipSponsor,
                        enabled = sbEnabled,
                        onCheckedChange = { preferences.setSkipSponsor(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Shield,
                        title = stringResource(R.string.settings_skip_selfpromo),
                        checked = skipSelfPromo,
                        enabled = sbEnabled,
                        onCheckedChange = { preferences.setSkipSelfPromo(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Shield,
                        title = stringResource(R.string.settings_skip_interaction),
                        checked = skipInteraction,
                        enabled = sbEnabled,
                        onCheckedChange = { preferences.setSkipInteraction(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Shield,
                        title = stringResource(R.string.settings_skip_intro),
                        checked = skipIntro,
                        enabled = sbEnabled,
                        onCheckedChange = { preferences.setSkipIntro(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Shield,
                        title = stringResource(R.string.settings_skip_outro),
                        checked = skipOutro,
                        enabled = sbEnabled,
                        onCheckedChange = { preferences.setSkipOutro(it) }
                    )
                }
            }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_privacy))
                SettingsGroupCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.VisibilityOff,
                        title = stringResource(R.string.settings_pause_history),
                        subtitle = stringResource(R.string.settings_pause_history_desc),
                        checked = pauseWatchHistory,
                        onCheckedChange = { preferences.setPauseWatchHistory(it) }
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        icon = Icons.Rounded.DeleteSweep,
                        title = stringResource(R.string.settings_clear_watch_history),
                        valueText = "",
                        onClick = { showClearWatchHistoryConfirm = true }
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.settings_clear_search_history),
                        valueText = "",
                        onClick = { showClearSearchHistoryConfirm = true }
                    )
                    SettingsDivider()
                    val formattedCache = remember(cacheSizeBytes) {
                        val mb = cacheSizeBytes.toDouble() / (1024.0 * 1024.0)
                        String.format(Locale.US, "%.1f MB", mb)
                    }
                    SettingsValueRow(
                        icon = Icons.Rounded.CleaningServices,
                        title = stringResource(R.string.settings_clear_cache),
                        valueText = formattedCache,
                        onClick = { showClearCacheConfirm = true }
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.settings_privacy_policy_title),
                        valueText = "",
                        onClick = {
                            context.openExternalUrl("https://github.com/Fiki-io/PhantomTube/blob/main/PRIVACY_POLICY.md")
                        }
                    )
                }
            }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_about))
                SettingsGroupCard {
                    SettingsValueRow(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.settings_app_version),
                        valueText = "v${BuildConfig.VERSION_NAME}"
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        icon = Icons.Rounded.SystemUpdate,
                        title = stringResource(R.string.settings_check_update),
                        valueText = "",
                        onClick = onCheckUpdateClick
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        icon = Icons.Rounded.BugReport,
                        title = stringResource(R.string.settings_feedback),
                        valueText = "",
                        onClick = { showFeedbackDialog = true }
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.settings_github),
                        valueText = "GitHub",
                        onClick = {
                            context.openExternalUrl("https://github.com/Fiki-io/PhantomTube")
                        }
                    )
                }
            }
        }
    }

    if (showLanguageDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.settings_app_language),
            options = LocaleHelper.SUPPORTED_LANGUAGES.map { it.code to it.displayName },
            selectedValue = appLanguage,
            onSelect = { newLang ->
                if (newLang != appLanguage) {
                    preferences.setAppLanguage(newLang)
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showRegionDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.settings_content_region),
            options = RegionHelper.SUPPORTED_REGIONS.map { it.code to it.name },
            selectedValue = contentCountry,
            onSelect = { preferences.setContentCountry(it) },
            onDismiss = { showRegionDialog = false }
        )
    }

    if (showQualityDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.settings_default_quality),
            options = qualityOptions,
            selectedValue = defaultQuality,
            onSelect = { preferences.setDefaultQuality(it) },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.settings_default_speed),
            options = speeds.map { speed ->
                speed to if (speed == 1.0f) {
                    context.getString(R.string.settings_speed_normal)
                } else {
                    "${speed}x"
                }
            },
            selectedValue = defaultSpeed,
            onSelect = { preferences.setDefaultSpeed(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showSeekDialog) {
        val seekOptions = listOf(5, 10, 15, 20, 30, 60)
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.settings_double_tap_seek),
            options = seekOptions.map { sec ->
                sec to if (sec == 10) {
                    context.getString(R.string.settings_seek_seconds_default, sec.toString())
                } else {
                    context.getString(R.string.settings_seek_seconds, sec.toString())
                }
            },
            selectedValue = doubleTapSeekSeconds,
            onSelect = { preferences.setDoubleTapSeekSeconds(it) },
            onDismiss = { showSeekDialog = false }
        )
    }

    if (showMiniPlayerStyleDialog) {
        val styleOptions = listOf(
            com.phantom.tube.data.settings.PhantomPreferences.MINIPLAYER_STYLE_FLOATING to stringResource(R.string.settings_miniplayer_floating),
            com.phantom.tube.data.settings.PhantomPreferences.MINIPLAYER_STYLE_BAR to stringResource(R.string.settings_miniplayer_bar)
        )
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.settings_miniplayer_style),
            options = styleOptions,
            selectedValue = miniPlayerStyle,
            onSelect = { preferences.setMiniPlayerStyle(it) },
            onDismiss = { showMiniPlayerStyleDialog = false }
        )
    }

    if (showClearWatchHistoryConfirm) {
        SettingsConfirmDialog(
            title = stringResource(R.string.settings_clear_watch_history),
            message = stringResource(R.string.dialog_confirm_clear_history),
            confirmLabel = stringResource(R.string.dialog_delete),
            isDestructive = true,
            onConfirm = {
                scope.launch {
                    repository.clearWatchHistory()
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_history_cleared),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { showClearWatchHistoryConfirm = false }
        )
    }

    if (showClearSearchHistoryConfirm) {
        SettingsConfirmDialog(
            title = stringResource(R.string.settings_clear_search_history),
            message = stringResource(R.string.dialog_confirm_clear_search),
            confirmLabel = stringResource(R.string.dialog_delete),
            isDestructive = true,
            onConfirm = {
                scope.launch {
                    repository.clearSearchHistory()
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_history_cleared),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { showClearSearchHistoryConfirm = false }
        )
    }

    if (showClearCacheConfirm) {
        SettingsConfirmDialog(
            title = stringResource(R.string.settings_clear_cache),
            message = stringResource(R.string.dialog_confirm_clear_cache),
            confirmLabel = stringResource(R.string.dialog_clean),
            isDestructive = false,
            onConfirm = {
                preferences.clearAppCache(context)
                cacheSizeBytes = 0L
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_cache_cleared),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showClearCacheConfirm = false }
        )
    }

    if (showFeedbackDialog) {
        SettingsFeedbackDialog(
            onSendFeedback = { typeIdx, message ->
                val subjectTag = when (typeIdx) {
                    0 -> context.getString(R.string.settings_feedback_subject_bug)
                    1 -> context.getString(R.string.settings_feedback_subject_feature)
                    else -> context.getString(R.string.settings_feedback_subject_general)
                }
                val subject = "[PhantomTube v${BuildConfig.VERSION_NAME}] $subjectTag"
                val body = buildString {
                    appendLine(message)
                    appendLine()
                    appendLine("--- Device Info ---")
                    appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                }
                context.sendFeedbackEmail(
                    recipient = "totoyu379@gmail.com",
                    subject = subject,
                    body = body
                )
            },
            onDismiss = { showFeedbackDialog = false }
        )
    }
}
