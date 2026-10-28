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
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhantomMediaService : Service(), AudioManager.OnAudioFocusChangeListener {

    private val binder = LocalBinder()
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain: Boolean = false
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

    var onPlayAction: (() -> Unit)? = null
    var onPauseAction: (() -> Unit)? = null
    var onNextAction: (() -> Unit)? = null
    var onPreviousAction: (() -> Unit)? = null
    var onSeekAction: ((Long) -> Unit)? = null

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

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        createNotificationChannel()
        setupMediaSession()
        startForegroundCompat(buildNotification())
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
                    onPlayAction?.invoke()
                }

                override fun onPause() {
                    onPauseAction?.invoke()
                }

                override fun onSkipToNext() {
                    onNextAction?.invoke()
                }

                override fun onSkipToPrevious() {
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
            requestAudioFocus()
            acquireWakeLock()
        } else {
            releaseWakeLock()
            if (!resumeOnFocusGain) {
                abandonAudioFocus()
            }
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
            ACTION_PLAY -> onPlayAction?.invoke()
            ACTION_PAUSE -> onPauseAction?.invoke()
            ACTION_NEXT -> onNextAction?.invoke()
            ACTION_PREVIOUS -> onPreviousAction?.invoke()
            ACTION_STOP -> {
                onPauseAction?.invoke()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
                .also { audioFocusRequest = it }
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { req ->
                try {
                    am.abandonAudioFocusRequest(req)
                } catch (_: Exception) {}
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                am.abandonAudioFocus(this)
            } catch (_: Exception) {}
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                onPauseAction?.invoke()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying) {
                    resumeOnFocusGain = true
                    onPauseAction?.invoke()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    onPlayAction?.invoke()
                }
            }
        }
    }

    override fun onDestroy() {
        abandonAudioFocus()
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
