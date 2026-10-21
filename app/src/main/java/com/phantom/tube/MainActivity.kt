package com.phantom.tube

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import com.phantom.tube.core.util.LocaleHelper
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.PhantomTheme
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.ui.components.BubbleBottomNav
import com.phantom.tube.ui.components.NavTab
import com.phantom.tube.ui.screens.history.HistoryScreen
import com.phantom.tube.ui.screens.home.HomeScreen
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import com.phantom.tube.core.update.UpdateCheckResult
import com.phantom.tube.ui.components.UpdateDialog
import com.phantom.tube.ui.components.UpdateDialogState
import kotlinx.coroutines.launch
import com.phantom.tube.ui.screens.channel.ChannelScreen
import com.phantom.tube.ui.screens.player.PlayerScreen
import com.phantom.tube.ui.screens.search.SearchScreen
import com.phantom.tube.ui.screens.settings.SettingsScreen
import com.phantom.tube.ui.screens.subscription.SubscriptionScreen
import com.phantom.tube.ui.screens.downloads.DownloadsScreen
import com.phantom.tube.ui.screens.player.components.OfflineMiniPlayer
import com.phantom.tube.player.offline.OfflineAudioPlayerManager
import android.annotation.SuppressLint

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {

    private var isInPipMode by mutableStateOf(false)
    private var activeVideo by mutableStateOf<VideoItem?>(null)
    private var isPlayerMinimized by mutableStateOf(false)
    private var pendingDeepLinkChannelId by mutableStateOf<String?>(null)

    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Handled notification permission response
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        handleIncomingIntent(intent)

        val app = application as PhantomApp
        val repository = app.repository

        setContent {
            val preferences = app.preferences
            val appLanguage by (preferences?.appLanguage ?: kotlinx.coroutines.flow.MutableStateFlow("id")).collectAsState()
            val baseContext = LocalContext.current
            val currentConfig = LocalConfiguration.current

            val hostActivity = this@MainActivity
            val localizedContext = remember(appLanguage, baseContext) {
                val configContext = LocaleHelper.applyLocale(baseContext, appLanguage)
                object : android.content.ContextWrapper(configContext) {
                    override fun getBaseContext(): android.content.Context = hostActivity

                    override fun startActivity(intent: android.content.Intent?) {
                        if (intent != null) {
                            hostActivity.startActivity(intent)
                        }
                    }

                    override fun startActivity(intent: android.content.Intent?, options: Bundle?) {
                        if (intent != null) {
                            hostActivity.startActivity(intent, options)
                        }
                    }
                }
            }
            val localizedConfig = remember(appLanguage, currentConfig) {
                android.content.res.Configuration(currentConfig).apply {
                    setLocale(LocaleHelper.getLocale(appLanguage))
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig
            ) {
                PhantomTheme {
                var currentTab by remember { mutableStateOf(NavTab.HOME) }
                var activeChannelId by remember { mutableStateOf<String?>(null) }
                var rememberedChannelId by remember { mutableStateOf("") }
                var activeChannelTitle by remember { mutableStateOf("") }
                var isSettingsOpen by remember { mutableStateOf(false) }
                var isDownloadsOpen by remember { mutableStateOf(false) }

                LaunchedEffect(pendingDeepLinkChannelId) {
                    val targetCh = pendingDeepLinkChannelId
                    if (!targetCh.isNullOrBlank()) {
                        activeChannelId = targetCh
                        activeChannelTitle = targetCh
                        pendingDeepLinkChannelId = null
                    }
                }

                LaunchedEffect(activeChannelId) {
                    if (!activeChannelId.isNullOrBlank()) {
                        rememberedChannelId = activeChannelId!!
                    }
                }

                val updateManager = app.updateManager
                val scope = rememberCoroutineScope()
                var updateDialogState by remember { mutableStateOf<UpdateDialogState?>(null) }

                // Check for updates automatically in the background on app start
                LaunchedEffect(Unit) {
                    val result = updateManager.checkForUpdate()
                    if (result is UpdateCheckResult.UpdateAvailable) {
                        updateDialogState = UpdateDialogState.Available(result.info)
                    }
                }

                fun triggerManualUpdateCheck() {
                    scope.launch {
                        Toast.makeText(this@MainActivity, "Memeriksa pembaruan...", Toast.LENGTH_SHORT).show()
                        when (val result = updateManager.checkForUpdate()) {
                            is UpdateCheckResult.UpdateAvailable -> {
                                updateDialogState = UpdateDialogState.Available(result.info)
                            }
                            is UpdateCheckResult.UpToDate -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.settings_up_to_date_toast, updateManager.getCurrentVersionName()),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is UpdateCheckResult.Error -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.settings_update_check_failed_toast, result.message),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                // Intercept system back press when ChannelScreen, SettingsScreen, or DownloadsScreen is active
                if (activeChannelId != null) {
                    BackHandler {
                        activeChannelId = null
                    }
                }

                if (isSettingsOpen) {
                    BackHandler {
                        isSettingsOpen = false
                    }
                }

                if (isDownloadsOpen) {
                    BackHandler {
                        isDownloadsOpen = false
                    }
                }

                if (currentTab != NavTab.HOME && activeChannelId == null && !isSettingsOpen && !isDownloadsOpen && (activeVideo == null || isPlayerMinimized)) {
                    BackHandler {
                        currentTab = NavTab.HOME
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianDark)
                ) {
                    // 1. BASE SCREEN TABS (Always active in background, edge-to-edge)
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                val forward = targetState.ordinal > initialState.ordinal
                                val slideDirection = if (forward) 1 else -1
                                (slideInHorizontally(
                                    animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset,
                                    initialOffsetX = { fullWidth -> (fullWidth * 0.18f * slideDirection).toInt() }
                                ) + fadeIn(
                                    animationSpec = tween(durationMillis = 200)
                                )) togetherWith (slideOutHorizontally(
                                    animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset,
                                    targetOffsetX = { fullWidth -> (-fullWidth * 0.18f * slideDirection).toInt() }
                                ) + fadeOut(
                                    animationSpec = tween(durationMillis = 160)
                                ))
                            },
                            label = "tab_navigation_transition"
                        ) { tab ->
                            when (tab) {
                                NavTab.HOME -> HomeScreen(
                                    repository = repository,
                                    onVideoClick = { video: VideoItem ->
                                        OfflineAudioPlayerManager.stop(this@MainActivity)
                                        activeVideo = video
                                        isPlayerMinimized = false
                                    },
                                    onChannelClick = { chId: String, chTitle: String ->
                                        activeChannelId = chId
                                        activeChannelTitle = chTitle
                                    },
                                    onSearchClick = { currentTab = NavTab.SEARCH },
                                    onSettingsClick = { isSettingsOpen = true }
                                )
                                NavTab.SEARCH -> SearchScreen(
                                    repository = repository,
                                    onVideoClick = { video: VideoItem ->
                                        OfflineAudioPlayerManager.stop(this@MainActivity)
                                        activeVideo = video
                                        isPlayerMinimized = false
                                    },
                                    onChannelClick = { chId: String, chTitle: String ->
                                        activeChannelId = chId
                                        activeChannelTitle = chTitle
                                    },
                                    onBackClick = { currentTab = NavTab.HOME }
                                )
                                NavTab.HISTORY -> HistoryScreen(
                                    repository = repository,
                                    onVideoClick = { video: VideoItem ->
                                        OfflineAudioPlayerManager.stop(this@MainActivity)
                                        activeVideo = video
                                        isPlayerMinimized = false
                                    }
                                )
                                NavTab.SUBSCRIPTION -> SubscriptionScreen(
                                    repository = repository,
                                    onVideoClick = { video: VideoItem ->
                                        OfflineAudioPlayerManager.stop(this@MainActivity)
                                        activeVideo = video
                                        isPlayerMinimized = false
                                    },
                                    onChannelClick = { chId: String, chTitle: String ->
                                        activeChannelId = chId
                                        activeChannelTitle = chTitle
                                    },
                                    onExploreClick = { currentTab = NavTab.HOME },
                                    onDownloadsClick = { isDownloadsOpen = true }
                                )
                            }
                        }
                    }

                    // 2. BUBBLE BOTTOM NAVIGATION DOCK (Smooth slide & fade)
                    val isBottomDockVisible = !isInPipMode &&
                        (activeVideo == null || isPlayerMinimized) &&
                        activeChannelId == null &&
                        !isSettingsOpen &&
                        !isDownloadsOpen

                    AnimatedVisibility(
                        visible = isBottomDockVisible,
                        enter = androidx.compose.animation.slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset
                        ) + fadeIn(tween(180)),
                        exit = androidx.compose.animation.slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset
                        ) + fadeOut(tween(150)),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        BubbleBottomNav(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    }

                    // 3. CHANNEL SCREEN OVERLAY (Preserves rememberedChannelId during exit transition)
                    AnimatedVisibility(
                        visible = activeChannelId != null,
                        enter = slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset
                        ) + fadeIn(animationSpec = tween(180)),
                        exit = slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset
                        ) + fadeOut(animationSpec = tween(180))
                    ) {
                        val targetChannelId = activeChannelId ?: rememberedChannelId
                        if (targetChannelId.isNotBlank()) {
                            ChannelScreen(
                                channelId = targetChannelId,
                                initialChannelTitle = activeChannelTitle,
                                repository = repository,
                                onVideoClick = { video: VideoItem ->
                                    OfflineAudioPlayerManager.stop(this@MainActivity)
                                    activeVideo = video
                                    isPlayerMinimized = false
                                },
                                onChannelClick = { chId: String, chTitle: String ->
                                    activeChannelId = chId
                                    activeChannelTitle = chTitle
                                },
                                onBackClick = { activeChannelId = null },
                                onSearchClick = {
                                    activeChannelId = null
                                    currentTab = NavTab.SEARCH
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // 3.5 SETTINGS SCREEN OVERLAY
                    AnimatedVisibility(
                        visible = isSettingsOpen,
                        enter = slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset
                        ) + fadeIn(animationSpec = tween(180)),
                        exit = slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset
                        ) + fadeOut(animationSpec = tween(180))
                    ) {
                        SettingsScreen(
                            preferences = app.preferences,
                            repository = repository,
                            onBackClick = { isSettingsOpen = false },
                            onCheckUpdateClick = { triggerManualUpdateCheck() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 3.6 DOWNLOADS SCREEN OVERLAY
                    AnimatedVisibility(
                        visible = isDownloadsOpen,
                        enter = slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset
                        ) + fadeIn(animationSpec = tween(180)),
                        exit = slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset
                        ) + fadeOut(animationSpec = tween(180))
                    ) {
                        DownloadsScreen(
                            repository = repository,
                            onBackClick = { isDownloadsOpen = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 3.7 OFFLINE MINI PLAYER BAR
                    val offlineTrack by OfflineAudioPlayerManager.currentTrack.collectAsState()
                    val hasBottomNav = activeChannelId == null && !isSettingsOpen && !isDownloadsOpen
                    AnimatedVisibility(
                        visible = offlineTrack != null && (activeVideo == null || isPlayerMinimized) && !isInPipMode,
                        enter = androidx.compose.animation.slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset
                        ) + fadeIn(tween(200)),
                        exit = androidx.compose.animation.slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = com.phantom.tube.ui.components.IosSpringSpecs.GentleOffset
                        ) + fadeOut(tween(160)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = if (hasBottomNav) 76.dp else 12.dp)
                    ) {
                        OfflineMiniPlayer()
                    }

                    // 4. PERSISTENT PLAYER (Full screen OR Miniplayer)
                    if (activeVideo != null) {
                        PlayerScreen(
                            video = activeVideo!!,
                            repository = repository,
                            isMinimized = isPlayerMinimized,
                            isBottomNavVisible = (activeChannelId == null && !isSettingsOpen && !isDownloadsOpen),
                            onMinimize = {
                                isPlayerMinimized = true
                            },
                            onExpand = {
                                isPlayerMinimized = false
                            },
                            onClose = {
                                activeVideo = null
                                isPlayerMinimized = false
                            },
                            onPlayNextVideo = { nextVideo: VideoItem ->
                                activeVideo = nextVideo
                            },
                            onChannelClick = { chId: String, chTitle: String ->
                                activeChannelId = chId
                                activeChannelTitle = chTitle
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 5. IN-APP AUTO UPDATE DIALOG
                    if (updateDialogState != null) {
                        UpdateDialog(
                            state = updateDialogState!!,
                            onDismiss = {
                                val isForce = (updateDialogState as? UpdateDialogState.Available)?.info?.isForceUpdate == true ||
                                              (updateDialogState as? UpdateDialogState.Downloading)?.info?.isForceUpdate == true ||
                                              com.phantom.tube.core.security.PhantomNative.isLocked()
                                if (!isForce) {
                                    updateDialogState = null
                                }
                            },
                            onStartDownload = { info ->
                                scope.launch {
                                    updateDialogState = UpdateDialogState.Downloading(info, 0f)
                                    val file = updateManager.downloadApk(info.downloadUrl) { progress ->
                                        updateDialogState = UpdateDialogState.Downloading(info, progress)
                                    }
                                    if (file != null && file.exists()) {
                                        if (!updateManager.canInstallApks()) {
                                            updateDialogState = UpdateDialogState.PermissionRequired(
                                                onOpenSettings = {
                                                    updateManager.openInstallPermissionSettings()
                                                }
                                            )
                                        } else {
                                            updateDialogState = null
                                            updateManager.promptInstall(file)
                                        }
                                    } else {
                                        updateDialogState = UpdateDialogState.Error(
                                            message = getString(R.string.update_dialog_download_failed),
                                            onRetry = {
                                                updateDialogState = UpdateDialogState.Available(info)
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incoming: android.content.Intent?) {
        if (incoming == null) return
        val parsed = when (incoming.action) {
            android.content.Intent.ACTION_VIEW -> {
                val dataString = incoming.dataString
                if (!dataString.isNullOrBlank()) {
                    com.phantom.tube.core.util.parseAppLink(dataString)
                } else null
            }
            android.content.Intent.ACTION_SEND -> {
                val sharedText = incoming.getStringExtra(android.content.Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    com.phantom.tube.core.util.extractYouTubeLinkFromText(sharedText)
                } else null
            }
            else -> null
        } ?: return

        when (parsed) {
            is com.phantom.tube.core.util.ParsedAppLink.YouTubeVideo -> {
                OfflineAudioPlayerManager.stop(this)
                activeVideo = VideoItem(
                    id = parsed.videoId,
                    title = "",
                    channelTitle = "",
                    thumbnailUrl = "https://i.ytimg.com/vi/${parsed.videoId}/hqdefault.jpg",
                    playlistId = parsed.playlistId
                )
                isPlayerMinimized = false
            }
            is com.phantom.tube.core.util.ParsedAppLink.YouTubeChannel -> {
                pendingDeepLinkChannelId = parsed.channelIdOrHandle
            }
            is com.phantom.tube.core.util.ParsedAppLink.ExternalWebUrl -> {
                // Ignore non-YouTube URLs on MainActivity launch
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Enter Picture-in-Picture only if enabled in preferences and video is actively playing in FULL player mode
        val isAutoPip = (application as? PhantomApp)?.preferences?.autoPipEnabled?.value ?: true
        if (isAutoPip && activeVideo != null && !isPlayerMinimized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                // Ignore if not supported on specific hardware
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }
}
