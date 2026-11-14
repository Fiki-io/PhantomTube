package com.phantom.tube.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RequiresApi
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.phantom.tube.MainActivity
import com.phantom.tube.R
import com.phantom.tube.core.network.NetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhantomMediaService : Service() {

    private val binder = LocalBinder()
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTitle: String = "Phantom Player"
    private var currentChannel: String = ""
    private var isPlaying: Boolean = false

    private var currentDurationMs: Long = 0L
    private var currentPositionMs: Long = 0L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentBitmap: Bitmap? = null
    private var currentThumbnailUrl: String = ""
    private var lastNotifiedPlaying: Boolean? = null
    private var lastNotifiedTitle: String = ""
    private var lastNotifiedChannel: String = ""

    private lateinit var networkConnectivityObserver: NetworkConnectivityObserver
    private var offlineTimeoutJob: Job? = null
    private var wasPlayingBeforeDisconnect = false

    private val audioManager: AudioManager? by lazy {
        getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    private var audioPlaybackCallback: AudioManager.AudioPlaybackCallback? = null
    private var audioModeChangedListener: Any? = null
    private var externalDebounceJob: Job? = null
    private var duckRecoveryDebounceJob: Job? = null
    private var pendingPausedMediaObserveJob: Job? = null
    private var externalInterruptionWatchJob: Job? = null

    private var externalWhilePlayingStartMs: Long = 0L
    private var hadSustainedExternalWhilePlaying: Boolean = false
    private var externalWhilePausedConfirmed: Boolean = false

    var onPlayAction: (() -> Unit)? = null
    var onPauseAction: (() -> Unit)? = null
    var onNextAction: (() -> Unit)? = null
    var onPreviousAction: (() -> Unit)? = null
    var onSeekAction: ((Long) -> Unit)? = null
    var onOfflineTimeoutAction: (() -> Unit)? = null
    var onExternalAudioDuckingEnded: (() -> Unit)? = null
    var onExternalAudioInterruptionDetected: (() -> Unit)? = null
    var onExternalAudioInterruptionEnded: (() -> Unit)? = null

    companion object {
        const val CHANNEL_ID = "phantom_media_playback"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.phantom.tube.ACTION_PLAY"
        const val ACTION_PAUSE = "com.phantom.tube.ACTION_PAUSE"
        const val ACTION_NEXT = "com.phantom.tube.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.phantom.tube.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.phantom.tube.ACTION_STOP"
    }

    inner class LocalBinder : Binder() {
        fun getService(): PhantomMediaService = this@PhantomMediaService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        try {
            com.phantom.tube.player.offline.OfflineAudioPlayerManager.stop(this)
        } catch (_: Exception) {}

        networkConnectivityObserver = NetworkConnectivityObserver(this)
        createNotificationChannel()
        setupMediaSession()
        startAudioEnvironmentMonitoring()
        observeNetworkConnectivity()
        startForegroundCompat(buildNotification())
    }

    private fun startAudioEnvironmentMonitoring() {
        val am = audioManager ?: return
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                evaluateAudioEnvironment(configs, am.mode)
            }
        }
        audioPlaybackCallback = callback
        am.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerAudioModeListenerApi31(am)
        }
    }

    private fun stopAudioEnvironmentMonitoring() {
        val am = audioManager ?: return
        audioPlaybackCallback?.let { cb ->
            try {
                am.unregisterAudioPlaybackCallback(cb)
            } catch (_: Exception) {}
        }
        audioPlaybackCallback = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            unregisterAudioModeListenerApi31(am)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerAudioModeListenerApi31(am: AudioManager) {
        val listener = AudioManager.OnModeChangedListener { newMode ->
            evaluateAudioEnvironment(am.activePlaybackConfigurations, newMode)
        }
        audioModeChangedListener = listener
        try {
            am.addOnModeChangedListener(mainExecutor, listener)
        } catch (_: Exception) {}
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterAudioModeListenerApi31(am: AudioManager) {
        val listener = audioModeChangedListener as? AudioManager.OnModeChangedListener
        if (listener != null) {
            try {
                am.removeOnModeChangedListener(listener)
            } catch (_: Exception) {}
        }
        audioModeChangedListener = null
    }

    private fun hasDedicatedExternalUsage(configs: List<AudioPlaybackConfiguration>): Boolean {
        for (cfg in configs) {
            when (cfg.audioAttributes.usage) {
                AudioAttributes.USAGE_VOICE_COMMUNICATION,
                AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
                AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
                AudioAttributes.USAGE_ALARM,
                AudioAttributes.USAGE_ASSISTANT -> return true
            }
        }
        return false
    }

    private fun countMediaLikeConfigs(configs: List<AudioPlaybackConfiguration>): Int {
        var count = 0
        for (cfg in configs) {
            when (cfg.audioAttributes.usage) {
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.USAGE_GAME,
                AudioAttributes.USAGE_UNKNOWN -> count++
            }
        }
        return count
    }

    private fun isAnyExternalAudioActiveWhenPaused(
        configs: List<AudioPlaybackConfiguration>,
        mode: Int
    ): Boolean {
        return mode != AudioManager.MODE_NORMAL ||
            hasDedicatedExternalUsage(configs) ||
            countMediaLikeConfigs(configs) >= 1
    }

    private fun isAnyExternalAudioActiveWhilePlaying(
        configs: List<AudioPlaybackConfiguration>,
        mode: Int
    ): Boolean {
        return mode != AudioManager.MODE_NORMAL ||
            hasDedicatedExternalUsage(configs) ||
            countMediaLikeConfigs(configs) >= 2
    }

    private fun startCallOrPauseWatch(shortWindowOnly: Boolean) {
        if (externalInterruptionWatchJob?.isActive == true) return
        externalInterruptionWatchJob = serviceScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            while (true) {
                delay(350L)
                val am = audioManager ?: break
                val configs = am.activePlaybackConfigurations
                val mode = am.mode
                if (isPlaying) break
                evaluateAudioEnvironment(configs, mode)
                val idleNow = !isAnyExternalAudioActiveWhenPaused(configs, mode)
                if (idleNow && (mode == AudioManager.MODE_NORMAL) &&
                    (!externalWhilePausedConfirmed || (shortWindowOnly && SystemClock.elapsedRealtime() - startedAt > 1800L))
                ) {
                    break
                }
                if (shortWindowOnly && !externalWhilePausedConfirmed && SystemClock.elapsedRealtime() - startedAt > 1800L) {
                    break
                }
            }
            externalInterruptionWatchJob = null
        }
    }

    private fun reEvaluateAudioEnvironment() {
        val am = audioManager ?: return
        evaluateAudioEnvironment(am.activePlaybackConfigurations, am.mode)
    }

    private fun evaluateAudioEnvironment(
        configs: List<AudioPlaybackConfiguration>,
        mode: Int
    ) {
        val now = SystemClock.elapsedRealtime()
        val isCallOrCommMode = (mode != AudioManager.MODE_NORMAL)
        val hasDedicatedExternal = hasDedicatedExternalUsage(configs)
        val mediaLikeCount = countMediaLikeConfigs(configs)

        if (isPlaying) {
            externalWhilePausedConfirmed = false
            pendingPausedMediaObserveJob?.cancel()
            pendingPausedMediaObserveJob = null
            externalDebounceJob?.cancel()
            externalDebounceJob = null
            externalInterruptionWatchJob?.cancel()
            externalInterruptionWatchJob = null

            val externalActive = isCallOrCommMode || hasDedicatedExternal || mediaLikeCount >= 2
            if (externalActive) {
                duckRecoveryDebounceJob?.cancel()
                duckRecoveryDebounceJob = null
                if (externalWhilePlayingStartMs == 0L) {
                    externalWhilePlayingStartMs = now
                }
                if (isCallOrCommMode || hasDedicatedExternal) {
                    hadSustainedExternalWhilePlaying = true
                }
            } else if (externalWhilePlayingStartMs > 0L) {
                val overlapDuration = now - externalWhilePlayingStartMs
                val shouldRecover = hadSustainedExternalWhilePlaying || overlapDuration >= 280L
                externalWhilePlayingStartMs = 0L
                hadSustainedExternalWhilePlaying = false
                if (shouldRecover) {
                    duckRecoveryDebounceJob?.cancel()
                    duckRecoveryDebounceJob = serviceScope.launch {
                        delay(400L)
                        val am = audioManager ?: return@launch
                        if (isPlaying && !isAnyExternalAudioActiveWhilePlaying(am.activePlaybackConfigurations, am.mode)) {
                            onExternalAudioDuckingEnded?.invoke()
                        }
                    }
                }
            }
        } else {
            duckRecoveryDebounceJob?.cancel()
            duckRecoveryDebounceJob = null

            val hadExternalOverlapBeforePause = hadSustainedExternalWhilePlaying ||
                (externalWhilePlayingStartMs > 0L && mediaLikeCount >= 1)
            externalWhilePlayingStartMs = 0L
            hadSustainedExternalWhilePlaying = false

            if (isCallOrCommMode || hasDedicatedExternal) {
                externalDebounceJob?.cancel()
                externalDebounceJob = null
                pendingPausedMediaObserveJob?.cancel()
                pendingPausedMediaObserveJob = null
                externalWhilePausedConfirmed = true
                onExternalAudioInterruptionDetected?.invoke()
                if (isCallOrCommMode) {
                    startCallOrPauseWatch(shortWindowOnly = false)
                }
            } else if (mediaLikeCount >= 1) {
                externalDebounceJob?.cancel()
                externalDebounceJob = null
                externalInterruptionWatchJob?.cancel()
                externalInterruptionWatchJob = null
                if (externalWhilePausedConfirmed || mediaLikeCount >= 2 || hadExternalOverlapBeforePause) {
                    externalWhilePausedConfirmed = true
                    pendingPausedMediaObserveJob?.cancel()
                    pendingPausedMediaObserveJob = null
                    onExternalAudioInterruptionDetected?.invoke()
                } else if (pendingPausedMediaObserveJob?.isActive != true) {
                    pendingPausedMediaObserveJob = serviceScope.launch {
                        delay(220L)
                        val am = audioManager ?: return@launch
                        if (!isPlaying && countMediaLikeConfigs(am.activePlaybackConfigurations) >= 1) {
                            externalWhilePausedConfirmed = true
                            onExternalAudioInterruptionDetected?.invoke()
                        }
                    }
                }
            } else {
                pendingPausedMediaObserveJob?.cancel()
                pendingPausedMediaObserveJob = null
                if (!isCallOrCommMode) {
                    externalInterruptionWatchJob?.cancel()
                    externalInterruptionWatchJob = null
                }
                if (externalWhilePausedConfirmed && externalDebounceJob?.isActive != true) {
                    externalDebounceJob = serviceScope.launch {
                        delay(650L)
                        val am = audioManager ?: return@launch
                        if (!isPlaying && !isAnyExternalAudioActiveWhenPaused(am.activePlaybackConfigurations, am.mode)) {
                            externalWhilePausedConfirmed = false
                            onExternalAudioInterruptionEnded?.invoke()
                        }
                    }
                }
            }
        }
    }

    fun isExternalAudioCurrentlyActive(): Boolean {
        val am = audioManager ?: return false
        val configs = am.activePlaybackConfigurations
        return am.mode != AudioManager.MODE_NORMAL ||
            hasDedicatedExternalUsage(configs) ||
            externalWhilePausedConfirmed ||
            hadSustainedExternalWhilePlaying ||
            (externalWhilePlayingStartMs > 0L) ||
            (!isPlaying && countMediaLikeConfigs(configs) >= 2)
    }

    fun onUnexpectedOnlinePause() {
        reEvaluateAudioEnvironment()
        if (!isPlaying && !externalWhilePausedConfirmed) {
            startCallOrPauseWatch(shortWindowOnly = true)
        }
    }

    fun clearExternalInterruptionState() {
        externalDebounceJob?.cancel()
        externalDebounceJob = null
        duckRecoveryDebounceJob?.cancel()
        duckRecoveryDebounceJob = null
        pendingPausedMediaObserveJob?.cancel()
        pendingPausedMediaObserveJob = null
        externalInterruptionWatchJob?.cancel()
        externalInterruptionWatchJob = null
        externalWhilePlayingStartMs = 0L
        hadSustainedExternalWhilePlaying = false
        externalWhilePausedConfirmed = false
    }

    private fun showNoInternetToast() {
        serviceScope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(
                this@PhantomMediaService,
                R.string.player_no_internet_notice,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeNetworkConnectivity() {
        serviceScope.launch {
            networkConnectivityObserver.isConnectedFlow.collect { connected ->
                if (!connected) {
                    if (offlineTimeoutJob == null || offlineTimeoutJob?.isActive == false) {
                        offlineTimeoutJob = serviceScope.launch {
                            delay(5 * 60 * 1000L) // 5 minutes timeout
                            handleOfflineTimeout()
                        }
                    }
                } else {
                    offlineTimeoutJob?.cancel()
                    offlineTimeoutJob = null
                    if (wasPlayingBeforeDisconnect) {
                        wasPlayingBeforeDisconnect = false
                        onPlayAction?.invoke()
                    }
                }
            }
        }
    }

    private fun handleOfflineTimeout() {
        serviceScope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(
                this@PhantomMediaService,
                R.string.player_stopped_offline_timeout,
                android.widget.Toast.LENGTH_LONG
            ).show()
            onPauseAction?.invoke()
            onOfflineTimeoutAction?.invoke()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.settings_bg_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.settings_bg_playback_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "PhantomMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!networkConnectivityObserver.isConnected()) {
                        showNoInternetToast()
                        return
                    }
                    onPlayAction?.invoke()
                }

                override fun onPause() {
                    wasPlayingBeforeDisconnect = false
                    onPauseAction?.invoke()
                }

                override fun onSkipToNext() {
                    if (!networkConnectivityObserver.isConnected()) {
                        showNoInternetToast()
                        return
                    }
                    onNextAction?.invoke()
                }

                override fun onSkipToPrevious() {
                    if (!networkConnectivityObserver.isConnected()) {
                        showNoInternetToast()
                        return
                    }
                    onPreviousAction?.invoke()
                }

                override fun onSeekTo(pos: Long) {
                    onSeekAction?.invoke(pos)
                }

                override fun onStop() {
                    wasPlayingBeforeDisconnect = false
                    onPauseAction?.invoke()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    fun updateMediaInfo(
        title: String,
        channel: String,
        durationMs: Long = 0L,
        playing: Boolean,
        thumbnailUrl: String = "",
        currentPositionMs: Long = -1L
    ) {
        val metaChanged = (title != currentTitle || channel != currentChannel)
        currentTitle = title
        currentChannel = channel
        isPlaying = playing
        if (durationMs > 0L) {
            currentDurationMs = durationMs
        }
        if (currentPositionMs >= 0L) {
            this.currentPositionMs = currentPositionMs
        }

        if (thumbnailUrl.isNotBlank() && thumbnailUrl != currentThumbnailUrl) {
            currentThumbnailUrl = thumbnailUrl
            currentBitmap = null
            loadThumbnail(thumbnailUrl)
        } else {
            applyMetadata(currentBitmap)
            updatePlaybackState(playing, this.currentPositionMs, forceNotify = metaChanged)
        }
    }

    fun updateDuration(durationMs: Long, currentPositionMs: Long = -1L) {
        if (currentPositionMs >= 0L) {
            this.currentPositionMs = currentPositionMs
        }
        if (durationMs > 0L && durationMs != currentDurationMs) {
            currentDurationMs = durationMs
            applyMetadata(currentBitmap)
            updatePlaybackState(isPlaying, this.currentPositionMs, forceNotify = false)
        }
    }

    private fun loadThumbnail(url: String) {
        applyMetadata(null)
        updatePlaybackState(isPlaying, this.currentPositionMs, forceNotify = true)

        serviceScope.launch(Dispatchers.IO) {
            try {
                val imageRequest = ImageRequest.Builder(this@PhantomMediaService)
                    .data(url)
                    .size(160, 90)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .allowRgb565(true)
                    .allowHardware(false)
                    .build()
                val result = (imageLoader.execute(imageRequest) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap
                if (bitmap != null && !bitmap.isRecycled) {
                    withContext(Dispatchers.Main) {
                        if (currentThumbnailUrl == url) {
                            currentBitmap = bitmap
                            applyMetadata(bitmap)
                            lastNotifiedPlaying = isPlaying
                            lastNotifiedTitle = currentTitle
                            lastNotifiedChannel = currentChannel
                            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.notify(NOTIFICATION_ID, buildNotification())
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun applyMetadata(bitmap: Bitmap?) {
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentChannel)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, currentDurationMs)

        if (bitmap != null && !bitmap.isRecycled) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
        }
        mediaSession?.setMetadata(metadataBuilder.build())
    }

    fun updatePlaybackState(
        playing: Boolean,
        currentPositionMs: Long = -1L,
        forceNotify: Boolean = false,
        isBuffering: Boolean = false
    ) {
        val effectivelyPlaying = playing && !isBuffering
        if (isBuffering && isPlaying && !networkConnectivityObserver.isConnected()) {
            wasPlayingBeforeDisconnect = true
        } else if (effectivelyPlaying || (!isBuffering && !playing)) {
            wasPlayingBeforeDisconnect = false
        }

        val stateChanged = (lastNotifiedPlaying != effectivelyPlaying) ||
            (lastNotifiedTitle != currentTitle) ||
            (lastNotifiedChannel != currentChannel) ||
            forceNotify

        val prevPlaying = isPlaying
        isPlaying = effectivelyPlaying
        if (currentPositionMs >= 0L) {
            this.currentPositionMs = currentPositionMs
        }
        if (isPlaying) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
        if (prevPlaying != isPlaying) {
            reEvaluateAudioEnvironment()
        }

        val state = when {
            isBuffering -> PlaybackState.STATE_BUFFERING
            effectivelyPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val playbackSpeed = if (effectivelyPlaying) 1.0f else 0.0f
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO
            )
            .setState(state, this.currentPositionMs, playbackSpeed, SystemClock.elapsedRealtime())
            .build()
        mediaSession?.setPlaybackState(playbackState)

        if (stateChanged) {
            lastNotifiedPlaying = effectivelyPlaying
            lastNotifiedTitle = currentTitle
            lastNotifiedChannel = currentChannel
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_previous),
            getString(R.string.player_prev_video),
            getServicePendingIntent(ACTION_PREVIOUS)
        ).build()

        val playPauseAction = if (isPlaying) {
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                getString(R.string.player_pause),
                getServicePendingIntent(ACTION_PAUSE)
            ).build()
        } else {
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_play),
                getString(R.string.player_play),
                getServicePendingIntent(ACTION_PLAY)
            ).build()
        }

        val nextAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_next),
            getString(R.string.player_next_video),
            getServicePendingIntent(ACTION_NEXT)
        ).build()

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentChannel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(isPlaying)
            .setLargeIcon(currentBitmap)

        return builder.build()
    }

    private fun getServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PhantomMediaService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Phantom:PlaybackWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours safety timeout
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                if (!networkConnectivityObserver.isConnected()) {
                    showNoInternetToast()
                } else {
                    onPlayAction?.invoke()
                }
            }
            ACTION_PAUSE -> {
                wasPlayingBeforeDisconnect = false
                onPauseAction?.invoke()
            }
            ACTION_NEXT -> {
                if (!networkConnectivityObserver.isConnected()) {
                    showNoInternetToast()
                } else {
                    onNextAction?.invoke()
                }
            }
            ACTION_PREVIOUS -> {
                if (!networkConnectivityObserver.isConnected()) {
                    showNoInternetToast()
                } else {
                    onPreviousAction?.invoke()
                }
            }
            ACTION_STOP -> {
                wasPlayingBeforeDisconnect = false
                onPauseAction?.invoke()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAudioEnvironmentMonitoring()
        clearExternalInterruptionState()
        offlineTimeoutJob?.cancel()
        offlineTimeoutJob = null
        serviceScope.cancel()
        currentBitmap = null
        releaseWakeLock()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
