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
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
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

    var onPlayAction: (() -> Unit)? = null
    var onPauseAction: (() -> Unit)? = null
    var onNextAction: (() -> Unit)? = null
    var onPreviousAction: (() -> Unit)? = null
    var onSeekAction: ((Long) -> Unit)? = null
    var onOfflineTimeoutAction: (() -> Unit)? = null

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
        observeNetworkConnectivity()
        startForegroundCompat(buildNotification())
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
                    if (isPlaying) {
                        wasPlayingBeforeDisconnect = true
                    }
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
                "Phantom Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls and notifications for Phantom"
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

    fun updatePlaybackState(playing: Boolean, currentPositionMs: Long = -1L, forceNotify: Boolean = false) {
        val stateChanged = (lastNotifiedPlaying != playing) ||
            (lastNotifiedTitle != currentTitle) ||
            (lastNotifiedChannel != currentChannel) ||
            forceNotify

        isPlaying = playing
        if (currentPositionMs >= 0L) {
            this.currentPositionMs = currentPositionMs
        }
        if (playing) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }

        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val playbackSpeed = if (playing) 1.0f else 0.0f
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
            lastNotifiedPlaying = playing
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
            "Previous",
            getServicePendingIntent(ACTION_PREVIOUS)
        ).build()

        val playPauseAction = if (isPlaying) {
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                "Pause",
                getServicePendingIntent(ACTION_PAUSE)
            ).build()
        } else {
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_play),
                "Play",
                getServicePendingIntent(ACTION_PLAY)
            ).build()
        }

        val nextAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_next),
            "Next",
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
            ACTION_PAUSE -> onPauseAction?.invoke()
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
