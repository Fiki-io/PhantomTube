package com.phantom.tube.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.phantom.tube.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.core.util.LocaleHelper
import com.phantom.tube.core.util.RegionHelper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.IconButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBackgroundAlt
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.SystemStatusBarColor
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.core.theme.YouTubeSurfaceLight
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.data.settings.PhantomPreferences
import com.phantom.tube.ui.components.PhantomIconButton
import kotlinx.coroutines.launch

private val REGION_OPTIONS = RegionHelper.SUPPORTED_REGIONS

private data class QualityOption(val code: String, val label: String)

private val QUALITY_OPTIONS = listOf(
    QualityOption("auto", "Otomatis (Disarankan)"),
    QualityOption("hd1080", "1080p (Full HD)"),
    QualityOption("hd720", "720p (HD)"),
    QualityOption("large", "480p"),
    QualityOption("medium", "360p"),
    QualityOption("small", "240p")
)

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@Composable
fun SettingsScreen(
    preferences: PhantomPreferences,
    repository: PhantomRepository,
    onBackClick: () -> Unit,
    onCheckUpdateClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val appVersionName = remember(context) {
        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    // Preferences states
    val defaultQuality by preferences.defaultQuality.collectAsState()
    val defaultSpeed by preferences.defaultSpeed.collectAsState()
    val bgPlayback by preferences.backgroundPlaybackEnabled.collectAsState()
    val autoPip by preferences.autoPipEnabled.collectAsState()

    val sbEnabled by preferences.sponsorBlockEnabled.collectAsState()
    val sbAutoSkip by preferences.sponsorBlockAutoSkip.collectAsState()
    val skipSponsor by preferences.skipSponsor.collectAsState()
    val skipSelfPromo by preferences.skipSelfPromo.collectAsState()
    val skipInteraction by preferences.skipInteraction.collectAsState()
    val skipIntro by preferences.skipIntro.collectAsState()
    val skipOutro by preferences.skipOutro.collectAsState()

    val appLanguage by preferences.appLanguage.collectAsState()
    val contentCountry by preferences.contentCountry.collectAsState()
    val pauseWatchHistory by preferences.pauseWatchHistory.collectAsState()

    // Cache state
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        cacheSizeBytes = preferences.getCacheSizeBytes(context)
    }

    // Dialog states
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showRegionDialog by remember { mutableStateOf(false) }
    var showClearWatchHistoryDialog by remember { mutableStateOf(false) }
    var showClearSearchHistoryDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
    ) {
        // Strip bilah status hitam (di atas header pengaturan, bawah baterai)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(SystemStatusBarColor)
        )

        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhantomIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                size = 40.dp,
                iconSize = 22.dp,
                onClick = onBackClick
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.settings_title),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // CONTENT LIST
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            // 0. BAHASA & TAMPILAN
            SettingsSectionHeader(title = stringResource(R.string.settings_section_general))

            SettingsCard {
                val currentLang = LocaleHelper.SUPPORTED_LANGUAGES.firstOrNull { it.code == appLanguage }
                val currentLangLabel = if (currentLang != null) "${currentLang.flag} ${currentLang.displayName}" else appLanguage

                SettingsItemClickable(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_app_language),
                    subtitle = currentLangLabel,
                    onClick = { showLanguageDialog = true }
                )

                SettingsDivider()

                val currentRegion = REGION_OPTIONS.firstOrNull { it.code == contentCountry }
                val regionLabel = if (currentRegion != null) "${currentRegion.flag} ${currentRegion.name}" else contentCountry

                SettingsItemClickable(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_content_region),
                    subtitle = regionLabel,
                    onClick = { showRegionDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. PEMUTARAN & KUALITAS VIDEO
            SettingsSectionHeader(title = stringResource(R.string.settings_section_playback))

            SettingsCard {
                val currentQualityLabel = if (defaultQuality == "auto") {
                    stringResource(R.string.settings_quality_auto)
                } else {
                    QUALITY_OPTIONS.firstOrNull { it.code == defaultQuality }?.label ?: defaultQuality
                }
                SettingsItemClickable(
                    icon = Icons.Default.HighQuality,
                    title = stringResource(R.string.settings_default_quality),
                    subtitle = currentQualityLabel,
                    onClick = { showQualityDialog = true }
                )

                SettingsDivider()

                val currentSpeedLabel = if (defaultSpeed == 1.0f) stringResource(R.string.settings_speed_normal) else "${defaultSpeed}x"
                SettingsItemClickable(
                    icon = Icons.Default.Speed,
                    title = stringResource(R.string.settings_default_speed),
                    subtitle = currentSpeedLabel,
                    onClick = { showSpeedDialog = true }
                )

                SettingsDivider()

                SettingsItemSwitch(
                    icon = Icons.Default.SurroundSound,
                    title = stringResource(R.string.settings_bg_playback),
                    subtitle = stringResource(R.string.settings_bg_playback_desc),
                    checked = bgPlayback,
                    onCheckedChange = { preferences.setBackgroundPlaybackEnabled(it) }
                )

                SettingsDivider()

                SettingsItemSwitch(
                    icon = Icons.Default.PictureInPicture,
                    title = stringResource(R.string.settings_auto_pip),
                    subtitle = stringResource(R.string.settings_auto_pip_desc),
                    checked = autoPip,
                    onCheckedChange = { preferences.setAutoPipEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. SPONSORBLOCK
            SettingsSectionHeader(title = stringResource(R.string.settings_section_sponsorblock))

            SettingsCard {
                SettingsItemSwitch(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.settings_sponsorblock_enable),
                    subtitle = stringResource(R.string.settings_sponsorblock_desc),
                    checked = sbEnabled,
                    onCheckedChange = { preferences.setSponsorBlockEnabled(it) }
                )

                AnimatedVisibility(visible = sbEnabled) {
                    Column {
                        SettingsDivider()

                        SettingsItemSwitch(
                            icon = Icons.Default.PlayArrow,
                            title = stringResource(R.string.settings_sponsorblock_auto_skip),
                            subtitle = if (sbAutoSkip) stringResource(R.string.settings_sponsorblock_auto_skip_desc) else stringResource(R.string.settings_sponsorblock_manual_skip_desc),
                            checked = sbAutoSkip,
                            onCheckedChange = { preferences.setSponsorBlockAutoSkip(it) }
                        )

                        SettingsDivider()

                        Text(
                            text = stringResource(R.string.settings_sponsorblock_categories_title),
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        SettingsSubItemSwitch(
                            title = stringResource(R.string.settings_skip_sponsor),
                            checked = skipSponsor,
                            onCheckedChange = { preferences.setSkipSponsor(it) }
                        )

                        SettingsSubItemSwitch(
                            title = stringResource(R.string.settings_skip_selfpromo),
                            checked = skipSelfPromo,
                            onCheckedChange = { preferences.setSkipSelfPromo(it) }
                        )

                        SettingsSubItemSwitch(
                            title = stringResource(R.string.settings_skip_interaction),
                            checked = skipInteraction,
                            onCheckedChange = { preferences.setSkipInteraction(it) }
                        )

                        SettingsSubItemSwitch(
                            title = stringResource(R.string.settings_skip_intro),
                            checked = skipIntro,
                            onCheckedChange = { preferences.setSkipIntro(it) }
                        )

                        SettingsSubItemSwitch(
                            title = stringResource(R.string.settings_skip_outro),
                            checked = skipOutro,
                            onCheckedChange = { preferences.setSkipOutro(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. PRIVASI & PENYIMPANAN
            SettingsSectionHeader(title = stringResource(R.string.settings_section_privacy))

            SettingsCard {
                SettingsItemSwitch(
                    icon = Icons.Default.PauseCircleOutline,
                    title = stringResource(R.string.settings_pause_history),
                    subtitle = stringResource(R.string.settings_pause_history_desc),
                    checked = pauseWatchHistory,
                    onCheckedChange = { preferences.setPauseWatchHistory(it) }
                )

                SettingsDivider()

                SettingsItemClickable(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.settings_clear_watch_history),
                    subtitle = stringResource(R.string.dialog_confirm_clear_history),
                    onClick = { showClearWatchHistoryDialog = true }
                )

                SettingsDivider()

                SettingsItemClickable(
                    icon = Icons.Default.DeleteOutline,
                    title = stringResource(R.string.settings_clear_search_history),
                    subtitle = stringResource(R.string.dialog_confirm_clear_search),
                    onClick = { showClearSearchHistoryDialog = true }
                )

                SettingsDivider()

                val formattedCache = formatFileSize(cacheSizeBytes)
                SettingsItemClickable(
                    icon = Icons.Default.CleaningServices,
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = "${stringResource(R.string.settings_cache_size)}: $formattedCache",
                    onClick = { showClearCacheDialog = true }
                )
            }


            Spacer(modifier = Modifier.height(20.dp))

            // 4. TENTANG PHANTOM
            SettingsSectionHeader(title = stringResource(R.string.settings_section_about))

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Phantom",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_version_format, appVersionName),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                SettingsDivider()

                SettingsItemClickable(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_privacy_policy_title),
                    subtitle = stringResource(R.string.settings_privacy_policy_desc),
                    onClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://telegra.ph/Privacy-Policy---Phantom-09-25")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.settings_browser_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                SettingsDivider()

                SettingsItemClickable(
                    icon = Icons.Default.SystemUpdate,
                    title = stringResource(R.string.settings_check_update),
                    subtitle = stringResource(R.string.settings_check_update_desc),
                    onClick = onCheckUpdateClick
                )

                SettingsDivider()

                SettingsItemClickable(
                    icon = Icons.Default.Email,
                    title = stringResource(R.string.settings_feedback),
                    subtitle = stringResource(R.string.settings_feedback_desc),
                    onClick = { showFeedbackDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // ==========================================
    // DIALOGS & PICKERS
    // ==========================================

    // 1. Quality Picker Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(text = stringResource(R.string.settings_default_quality), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    QUALITY_OPTIONS.forEach { opt ->
                        val isSelected = opt.code == defaultQuality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    preferences.setDefaultQuality(opt.code)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    preferences.setDefaultQuality(opt.code)
                                    showQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = YouTubeRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (opt.code == "auto") stringResource(R.string.settings_quality_auto) else opt.label,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text(stringResource(R.string.player_close), color = YouTubeRed)
                }
            },
            containerColor = YouTubeSurface
        )
    }

    // 2. Speed Picker Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(text = stringResource(R.string.settings_default_speed), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SPEED_OPTIONS.forEach { speed ->
                        val isSelected = defaultSpeed == speed
                        val speedText = if (speed == 1.0f) stringResource(R.string.settings_speed_normal) else "${speed}x"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    preferences.setDefaultSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    preferences.setDefaultSpeed(speed)
                                    showSpeedDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = YouTubeRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = speedText,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(stringResource(R.string.player_close), color = YouTubeRed)
                }
            },
            containerColor = YouTubeSurface
        )
    }

    // 0. Language Picker Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_app_language),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LocaleHelper.SUPPORTED_LANGUAGES.forEach { lang ->
                        val isSelected = lang.code == appLanguage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    preferences.setAppLanguage(lang.code)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    preferences.setAppLanguage(lang.code)
                                    showLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = YouTubeRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${lang.flag}  ${lang.displayName}",
                                color = if (isSelected) YouTubeRed else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel), color = YouTubeRed)
                }
            },
            containerColor = YouTubeSurface
        )
    }

    // 3. Region Picker Dialog
    if (showRegionDialog) {
        AlertDialog(
            onDismissRequest = { showRegionDialog = false },
            title = { Text(text = stringResource(R.string.settings_content_region), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    REGION_OPTIONS.forEach { reg ->
                        val isSelected = reg.code == contentCountry
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    preferences.setContentCountry(reg.code)
                                    showRegionDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    preferences.setContentCountry(reg.code)
                                    showRegionDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = YouTubeRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${reg.flag}  ${reg.name}",
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRegionDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel), color = YouTubeRed)
                }
            },
            containerColor = YouTubeSurface
        )
    }

    // 4. Clear Watch History Dialog
    if (showClearWatchHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearWatchHistoryDialog = false },
            title = { Text(text = stringResource(R.string.dialog_confirm_clear_history), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = stringResource(R.string.dialog_confirm_clear_history),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.clearWatchHistory()
                            Toast.makeText(context, context.getString(R.string.toast_history_cleared), Toast.LENGTH_SHORT).show()
                            showClearWatchHistoryDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.dialog_delete), color = YouTubeRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearWatchHistoryDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel), color = TextSecondary)
                }
            },
            containerColor = YouTubeSurface
        )
    }

    // 5. Clear Search History Dialog
    if (showClearSearchHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearSearchHistoryDialog = false },
            title = { Text(text = stringResource(R.string.dialog_confirm_clear_search), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = stringResource(R.string.dialog_confirm_clear_search),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.clearSearchHistory()
                            Toast.makeText(context, context.getString(R.string.toast_history_cleared), Toast.LENGTH_SHORT).show()
                            showClearSearchHistoryDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.dialog_delete), color = YouTubeRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchHistoryDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel), color = TextSecondary)
                }
            },
            containerColor = YouTubeSurface
        )
    }

    // 6. Clear Cache Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(text = stringResource(R.string.dialog_confirm_clear_cache), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = stringResource(R.string.dialog_confirm_clear_cache),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferences.clearAppCache(context)
                        cacheSizeBytes = preferences.getCacheSizeBytes(context)
                        Toast.makeText(context, context.getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                        showClearCacheDialog = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_clean), color = YouTubeRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel), color = TextSecondary)
                }
            },
            containerColor = YouTubeSurface
        )
    }

    // 7. Feedback & Bug Report Dialog
    if (showFeedbackDialog) {
        var feedbackCategory by remember { mutableStateOf(0) } // 0: Bug, 1: Feature, 2: General
        var messageText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = YouTubeRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.feedback_dialog_title),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // Type Selector Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val types = listOf(
                            stringResource(R.string.feedback_type_bug),
                            stringResource(R.string.feedback_type_feature),
                            stringResource(R.string.feedback_type_general)
                        )
                        types.forEachIndexed { index, title ->
                            val isSelected = feedbackCategory == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) YouTubeRed else ObsidianDark)
                                    .clickable { feedbackCategory = index }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.feedback_message_hint),
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = YouTubeRed,
                            focusedBorderColor = YouTubeRed,
                            unfocusedBorderColor = YouTubeSurface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Technical Info Note
                    Text(
                        text = "ℹ️ ${stringResource(R.string.feedback_device_info_note)}\n(Phantom v$appVersionName • Android ${Build.VERSION.RELEASE} • ${Build.MANUFACTURER} ${Build.MODEL})",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val catLabel = when (feedbackCategory) {
                            0 -> context.getString(R.string.settings_feedback_subject_bug)
                            1 -> context.getString(R.string.settings_feedback_subject_feature)
                            else -> context.getString(R.string.settings_feedback_subject_general)
                        }
                        val subject = "[Phantom] $catLabel"
                        val emailBody = buildString {
                            appendLine(messageText.ifBlank { "Hello Phantom Team," })
                            appendLine()
                            appendLine("==============================")
                            appendLine("DEVICE & APP INFORMATION")
                            appendLine("==============================")
                            appendLine("Phantom Version: v$appVersionName")
                            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                            appendLine("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                            appendLine("App Language: $appLanguage")
                            appendLine("Content Region: $contentCountry")
                            appendLine("Tanggal: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
                        }

                        val uriString = "mailto:totoyu379@gmail.com" +
                            "?subject=" + Uri.encode(subject) +
                            "&body=" + Uri.encode(emailBody)
                        val mailtoUri = Uri.parse(uriString)

                        var isSent = false

                        // Method 1: Direct ACTION_SENDTO with encoded mailto URI (Preferred by Android system)
                        try {
                            val directIntent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(directIntent)
                            isSent = true
                        } catch (_: Exception) {}

                        // Method 2: ACTION_SENDTO with Chooser
                        if (!isSent) {
                            try {
                                val sendToChooser = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:totoyu379@gmail.com")
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("totoyu379@gmail.com"))
                                    putExtra(Intent.EXTRA_SUBJECT, subject)
                                    putExtra(Intent.EXTRA_TEXT, emailBody)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(sendToChooser, context.getString(R.string.settings_feedback_chooser)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                                isSent = true
                            } catch (_: Exception) {}
                        }

                        // Method 3: Standard ACTION_SEND with message/rfc822
                        if (!isSent) {
                            try {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "message/rfc822"
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("totoyu379@gmail.com"))
                                    putExtra(Intent.EXTRA_SUBJECT, subject)
                                    putExtra(Intent.EXTRA_TEXT, emailBody)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.settings_feedback_chooser)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                                isSent = true
                            } catch (_: Exception) {}
                        }

                        // Method 4: Fallback to ACTION_SEND text/plain
                        if (!isSent) {
                            try {
                                val textIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("totoyu379@gmail.com"))
                                    putExtra(Intent.EXTRA_SUBJECT, subject)
                                    putExtra(Intent.EXTRA_TEXT, emailBody)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(textIntent, context.getString(R.string.settings_feedback_chooser)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                                isSent = true
                            } catch (_: Exception) {}
                        }

                        if (isSent) {
                            showFeedbackDialog = false
                        } else {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Developer Email", "totoyu379@gmail.com"))
                            Toast.makeText(context, context.getString(R.string.feedback_no_email_app), Toast.LENGTH_LONG).show()
                            showFeedbackDialog = false
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.feedback_send_button),
                        color = YouTubeRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text(text = stringResource(R.string.dialog_cancel), color = TextSecondary)
                }
            },
            containerColor = YouTubeSurface
        )
    }

}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = YouTubeRed,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF272727))
    )
}

@Composable
private fun SettingsItemClickable(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CardBackgroundAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsItemSwitch(
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CardBackgroundAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) YouTubeRed else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = YouTubeRed,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = YouTubeSurfaceLight
            )
        )
    }
}

@Composable
private fun SettingsSubItemSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (checked) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = YouTubeRed,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = YouTubeSurfaceLight
            )
        )
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val kb = size / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$size B"
    }
}
