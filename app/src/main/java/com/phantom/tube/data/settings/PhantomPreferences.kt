package com.phantom.tube.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Pengelola preferensi aplikasi Phantom Tube (Single Source of Truth).
 * Menyimpan konfigurasi pengguna secara persisten menggunakan SharedPreferences
 * dan mengeksposnya sebagai StateFlow reaktif untuk Jetpack Compose.
 */
class PhantomPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==========================================
    // 1. PEMUTARAN & KUALITAS (PLAYBACK)
    // ==========================================

    private val _defaultQuality = MutableStateFlow(
        prefs.getString(KEY_DEFAULT_QUALITY, "auto") ?: "auto"
    )
    val defaultQuality: StateFlow<String> = _defaultQuality.asStateFlow()

    fun setDefaultQuality(quality: String) {
        _defaultQuality.value = quality
        prefs.edit().putString(KEY_DEFAULT_QUALITY, quality).apply()
    }

    private val _defaultSpeed = MutableStateFlow(
        prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f)
    )
    val defaultSpeed: StateFlow<Float> = _defaultSpeed.asStateFlow()

    fun setDefaultSpeed(speed: Float) {
        _defaultSpeed.value = speed
        prefs.edit().putFloat(KEY_DEFAULT_SPEED, speed).apply()
    }

    private val _doubleTapSeekSeconds = MutableStateFlow(
        prefs.getInt(KEY_DOUBLE_TAP_SEEK_SECONDS, 10)
    )
    val doubleTapSeekSeconds: StateFlow<Int> = _doubleTapSeekSeconds.asStateFlow()

    fun setDoubleTapSeekSeconds(seconds: Int) {
        _doubleTapSeekSeconds.value = seconds
        prefs.edit().putInt(KEY_DOUBLE_TAP_SEEK_SECONDS, seconds).apply()
    }

    private val _backgroundPlaybackEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BG_PLAYBACK, true)
    )
    val backgroundPlaybackEnabled: StateFlow<Boolean> = _backgroundPlaybackEnabled.asStateFlow()

    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        _backgroundPlaybackEnabled.value = enabled
        prefs.edit().putBoolean(KEY_BG_PLAYBACK, enabled).apply()
    }

    private val _autoPipEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_PIP, true)
    )
    val autoPipEnabled: StateFlow<Boolean> = _autoPipEnabled.asStateFlow()

    fun setAutoPipEnabled(enabled: Boolean) {
        _autoPipEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_PIP, enabled).apply()
    }

    // ==========================================
    // 2. SPONSORBLOCK (ANTI-IKLAN & SEGMEN)
    // ==========================================

    private val _sponsorBlockEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SPONSORBLOCK_ENABLED, true)
    )
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    fun setSponsorBlockEnabled(enabled: Boolean) {
        _sponsorBlockEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SPONSORBLOCK_ENABLED, enabled).apply()
    }

    private val _sponsorBlockAutoSkip = MutableStateFlow(
        prefs.getBoolean(KEY_SPONSORBLOCK_AUTO_SKIP, true)
    )
    val sponsorBlockAutoSkip: StateFlow<Boolean> = _sponsorBlockAutoSkip.asStateFlow()

    fun setSponsorBlockAutoSkip(autoSkip: Boolean) {
        _sponsorBlockAutoSkip.value = autoSkip
        prefs.edit().putBoolean(KEY_SPONSORBLOCK_AUTO_SKIP, autoSkip).apply()
    }

    private val _skipSponsor = MutableStateFlow(prefs.getBoolean(KEY_SKIP_SPONSOR, true))
    val skipSponsor: StateFlow<Boolean> = _skipSponsor.asStateFlow()
    fun setSkipSponsor(enabled: Boolean) {
        _skipSponsor.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_SPONSOR, enabled).apply()
    }

    private val _skipSelfPromo = MutableStateFlow(prefs.getBoolean(KEY_SKIP_SELFPROMO, true))
    val skipSelfPromo: StateFlow<Boolean> = _skipSelfPromo.asStateFlow()
    fun setSkipSelfPromo(enabled: Boolean) {
        _skipSelfPromo.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_SELFPROMO, enabled).apply()
    }

    private val _skipInteraction = MutableStateFlow(prefs.getBoolean(KEY_SKIP_INTERACTION, true))
    val skipInteraction: StateFlow<Boolean> = _skipInteraction.asStateFlow()
    fun setSkipInteraction(enabled: Boolean) {
        _skipInteraction.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_INTERACTION, enabled).apply()
    }

    private val _skipIntro = MutableStateFlow(prefs.getBoolean(KEY_SKIP_INTRO, true))
    val skipIntro: StateFlow<Boolean> = _skipIntro.asStateFlow()
    fun setSkipIntro(enabled: Boolean) {
        _skipIntro.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_INTRO, enabled).apply()
    }

    private val _skipOutro = MutableStateFlow(prefs.getBoolean(KEY_SKIP_OUTRO, true))
    val skipOutro: StateFlow<Boolean> = _skipOutro.asStateFlow()
    fun setSkipOutro(enabled: Boolean) {
        _skipOutro.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_OUTRO, enabled).apply()
    }

    // ==========================================
    // 3. BAHASA & WILAYAH (LANGUAGE & REGION)
    // ==========================================

    private val initialLanguage: String = if (prefs.contains(KEY_APP_LANGUAGE)) {
        prefs.getString(KEY_APP_LANGUAGE, "en") ?: "en"
    } else {
        val detected = com.phantom.tube.core.util.LocaleHelper.resolveDefaultLanguage()
        prefs.edit().putString(KEY_APP_LANGUAGE, detected).apply()
        detected
    }

    private val _appLanguage = MutableStateFlow(initialLanguage)
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    fun setAppLanguage(langCode: String) {
        _appLanguage.value = langCode
        prefs.edit().putString(KEY_APP_LANGUAGE, langCode).apply()
    }

    private val initialCountry: String = if (prefs.contains(KEY_CONTENT_COUNTRY)) {
        prefs.getString(KEY_CONTENT_COUNTRY, "GLOBAL") ?: "GLOBAL"
    } else {
        val detected = com.phantom.tube.core.util.RegionHelper.resolveDefaultCountry()
        prefs.edit().putString(KEY_CONTENT_COUNTRY, detected).apply()
        detected
    }

    private val _contentCountry = MutableStateFlow(initialCountry)
    val contentCountry: StateFlow<String> = _contentCountry.asStateFlow()

    fun setContentCountry(countryCode: String) {
        _contentCountry.value = countryCode
        prefs.edit().putString(KEY_CONTENT_COUNTRY, countryCode).apply()
    }

    // ==========================================
    // 4. PRIVASI & RIWAYAT (PRIVACY)
    // ==========================================

    private val _pauseWatchHistory = MutableStateFlow(
        prefs.getBoolean(KEY_PAUSE_WATCH_HISTORY, false)
    )
    val pauseWatchHistory: StateFlow<Boolean> = _pauseWatchHistory.asStateFlow()

    fun setPauseWatchHistory(paused: Boolean) {
        _pauseWatchHistory.value = paused
        prefs.edit().putBoolean(KEY_PAUSE_WATCH_HISTORY, paused).apply()
    }

    // ==========================================
    // 5. CACHE MANAGEMENT
    // ==========================================

    fun getCacheSizeBytes(context: Context): Long {
        var size = 0L
        try {
            val appCacheDir = context.cacheDir
            if (appCacheDir != null && appCacheDir.isDirectory) {
                size += getFolderSize(appCacheDir)
            }
            val externalCache = context.externalCacheDir
            if (externalCache != null && externalCache.isDirectory) {
                size += getFolderSize(externalCache)
            }
        } catch (_: Exception) {}
        return size
    }

    fun clearAppCache(context: Context) {
        try {
            context.cacheDir?.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()
        } catch (_: Exception) {}
    }

    private fun getFolderSize(file: File): Long {
        var size = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            size += if (child.isDirectory) getFolderSize(child) else child.length()
        }
        return size
    }

    companion object {
        private const val PREFS_NAME = "phantom_user_preferences"

        private const val KEY_DEFAULT_QUALITY = "pref_default_quality"
        private const val KEY_DEFAULT_SPEED = "pref_default_speed"
        private const val KEY_DOUBLE_TAP_SEEK_SECONDS = "pref_double_tap_seek_seconds"
        private const val KEY_BG_PLAYBACK = "pref_bg_playback"
        private const val KEY_AUTO_PIP = "pref_auto_pip"

        private const val KEY_SPONSORBLOCK_ENABLED = "pref_sb_enabled"
        private const val KEY_SPONSORBLOCK_AUTO_SKIP = "pref_sb_auto_skip"
        private const val KEY_SKIP_SPONSOR = "pref_skip_sponsor"
        private const val KEY_SKIP_SELFPROMO = "pref_skip_selfpromo"
        private const val KEY_SKIP_INTERACTION = "pref_skip_interaction"
        private const val KEY_SKIP_INTRO = "pref_skip_intro"
        private const val KEY_SKIP_OUTRO = "pref_skip_outro"

        private const val KEY_APP_LANGUAGE = "pref_app_language"
        private const val KEY_CONTENT_COUNTRY = "pref_content_country"
        private const val KEY_PAUSE_WATCH_HISTORY = "pref_pause_watch_history"
    }
}
