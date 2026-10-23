package com.phantom.tube.player.offline

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
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.phantom.tube.MainActivity
import com.phantom.tube.R
import com.phantom.tube.core.database.DownloadEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * Service latar depan (Foreground Service) untuk memutar audio MP3 hasil unduhan secara offline.
 * Mendukung pemutaran terus menerus (auto-next playlist queue), MediaSession di status bar & lockscreen,
 * dan background audio dengan CPU WakeLock saat layar ponsel dimatikan.
 */
class PhantomOfflineAudioService : Service(), AudioManager.OnAudioFocusChangeListener {

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null
    private var currentBitmap: Bitmap? = null
    private var lastThumbnailUrl: String = ""

    companion object {
        const val CHANNEL_ID = "phantom_offline_audio_playback"
        const val NOTIFICATION_ID = 2002

        const val ACTION_PLAY_QUEUE = "com.phantom.tube.offline.PLAY_QUEUE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.phantom.tube.offline.TOGGLE_PLAY_PAUSE"
        const val ACTION_PLAY = "com.phantom.tube.offline.PLAY"
        const val ACTION_PAUSE = "com.phantom.tube.offline.PAUSE"
        const val ACTION_NEXT = "com.phantom.tube.offline.NEXT"
        const val ACTION_PREVIOUS = "com.phantom.tube.offline.PREVIOUS"
        const val ACTION_SEEK = "com.phantom.tube.offline.SEEK"
        const val ACTION_STOP = "com.phantom.tube.offline.STOP"
        const val ACTION_UPDATE_LOOP = "com.phantom.tube.offline.UPDATE_LOOP"
        const val ACTION_UPDATE_SHUFFLE = "com.phantom.tube.offline.UPDATE_SHUFFLE"

        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_LOOP_MODE = "extra_loop_mode"
        const val EXTRA_SHUFFLE_MODE = "extra_shuffle_mode"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Hentikan service pemutar online jika sedang jalan agar audio tidak dobel
        try {
            stopService(Intent(this, com.phantom.tube.player.service.PhantomMediaService::class.java))
        } catch (_: Exception) {}

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        createNotificationChannel()
        setupMediaSession()
        acquireWakeLock()

        // Critical: Must call startForeground immediately in onCreate to satisfy Android ForegroundService requirement
        val initialTrack = OfflineAudioPlayerManager.currentTrack.value
        startForegroundCompat(buildNotification(initialTrack, isPlaying = false))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentTrack = OfflineAudioPlayerManager.currentTrack.value
        startForegroundCompat(buildNotification(currentTrack, isPlaying = mediaPlayer?.isPlaying ?: false))

        when (intent?.action) {
            ACTION_PLAY_QUEUE -> {
                val index = intent.getIntExtra(EXTRA_INDEX, 0)
                playTrackAtIndex(index)
            }
            ACTION_TOGGLE_PLAY_PAUSE -> {
                togglePlayPause()
            }
            ACTION_PLAY -> {
                resumePlayback()
            }
            ACTION_PAUSE -> {
                pausePlayback()
            }
            ACTION_NEXT -> {
                playNext()
            }
            ACTION_PREVIOUS -> {
                playPrevious()
            }
            ACTION_SEEK -> {
                val pos = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
                seekTo(pos)
            }
            ACTION_UPDATE_LOOP -> {
                val mode = OfflineAudioPlayerManager.repeatMode.value
                mediaPlayer?.isLooping = (mode == OfflineRepeatMode.ONE)
            }
            ACTION_UPDATE_SHUFFLE -> {
                // Status shuffle dikelola di OfflineAudioPlayerManager
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pemutar Musik Offline Phantom",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kontrol pemutar musik offline latar belakang Phantom"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "PhantomOfflineSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    resumePlayback()
                }

                override fun onPause() {
                    pausePlayback()
                }

                override fun onSkipToNext() {
                    playNext()
                }

                override fun onSkipToPrevious() {
                    playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos)
                }

                override fun onStop() {
                    this@PhantomOfflineAudioService.stopSelf()
                }
            })
            isActive = true
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Phantom:OfflineAudioWakeLock")?.apply {
            setReferenceCounted(false)
        }
    }

    private fun playTrackAtIndex(index: Int) {
        val queue = OfflineAudioPlayerManager.queue.value
        if (queue.isEmpty() || index !in queue.indices) return

        val track = queue[index]
        OfflineAudioPlayerManager.updateTrack(track, index)

        // Update foreground notification immediately before async preparation
        startForegroundCompat(buildNotification(track, isPlaying = true))
        loadThumbnailBitmap(track.thumbnailUrl)

        try {
            releasePlayer()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                // Prioritaskan Content Uri Scoped Storage, lalu fallback ke File path
                var prepared = false
                if (track.fileUri.isNotBlank()) {
                    try {
                        setDataSource(this@PhantomOfflineAudioService, Uri.parse(track.fileUri))
                        prepared = true
                    } catch (_: Exception) {}
                }
                if (!prepared && track.filePath.isNotBlank()) {
                    val file = File(track.filePath)
                    if (file.exists()) {
                        setDataSource(file.absolutePath)
                        prepared = true
                    }
                }

                if (!prepared) {
                    // Coba jalur umum MediaStore Scoped Storage
                    setDataSource(this@PhantomOfflineAudioService, Uri.parse(track.fileUri))
                }

                setOnPreparedListener { mp ->
                    mp.start()
                    val dur = mp.duration.toLong().coerceAtLeast(0L)
                    OfflineAudioPlayerManager.updateDuration(dur)
                    OfflineAudioPlayerManager.updatePlaybackState(true)
                    mp.isLooping = (OfflineAudioPlayerManager.repeatMode.value == OfflineRepeatMode.ONE)
                    wakeLock?.acquire(dur + 60000L)
                    startProgressTracker()
                    applyMetadata(track, currentBitmap)
                    updateMediaSessionState(true, 0L)
                    updateNotification(track, true)
                }

                setOnCompletionListener {
                    if (OfflineAudioPlayerManager.repeatMode.value != OfflineRepeatMode.ONE) {
                        playNext()
                    }
                }

                setOnErrorListener { _, _, _ ->
                    OfflineAudioPlayerManager.updatePlaybackState(false)
                    stopProgressTracker()
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            OfflineAudioPlayerManager.updatePlaybackState(false)
        }
    }

    private fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun resumePlayback() {
        val mp = mediaPlayer ?: return
        if (!mp.isPlaying) {
            mp.start()
            OfflineAudioPlayerManager.updatePlaybackState(true)
            startProgressTracker()
            val track = OfflineAudioPlayerManager.currentTrack.value
            if (track != null) {
                updateMediaSessionState(true, mp.currentPosition.toLong())
                updateNotification(track, true)
            }
        }
    }

    private fun pausePlayback() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            OfflineAudioPlayerManager.updatePlaybackState(false)
            stopProgressTracker()
            val track = OfflineAudioPlayerManager.currentTrack.value
            if (track != null) {
                updateMediaSessionState(false, mp.currentPosition.toLong())
                updateNotification(track, false)
            }
        }
    }

    private fun playNext() {
        val queue = OfflineAudioPlayerManager.queue.value
        if (queue.isEmpty()) return

        val currentIndex = OfflineAudioPlayerManager.currentIndex.value
        val isShuffle = OfflineAudioPlayerManager.isShuffle.value
        val repeatMode = OfflineAudioPlayerManager.repeatMode.value

        val nextIndex = when {
            repeatMode == OfflineRepeatMode.ONE -> currentIndex
            isShuffle && queue.size > 1 -> {
                var randomIdx = Random.nextInt(queue.size)
                while (randomIdx == currentIndex && queue.size > 1) {
                    randomIdx = Random.nextInt(queue.size)
                }
                randomIdx
            }
            currentIndex < queue.lastIndex -> currentIndex + 1
            repeatMode == OfflineRepeatMode.ALL -> 0
            else -> {
                // Selesai seluruh antrean
                pausePlayback()
                seekTo(0L)
                return
            }
        }

        playTrackAtIndex(nextIndex)
    }

    private fun playPrevious() {
        val mp = mediaPlayer
        if (mp != null && mp.currentPosition > 3000) {
            // Jika sudah lewat 3 detik, ulangi dari awal lagu
            seekTo(0L)
            return
        }

        val queue = OfflineAudioPlayerManager.queue.value
        if (queue.isEmpty()) return

        val currentIndex = OfflineAudioPlayerManager.currentIndex.value
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else queue.lastIndex
        playTrackAtIndex(prevIndex)
    }

    private fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        mp.seekTo(positionMs.toInt())
        OfflineAudioPlayerManager.updatePosition(positionMs)
        updateMediaSessionState(mp.isPlaying, positionMs)
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = serviceScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition.toLong()
                        OfflineAudioPlayerManager.updatePosition(pos)
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun loadThumbnailBitmap(url: String) {
        if (url.isBlank() || url == lastThumbnailUrl) return
        lastThumbnailUrl = url
        currentBitmap = null

        serviceScope.launch(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(this@PhantomOfflineAudioService)
                    .data(url)
                    .size(160, 90)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .allowRgb565(true)
                    .allowHardware(false)
                    .build()
                val result = (imageLoader.execute(req) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap
                if (bitmap != null && !bitmap.isRecycled) {
                    withContext(Dispatchers.Main) {
                        if (lastThumbnailUrl == url) {
                            currentBitmap = bitmap
                            val track = OfflineAudioPlayerManager.currentTrack.value
                            if (track != null) {
                                applyMetadata(track, bitmap)
                                val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                notifManager.notify(NOTIFICATION_ID, buildNotification(track, mediaPlayer?.isPlaying ?: false))
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyMetadata(track: DownloadEntity, bitmap: Bitmap?) {
        val dur = OfflineAudioPlayerManager.durationMs.value.takeIf { it > 0 }
            ?: mediaPlayer?.duration?.toLong()?.coerceAtLeast(0L)
            ?: 0L
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.channelTitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, dur)

        if (bitmap != null && !bitmap.isRecycled) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
        }
        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun updateMediaSessionState(isPlaying: Boolean, positionMs: Long) {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO

        val playbackSpeed = if (isPlaying) 1.0f else 0.0f
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, positionMs, playbackSpeed, SystemClock.elapsedRealtime())
                .build()
        )
    }

    private fun buildNotification(track: DownloadEntity? = null, isPlaying: Boolean = false): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PhantomOfflineAudioService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PhantomOfflineAudioService::class.java).apply { action = playPauseAction },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, PhantomOfflineAudioService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, PhantomOfflineAudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevActionObj = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_previous),
            "Previous",
            prevIntent
        ).build()

        val playPauseActionObj = Notification.Action.Builder(
            Icon.createWithResource(this, playPauseIcon),
            if (isPlaying) "Pause" else "Play",
            playPauseIntent
        ).build()

        val nextActionObj = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_next),
            "Next",
            nextIntent
        ).build()

        val stopActionObj = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop",
            stopIntent
        ).build()

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(track?.title ?: "Phantom Player")
            .setContentText(track?.channelTitle?.ifBlank { "Musik Offline" } ?: "Musik Offline")
            .setSubText("Musik Offline")
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(prevActionObj)
            .addAction(playPauseActionObj)
            .addAction(nextActionObj)
            .addAction(stopActionObj)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (currentBitmap != null) {
            builder.setLargeIcon(currentBitmap)
        }

        return builder.build()
    }

    private fun updateNotification(track: DownloadEntity? = null, isPlaying: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(track, isPlaying))
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> pausePlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pausePlayback()
            AudioManager.AUDIOFOCUS_GAIN -> resumePlayback()
        }
    }

    override fun onDestroy() {
        stopProgressTracker()
        releasePlayer()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        mediaSession?.release()
        serviceScope.cancel()
        OfflineAudioPlayerManager.updatePlaybackState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
