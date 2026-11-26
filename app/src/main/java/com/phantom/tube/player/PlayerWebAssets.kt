package com.phantom.tube.player

import com.phantom.tube.core.config.PhantomConfig

/**
 * In-memory HTML, CSS, and JavaScript payloads injected into the WebView player
 * by [PhantomRenderEngine].
 */
object PlayerWebAssets {

    fun getHtml(): String {
        return if (PhantomConfig.isLocked()) LOCKED_ENGINE_HTML else RAW_ENGINE_HTML
    }

    fun getCssRules(): String {
        return if (PhantomConfig.isLocked()) "" else RAW_CSS_RULES
    }

    fun getCleanEngineScript(): String {
        return if (PhantomConfig.isLocked()) "" else RAW_CLEAN_SCRIPT
    }

    private const val LOCKED_ENGINE_HTML = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <title>Pembaruan Diperlukan</title>
    <style>
        body {
            background-color: #0F0F0F;
            color: #FFFFFF;
            display: flex;
            align-items: center;
            justify-content: center;
            height: 100vh;
            margin: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            text-align: center;
            padding: 24px;
            box-sizing: border-box;
        }
        .box {
            background: #1B1B1E;
            border-radius: 16px;
            padding: 28px 20px;
            max-width: 320px;
            border: 1px solid #333;
        }
        h2 { color: #FF0000; margin: 0 0 10px 0; font-size: 19px; }
        p { color: #AAAAAA; font-size: 13px; line-height: 1.5; margin: 0; }
    </style>
</head>
<body>
    <div class="box">
        <h2>Pembaruan Diperlukan</h2>
        <p>Versi aplikasi ini sudah tidak didukung atau integritas aplikasi telah dimodifikasi.<br><br>Silakan perbarui ke versi resmi untuk melanjutkan.</p>
    </div>
</body>
</html>
"""

    private const val RAW_ENGINE_HTML = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Phantom Engine</title>
    <style>
        html, body {
            margin: 0;
            padding: 0;
            width: 100vw;
            height: 100vh;
            background-color: #000000;
            overflow: hidden;
            -webkit-user-select: none;
            user-select: none;
            -webkit-tap-highlight-color: transparent;
        }
        #player, iframe {
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            right: 0 !important;
            bottom: 0 !important;
            width: 100vw !important;
            height: 100vh !important;
            border: 0 !important;
            outline: 0 !important;
            pointer-events: none;
        }
        body.phantom-collapsed #player,
        body.phantom-collapsed iframe {
            width: 1px !important;
            height: 1px !important;
            right: auto !important;
            bottom: auto !important;
            overflow: hidden !important;
            contain: strict !important;
        }
    </style>
    <script>
        try {
            Object.defineProperty(document, 'hidden', {
                get: function() { return false; },
                configurable: true
            });
            Object.defineProperty(document, 'visibilityState', {
                get: function() { return 'visible'; },
                configurable: true
            });
            Object.defineProperty(document, 'webkitVisibilityState', {
                get: function() { return 'visible'; },
                configurable: true
            });
            Object.defineProperty(document, 'webkitHidden', {
                get: function() { return false; },
                configurable: true
            });
        } catch(e) {}

        window.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
        document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
        window.addEventListener('webkitvisibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
        document.addEventListener('webkitvisibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
        window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
        window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);
        window.addEventListener('freeze', function(e) { e.stopImmediatePropagation(); }, true);
    </script>
</head>
<body>
    <div id="player"></div>

    <script>
        var player = null;
        var timeTicker = null;
        var isBackgroundMode = false;
        var keepLightweightTicker = false;
        var lastReportedYtState = -999;
        var desiredPlayState = null;
        var stateSyncTimer = null;

        var urlParams = new URLSearchParams(window.location.search);
        var initialVideoId = urlParams.get('v') || '';
        var initialStartSeconds = parseFloat(urlParams.get('start') || '0');
        var activeVideoId = initialVideoId;

        var tag = document.createElement('script');
        tag.src = "https://www.youtube.com/iframe_api";
        tag.onerror = function() {
            if (window.PhantomBridge) {
                window.PhantomBridge.onError(999);
            }
        };
        var firstScriptTag = document.getElementsByTagName('script')[0];
        firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

        function onYouTubeIframeAPIReady() {
            var playerConfig = {
                height: '100%',
                width: '100%',
                playerVars: {
                    'autoplay': 1,
                    'controls': 0,
                    'disablekb': 1,
                    'fs': 0,
                    'modestbranding': 1,
                    'rel': 0,
                    'iv_load_policy': 3,
                    'playsinline': 1,
                    'enablejsapi': 1,
                    'showinfo': 0,
                    'autohide': 1,
                    'cc_load_policy': 1,
                    'cc_lang_pref': 'id',
                    'origin': 'https://appassets.androidplatform.net',
                    'widget_referrer': 'https://appassets.androidplatform.net'
                },
                events: {
                    'onReady': onPlayerReady,
                    'onStateChange': onPlayerStateChange,
                    'onError': onPlayerError
                }
            };

            if (initialVideoId && initialVideoId.length > 2) {
                playerConfig.videoId = initialVideoId;
                if (initialStartSeconds > 0) {
                    playerConfig.playerVars.start = Math.floor(initialStartSeconds);
                }
            }

            player = new YT.Player('player', playerConfig);
        }

        window.addEventListener('resize', function() {
            try {
                if (player && typeof player.setSize === 'function') {
                    player.setSize(window.innerWidth, window.innerHeight);
                }
                var iframe = document.querySelector('iframe');
                if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage({ type: 'PHANTOM_CLEAN' }, '*');
                }
            } catch(e) {}
        });

        function sendStoryboard() {
            try {
                if (player && typeof player.getPlayerResponse === 'function') {
                    var pr = player.getPlayerResponse();
                    if (pr && pr.storyboards && pr.storyboards.playerStoryboardSpecRenderer) {
                        var s = pr.storyboards.playerStoryboardSpecRenderer.spec;
                        if (s && window.PhantomBridge && typeof window.PhantomBridge.onStoryboardSpec === 'function') {
                            window.PhantomBridge.onStoryboardSpec(s);
                        }
                    }
                    if (pr) {
                        var es = pr.endscreen || (pr.playerOverlays && pr.playerOverlays.playerOverlayRenderer && pr.playerOverlays.playerOverlayRenderer.endscreen) || (pr.overlay && pr.overlay.playerOverlayRenderer && pr.overlay.playerOverlayRenderer.endscreen);
                        var renderer = es ? (es.endscreenRenderer || es) : null;
                        var elements = renderer ? (renderer.elements || []) : [];
                        if (elements.length > 0) {
                            var items = [];
                            for (var i = 0; i < elements.length; i++) {
                                var elem = elements[i].endscreenElementRenderer || elements[i];
                                var ep = elem.endpoint && elem.endpoint.watchEndpoint;
                                var vidId = ep ? ep.videoId : '';
                                if (!vidId || vidId === activeVideoId) continue;
                                if (items.some(function(it){ return it.id === vidId; })) continue;
                                var title = (elem.title && elem.title.runs) ? elem.title.runs.map(function(r){return r.text;}).join('') : ((elem.title && elem.title.simpleText) ? elem.title.simpleText : '');
                                var channel = (elem.shortBylineText && elem.shortBylineText.runs) ? elem.shortBylineText.runs.map(function(r){return r.text;}).join('') : '';
                                var duration = (elem.videoDuration && elem.videoDuration.simpleText) ? elem.videoDuration.simpleText : '';
                                var thumb = '';
                                if (elem.image && elem.image.thumbnails && elem.image.thumbnails.length > 0) {
                                    thumb = elem.image.thumbnails[elem.image.thumbnails.length - 1].url;
                                }
                                if (!thumb) thumb = 'https://i.ytimg.com/vi/' + vidId + '/hqdefault.jpg';
                                items.push({
                                    id: vidId,
                                    title: title,
                                    channel: channel,
                                    duration: duration,
                                    thumbnail: thumb
                                });
                            }
                            if (items.length > 0 && window.PhantomBridge && typeof window.PhantomBridge.onEndscreenData === 'function') {
                                window.PhantomBridge.onEndscreenData(JSON.stringify(items));
                            }
                        }
                    }
                }
            } catch(e) {}
        }

        function ensureHostAudioFull() {
            try {
                if (player) {
                    if (typeof player.isMuted === 'function' && player.isMuted()) {
                        if (typeof player.unMute === 'function') player.unMute();
                    }
                    if (typeof player.getVolume === 'function' && player.getVolume() < 100) {
                        if (typeof player.setVolume === 'function') player.setVolume(100);
                    }
                }
                var iframe = document.querySelector('iframe');
                if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage({ type: 'PHANTOM_ENSURE_AUDIO_HEALTH' }, '*');
                }
            } catch(e) {}
        }

        function onPlayerReady(event) {
            if (window.PhantomBridge) {
                window.PhantomBridge.onReady();
            }
            ensureHostAudioFull();
            startTimeTicker();
            setTimeout(sendStoryboard, 1000);
            if (event.target && typeof event.target.playVideo === 'function') {
                event.target.playVideo();
            }
        }

        function onPlayerStateChange(event) {
            if (desiredPlayState === true && event.data === 2) {
                try {
                    if (player && typeof player.playVideo === 'function') player.playVideo();
                } catch(e) {}
                startTimeTicker();
                return;
            }
            if (desiredPlayState === false && (event.data === 1 || event.data === 3)) {
                try {
                    if (player && typeof player.pauseVideo === 'function') player.pauseVideo();
                } catch(e) {}
                stopTimeTicker();
                return;
            }
            if ((desiredPlayState === true && event.data === 1) ||
                (desiredPlayState === false && event.data === 2)) {
                desiredPlayState = null;
            }
            if (!isBackgroundMode) {
                try {
                    var iframe = document.querySelector('iframe');
                    if (iframe && iframe.contentWindow) {
                        iframe.contentWindow.postMessage({ type: 'PHANTOM_CLEAN' }, '*');
                    }
                } catch(e) {}
            }
            if (event.data === 1 || event.data === 3) {
                ensureHostAudioFull();
                startTimeTicker();
                if (!isBackgroundMode) {
                    sendStoryboard();
                    try {
                        if (player && typeof player.getAvailableQualityLevels === 'function') {
                            var lvls = player.getAvailableQualityLevels() || [];
                            var cur = (typeof player.getPlaybackQuality === 'function') ? player.getPlaybackQuality() : 'auto';
                            if (window.PhantomBridge && typeof window.PhantomBridge.onQualityChange === 'function') {
                                window.PhantomBridge.onQualityChange(cur || 'auto', JSON.stringify(lvls));
                            }
                        }
                    } catch(e) {}
                }
            } else {
                stopTimeTicker();
            }
            lastReportedYtState = event.data;
            syncTimeSnapshot();
            if (window.PhantomBridge) {
                window.PhantomBridge.onStateChange(event.data);
            }
        }

        function onPlayerError(event) {
            stopTimeTicker();
            if (window.PhantomBridge) {
                window.PhantomBridge.onError(event.data);
            }
        }

        function syncTimeSnapshot() {
            try {
                if (player && typeof player.getCurrentTime === 'function' && typeof player.getDuration === 'function') {
                    var videoData = (typeof player.getVideoData === 'function') ? player.getVideoData() : null;
                    var loadedId = (videoData && videoData.video_id) ? videoData.video_id : activeVideoId;
                    if (activeVideoId && loadedId && loadedId !== activeVideoId) {
                        return;
                    }
                    if (typeof player.getPlayerState === 'function') {
                        var ytState = player.getPlayerState();
                        if (desiredPlayState === true && ytState === 2) {
                            try {
                                if (typeof player.playVideo === 'function') player.playVideo();
                            } catch(e) {}
                        } else if (desiredPlayState === false && (ytState === 1 || ytState === 3)) {
                            try {
                                if (typeof player.pauseVideo === 'function') player.pauseVideo();
                            } catch(e) {}
                        } else if ((ytState === 1 || ytState === 2) && ytState !== lastReportedYtState && window.PhantomBridge) {
                            if ((desiredPlayState === true && ytState === 1) ||
                                (desiredPlayState === false && ytState === 2)) {
                                desiredPlayState = null;
                            }
                            lastReportedYtState = ytState;
                            window.PhantomBridge.onStateChange(ytState);
                        }
                    }
                    var current = player.getCurrentTime() || 0;
                    var duration = player.getDuration() || 0;
                    var loaded = (typeof player.getVideoLoadedFraction === 'function' ? player.getVideoLoadedFraction() : 0) || 0;
                    if (window.PhantomBridge) {
                        window.PhantomBridge.onTimeUpdate(activeVideoId, current, duration, loaded);
                    }
                }
            } catch(e) {}
        }

        function stopTimeTicker() {
            if (timeTicker) {
                clearInterval(timeTicker);
                timeTicker = null;
            }
        }

        function startTimeTicker() {
            if (timeTicker) clearInterval(timeTicker);
            timeTicker = null;
            if (isBackgroundMode) {
                timeTicker = setInterval(syncTimeSnapshot, 2000);
                return;
            }
            timeTicker = setInterval(syncTimeSnapshot, 500);
        }

        window.addEventListener('message', function(e) {
            if (e.data && e.data.type === 'PHANTOM_QUALITY_REPORT') {
                if (window.PhantomBridge && typeof window.PhantomBridge.onQualityChange === 'function') {
                    window.PhantomBridge.onQualityChange(e.data.current || 'auto', JSON.stringify(e.data.levels || []));
                }
                if (window.PhantomBridge && typeof window.PhantomBridge.onCaptionsAvailable === 'function' && typeof e.data.hasCaptions !== 'undefined') {
                    window.PhantomBridge.onCaptionsAvailable(!!e.data.hasCaptions);
                }
            } else if (e.data && e.data.type === 'PHANTOM_ENDSCREEN_DATA' && e.data.items) {
                if (window.PhantomBridge && typeof window.PhantomBridge.onEndscreenData === 'function') {
                    window.PhantomBridge.onEndscreenData(e.data.items);
                }
            }
        });

        function notifyIframeMediaState(active) {
            try {
                var iframe = document.querySelector('iframe');
                if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage({
                        type: active ? 'PHANTOM_MEDIA_WAKE' : 'PHANTOM_MEDIA_SLEEP'
                    }, '*');
                }
            } catch(e) {}
        }

        window.loadVideo = function(videoId, startSec) {
            try {
                activeVideoId = videoId;
                lastReportedYtState = -999;
                desiredPlayState = null;
                if (stateSyncTimer) {
                    clearTimeout(stateSyncTimer);
                    stateSyncTimer = null;
                }
                stopTimeTicker();
                notifyIframeMediaState(true);
                if (player && typeof player.loadVideoById === 'function') {
                    player.loadVideoById({
                        'videoId': videoId,
                        'startSeconds': startSec || 0
                    });
                    ensureHostAudioFull();
                    startTimeTicker();
                }
                setTimeout(sendStoryboard, 1500);
            } catch(e) {
                console.error("loadVideo error: " + e);
            }
        };

        window.requestStoryboard = function() {
            sendStoryboard();
        };

        window.playVideo = function() {
            try {
                desiredPlayState = true;
                notifyIframeMediaState(true);
                if (player) {
                    ensureHostAudioFull();
                    if (typeof player.playVideo === 'function') player.playVideo();
                    startTimeTicker();
                }
                if (stateSyncTimer) clearTimeout(stateSyncTimer);
                stateSyncTimer = setTimeout(syncTimeSnapshot, 350);
            } catch(e) {}
        };

        window.pauseVideo = function() {
            try {
                desiredPlayState = false;
                stopTimeTicker();
                notifyIframeMediaState(false);
                if (player && typeof player.pauseVideo === 'function') player.pauseVideo();
                syncTimeSnapshot();
                if (stateSyncTimer) clearTimeout(stateSyncTimer);
                stateSyncTimer = setTimeout(syncTimeSnapshot, 350);
            } catch(e) {}
        };

        window.seekTo = function(seconds) {
            try {
                notifyIframeMediaState(true);
                if (player && typeof player.seekTo === 'function') player.seekTo(seconds, true);
                syncTimeSnapshot();
            } catch(e) {}
        };

        window.setBackgroundMode = function(bg, keepSponsorTick) {
            try {
                isBackgroundMode = !!bg;
                keepLightweightTicker = !!keepSponsorTick;
                if (isBackgroundMode) {
                    syncTimeSnapshot();
                    stopTimeTicker();
                    var pState = (player && typeof player.getPlayerState === 'function') ? player.getPlayerState() : -1;
                    if (pState === 1 || pState === 3) {
                        startTimeTicker();
                    }
                } else {
                    syncTimeSnapshot();
                    var pStateFg = (player && typeof player.getPlayerState === 'function') ? player.getPlayerState() : -1;
                    if (pStateFg === 1 || pStateFg === 3) {
                        startTimeTicker();
                    }
                }
                var iframe = document.querySelector('iframe');
                if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage({
                        type: 'PHANTOM_SET_BACKGROUND_MODE',
                        background: isBackgroundMode
                    }, '*');
                }
            } catch(e) {}
        };

        window.setPlaybackRate = function(rate) {
            try {
                if (player && typeof player.setPlaybackRate === 'function') player.setPlaybackRate(rate);
            } catch(e) {}
        };

        window.setLoop = function(loop) {
            try {
                if (player && typeof player.setLoop === 'function') {
                    player.setLoop(loop);
                }
            } catch(e) {}
        };

        window.setPlaybackQuality = function(quality) {
            try {
                if (player) {
                    if (typeof player.setPlaybackQualityRange === 'function') {
                        player.setPlaybackQualityRange(quality, quality);
                    }
                    if (typeof player.setPlaybackQuality === 'function') {
                        player.setPlaybackQuality(quality);
                    }
                }
                var iframe = document.querySelector('iframe');
                if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage({ type: 'PHANTOM_SET_QUALITY', quality: quality }, '*');
                }
            } catch(e) {}
        };

        window.setAudioOnly = function(enabled) {
            try {
                if (document.body) {
                    if (enabled) document.body.classList.add('phantom-collapsed');
                    else document.body.classList.remove('phantom-collapsed');
                }
                if (enabled && player) {
                    if (typeof player.setPlaybackQualityRange === 'function') {
                        player.setPlaybackQualityRange('tiny', 'tiny');
                    }
                    if (typeof player.setPlaybackQuality === 'function') {
                        player.setPlaybackQuality('tiny');
                    }
                    if (typeof player.setSize === 'function') {
                        player.setSize(1, 1);
                    }
                } else if (!enabled && player) {
                    if (typeof player.setSize === 'function') {
                        player.setSize(window.innerWidth, window.innerHeight);
                    }
                }
                var iframe = document.querySelector('iframe');
                if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage({ type: 'PHANTOM_SET_AUDIO_ONLY', enabled: !!enabled }, '*');
                }
                ensureHostAudioFull();
            } catch(e) {}
        };

        window.setVideoSurfaceActive = function(active, restoreQuality) {
            try {
                if (document.body) {
                    if (!active) document.body.classList.add('phantom-collapsed');
                    else document.body.classList.remove('phantom-collapsed');
                }
                if (player && typeof player.setSize === 'function') {
                    if (active) {
                        player.setSize(window.innerWidth, window.innerHeight);
                        if (restoreQuality && restoreQuality !== 'auto') {
                            if (typeof player.setPlaybackQualityRange === 'function') {
                                player.setPlaybackQualityRange(restoreQuality, restoreQuality);
                            }
                            if (typeof player.setPlaybackQuality === 'function') {
                                player.setPlaybackQuality(restoreQuality);
                            }
                        }
                    } else {
                        player.setSize(1, 1);
                        if (typeof player.setPlaybackQualityRange === 'function') {
                            player.setPlaybackQualityRange('tiny', 'tiny');
                        }
                        if (typeof player.setPlaybackQuality === 'function') {
                            player.setPlaybackQuality('tiny');
                        }
                    }
                }
                var iframe = document.querySelector('iframe');
                if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage({
                        type: 'PHANTOM_SET_SURFACE_ACTIVE',
                        active: !!active,
                        restoreQuality: restoreQuality || 'auto'
                    }, '*');
                }
                ensureHostAudioFull();
            } catch(e) {}
        };

        window.setCaptionsEnabled = function(enabled, lang) {
            try {
                if (player) {
                    if (enabled) {
                        try { if (typeof player.loadModule === 'function') player.loadModule('captions'); } catch(e) {}
                        try {
                            var tl = (typeof player.getOption === 'function') ? player.getOption('captions', 'tracklist') : null;
                            if (tl && tl.length > 0) {
                                var chosen = null;
                                for (var i = 0; i < tl.length; i++) {
                                    if (tl[i].languageCode === 'id' || tl[i].languageCode === 'in') {
                                        chosen = tl[i]; break;
                                    }
                                }
                                if (!chosen) {
                                    for (var i = 0; i < tl.length; i++) {
                                        if (tl[i].languageCode === 'jv' || tl[i].languageCode === 'en') {
                                            chosen = tl[i]; break;
                                        }
                                    }
                                }
                                if (!chosen) chosen = tl[0];
                                player.setOption('captions', 'track', chosen);
                            } else {
                                player.setOption('captions', 'track', { 'languageCode': lang || 'id' });
                            }
                        } catch(e) {}
                    } else {
                        try {
                            if (typeof player.unloadModule === 'function') player.unloadModule('captions');
                            if (typeof player.setOption === 'function') player.setOption('captions', 'track', {});
                        } catch(e) {}
                    }
                }
                var iframe = document.querySelector('iframe');
                if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage({
                        type: 'PHANTOM_SET_CAPTIONS',
                        enabled: !!enabled,
                        lang: lang || 'id'
                    }, '*');
                }
            } catch(e) {}
        };
    </script>
</body>
</html>
"""

    private const val RAW_CSS_RULES = """
/* 0. Force full-bleed responsive video container & stream across portrait and landscape fullscreen */
html, body, #player, #movie_player, .html5-video-player, .html5-video-container {
    width: 100% !important;
    height: 100% !important;
    max-width: 100% !important;
    max-height: 100% !important;
    margin: 0 !important;
    padding: 0 !important;
    top: 0 !important;
    left: 0 !important;
    background-color: #000000 !important;
}
video.html5-main-video, .html5-video-container video {
    width: 100% !important;
    height: 100% !important;
    max-width: 100% !important;
    max-height: 100% !important;
    top: 0 !important;
    left: 0 !important;
    right: 0 !important;
    bottom: 0 !important;
    object-fit: contain !important;
}

/* 1. Endscreen elements & annotations (video cards, playlists, videowall) */
.ytp-ce-element,
.ytp-ce-element *,
.ytp-ce-video,
.ytp-ce-channel,
.ytp-ce-playlist,
.ytp-ce-website,
.ytp-ce-merchandise,
.ytp-ce-covering-overlay,
.ytp-ce-covering-image,
.ytp-ce-expanding-overlay,
.ytp-ce-expanding-image,
.ytp-ce-element-show,
.ytp-ce-shown,
.ytp-ce-element-shadow,
.ytp-ce-hide-button-container,
.html5-endscreen,
.html5-endscreen *,
.html5-ypc-endscreen,
.html5-ypc-endscreen *,
.modern-videowall-endscreen,
.modern-videowall-endscreen *,
.video-annotations,
.video-annotations *,
.video-legacy-annotations,
.video-legacy-annotations *,
.ytp-endscreen-content,
.ytp-endscreen-content *,
.ytp-endscreen-next,
.ytp-endscreen-paginate,
.ytp-endscreen-previous,
.ytp-endscreen-takeover,
.ytp-videowall-still,
.ytp-videowall-still *,
.iv-card,
.iv-card *,
.iv-promo,
.iv-promo *,
[class*="ytp-ce"],
[class*="ytp-ce"] *,
[class*="endscreen"],
[class*="endscreen"] *,
[class*="annotation"],
[class*="annotation"] *,
[class*="videowall"],
[class*="videowall"] *,
[class*="iv-card"],
[class*="iv-card"] *,
[class*="iv-promo"],
[class*="iv-promo"] * {
    display: none !important;
    opacity: 0 !important;
    visibility: hidden !important;
    pointer-events: none !important;
    width: 0 !important;
    height: 0 !important;
    position: absolute !important;
    top: -9999px !important;
    left: -9999px !important;
    z-index: -9999 !important;
}

/* 2. Play/Pause bezel animation, large play button & center overlays */
.ytp-bezel,
.ytp-bezel *,
.ytp-bezel-icon,
.ytp-bezel-icon *,
.ytp-bezel-text,
.ytp-bezel-text-wrapper,
.ytp-large-play-button,
.ytp-large-play-button *,
.ytp-large-play-button-bg,
.ytp-play-button,
.ytp-play-button *,
button.ytp-play-button,
.ytp-pause-overlay,
.ytp-pause-overlay *,
.ytp-pause-overlay-backdrop,
.ytp-pause-overlay-container,
.ytp-cued-thumbnail-overlay,
.ytp-cued-thumbnail-overlay-image,
.ytp-spinner,
.ytp-paid-content-overlay,
[class*="ytp-bezel"],
[class*="ytp-bezel"] *,
[class*="ytp-large-play"],
[class*="ytp-large-play"] *,
[class*="ytp-play-button"],
[class*="ytp-play-button"] *,
[class*="ytp-pause"],
[class*="ytp-pause"] *,
button[aria-label*="Play"],
button[aria-label*="Putar"],
button[aria-label*="Pause"],
button[aria-label*="Jeda"],
button[aria-label*="Share"],
button[aria-label*="Bagikan"] {
    display: none !important;
    opacity: 0 !important;
    visibility: hidden !important;
    pointer-events: none !important;
    animation: none !important;
    -webkit-animation: none !important;
    width: 0 !important;
    height: 0 !important;
    position: absolute !important;
    top: -9999px !important;
    left: -9999px !important;
    z-index: -9999 !important;
}

/* 3. Bottom controls, progress bar & gradient */
.ytp-chrome-bottom,
.ytp-chrome-bottom *,
.ytp-progress-bar-container,
.ytp-progress-bar-container *,
.ytp-progress-bar,
.ytp-progress-bar *,
.ytp-play-progress,
.ytp-load-progress,
.ytp-gradient-bottom,
[class*="ytp-chrome-bottom"],
[class*="ytp-chrome-bottom"] * {
    display: none !important;
    opacity: 0 !important;
    visibility: hidden !important;
    pointer-events: none !important;
    width: 0 !important;
    height: 0 !important;
    position: absolute !important;
    top: -9999px !important;
    left: -9999px !important;
    z-index: -9999 !important;
}

/* 4. Top bar, titles, channel logo/avatar & share button */
.ytp-chrome-top,
.ytp-chrome-top *,
.ytp-title,
.ytp-title *,
.ytp-title-channel,
.ytp-title-channel *,
.ytp-title-channel-logo,
.ytp-title-channel-name,
span.ytp-title-channel-name,
a.ytp-title-channel,
.ytp-title-link,
.ytp-title-subtext,
.ytp-title-expanded,
.ytp-title-show-expanded,
.ytp-share-button,
.ytp-share-button *,
.ytp-share-panel,
.ytp-share-panel *,
.ytp-gradient-top,
.ytp-cards-teaser,
.ytp-cards-teaser *,
.ytp-cards-button,
.ytp-cards-button *,
[class*="ytp-chrome-top"],
[class*="ytp-chrome-top"] *,
[class*="ytp-title"],
[class*="ytp-title"] *,
[class*="ytp-share"],
[class*="ytp-share"] *,
[class*="cards-teaser"],
[class*="cards-teaser"] *,
[class*="channel"],
[class*="channel"] *,
[class*="avatar"],
[class*="avatar"] *,
a[href*="/@"],
a[href*="/channel/"],
a[href*="/user/"],
a[href*="/c/"],
img[src*="ggpht.com"],
img[src*="googleusercontent.com"],
[style*="ggpht.com"],
[style*="googleusercontent.com"] {
    display: none !important;
    opacity: 0 !important;
    visibility: hidden !important;
    pointer-events: none !important;
    width: 0 !important;
    height: 0 !important;
    position: absolute !important;
    top: -9999px !important;
    left: -9999px !important;
    z-index: -9999 !important;
}

/* 5. Watermarks, YouTube logo & impression links */
.ytp-watermark,
.ytp-watermark *,
.ytp-youtube-button,
.ytp-youtube-button *,
.ytp-impression-link,
.ytp-impression-link *,
[class*="ytp-watermark"],
[class*="ytp-watermark"] *,
[class*="ytp-youtube"],
[class*="ytp-youtube"] *,
[class*="ytp-impression"],
[class*="ytp-impression"] *,
a[href*="youtube.com/watch"] {
    display: none !important;
    opacity: 0 !important;
    visibility: hidden !important;
    pointer-events: none !important;
    width: 0 !important;
    height: 0 !important;
    position: absolute !important;
    top: -9999px !important;
    left: -9999px !important;
    z-index: -9999 !important;
}
"""

    private const val RAW_CLEAN_SCRIPT = """
(function() {
    var isIframeBackground = false;
    var isIframeMediaActive = false;
    var isIframePlaybackDesired = true;

    try {
        Object.defineProperty(document, 'hidden', {
            get: function() { return false; },
            configurable: true
        });
        Object.defineProperty(document, 'visibilityState', {
            get: function() { return 'visible'; },
            configurable: true
        });
        Object.defineProperty(document, 'webkitVisibilityState', {
            get: function() { return 'visible'; },
            configurable: true
        });
        Object.defineProperty(document, 'webkitHidden', {
            get: function() { return false; },
            configurable: true
        });
    } catch(e) {}

    window.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
    document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
    window.addEventListener('webkitvisibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
    document.addEventListener('webkitvisibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
    window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
    window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);
    window.addEventListener('freeze', function(e) { e.stopImmediatePropagation(); }, true);

    var bgRafTimers = Object.create(null);
    var deferredRafQueue = [];
    function clearBgRafTimers() {
        for (var k in bgRafTimers) {
            if (Object.prototype.hasOwnProperty.call(bgRafTimers, k)) {
                clearTimeout(bgRafTimers[k]);
                delete bgRafTimers[k];
            }
        }
    }
    function flushDeferredRafs() {
        if (!deferredRafQueue.length) return;
        var pending = deferredRafQueue;
        deferredRafQueue = [];
        var ts = (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now();
        for (var i = 0; i < pending.length; i++) {
            try { pending[i](ts); } catch(err) {}
        }
    }

    try {
        var origRAF = window.requestAnimationFrame ? window.requestAnimationFrame.bind(window) : null;
        var origCAF = window.cancelAnimationFrame ? window.cancelAnimationFrame.bind(window) : null;
        var bgRafSeq = 1;
        window.requestAnimationFrame = function(cb) {
            if (!isIframeBackground && origRAF) {
                return origRAF(cb);
            }
            if (isIframeBackground && !isIframeMediaActive && !isIframePlaybackDesired) {
                if (deferredRafQueue.length < 16) {
                    deferredRafQueue.push(cb);
                }
                return -bgRafSeq++;
            }
            var fakeId = -bgRafSeq++;
            bgRafTimers[fakeId] = setTimeout(function() {
                delete bgRafTimers[fakeId];
                try {
                    cb((typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now());
                } catch(err) {}
            }, isIframeBackground ? 250 : 16);
            return fakeId;
        };
        window.cancelAnimationFrame = function(id) {
            if (id < 0 && bgRafTimers[id]) {
                clearTimeout(bgRafTimers[id]);
                delete bgRafTimers[id];
                return;
            }
            if (origCAF && id >= 0) {
                try { origCAF(id); } catch(err) {}
            }
        };
    } catch(e) {}

    document.addEventListener('play', function() {
        isIframeMediaActive = true;
        isIframePlaybackDesired = true;
        flushDeferredRafs();
    }, true);
    document.addEventListener('playing', function() {
        isIframeMediaActive = true;
        isIframePlaybackDesired = true;
        flushDeferredRafs();
    }, true);
    document.addEventListener('waiting', function() {
        isIframePlaybackDesired = true;
        flushDeferredRafs();
    }, true);
    document.addEventListener('loadstart', function() {
        isIframePlaybackDesired = true;
        flushDeferredRafs();
    }, true);
    document.addEventListener('seeking', function() {
        isIframePlaybackDesired = true;
        flushDeferredRafs();
    }, true);
    document.addEventListener('pause', function() {
        isIframeMediaActive = false;
        isIframePlaybackDesired = false;
        if (isIframeBackground) clearBgRafTimers();
    }, true);
    document.addEventListener('ended', function() {
        isIframeMediaActive = false;
        isIframePlaybackDesired = false;
        if (isIframeBackground) clearBgRafTimers();
    }, true);

    try {
        if (window.MediaSource && typeof window.MediaSource.isTypeSupported === 'function') {
            var origIsTypeSupported = window.MediaSource.isTypeSupported.bind(window.MediaSource);
            window.MediaSource.isTypeSupported = function(mimeType) {
                if (typeof mimeType === 'string' && mimeType.toLowerCase().indexOf('av01') !== -1) {
                    return false;
                }
                return origIsTypeSupported(mimeType);
            };
        }
        if (navigator.mediaCapabilities && typeof navigator.mediaCapabilities.decodingInfo === 'function') {
            var origDecodingInfo = navigator.mediaCapabilities.decodingInfo.bind(navigator.mediaCapabilities);
            navigator.mediaCapabilities.decodingInfo = function(config) {
                if (config && config.video) {
                    var ct = (config.video.contentType || '').toLowerCase();
                    if (ct.indexOf('av01') !== -1) {
                        return Promise.resolve({ supported: false, smooth: false, powerEfficient: false });
                    }
                    if (isIframeBackground &&
                        ((config.video.height || 0) > 240 ||
                         (config.video.width || 0) > 426 ||
                         (config.video.framerate || 0) > 30)) {
                        return Promise.resolve({ supported: true, smooth: false, powerEfficient: false });
                    }
                }
                return origDecodingInfo(config);
            };
        }
    } catch(e) {}

    var selectors = [
        '.ytp-ce-element', '.ytp-ce-video', '.ytp-ce-channel', '.ytp-ce-playlist',
        '.ytp-ce-website', '.ytp-ce-merchandise', '.ytp-ce-covering-overlay',
        '.ytp-ce-covering-image', '.ytp-ce-expanding-overlay', '.ytp-ce-expanding-image',
        '.ytp-ce-element-show', '.ytp-ce-shown', '.ytp-ce-element-shadow',
        '.ytp-ce-hide-button-container', '.ytp-ce-size-medium', '.ytp-ce-size-large',
        '.ytp-ce-bottom-right-quad', '.ytp-ce-bottom-left-quad', '.ytp-ce-top-right-quad', '.ytp-ce-top-left-quad',
        '[class*="ytp-ce"]', '.html5-endscreen', '.html5-ypc-endscreen',
        '.modern-videowall-endscreen', '.ytp-endscreen-content', '.ytp-endscreen-next',
        '.ytp-endscreen-paginate', '.ytp-endscreen-previous', '.ytp-endscreen-takeover',
        '.ytp-videowall-still', '[class*="endscreen"]',
        '.iv-card', '.iv-promo', '[class*="iv-card"]', '[class*="iv-promo"]',
        '.video-annotations', '.video-legacy-annotations', '[class*="annotation"]', '[class*="videowall"]',

        '.ytp-bezel', '.ytp-bezel-icon', '.ytp-bezel-text', '.ytp-bezel-text-wrapper', '[class*="ytp-bezel"]',
        '.ytp-large-play-button', '.ytp-large-play-button-bg', '[class*="ytp-large-play"]',
        '.ytp-play-button', 'button.ytp-play-button', '[class*="ytp-play-button"]',
        '.ytp-pause-overlay', '.ytp-pause-overlay-backdrop', '.ytp-pause-overlay-container', '[class*="ytp-pause"]',
        '.ytp-cued-thumbnail-overlay', '.ytp-cued-thumbnail-overlay-image', '.ytp-spinner',
        'button[aria-label*="Play"]', 'button[aria-label*="Putar"]',
        'button[aria-label*="Pause"]', 'button[aria-label*="Jeda"]',

        '.ytp-chrome-bottom', '.ytp-progress-bar-container', '.ytp-progress-bar',
        '.ytp-play-progress', '.ytp-load-progress', '.ytp-gradient-bottom',
        '[class*="ytp-chrome-bottom"]',

        '.ytp-chrome-top', '.ytp-chrome-top-buttons',
        '.ytp-title', '.ytp-title-text', '.ytp-title-channel', '.ytp-title-channel-logo',
        '.ytp-title-channel-name', 'span.ytp-title-channel-name', 'a.ytp-title-channel',
        '.ytp-title-link', '.ytp-title-subtext', '.ytp-title-expanded', '.ytp-title-show-expanded',
        '.ytp-share-button', '.ytp-share-panel', '.ytp-share-icon',
        'button[aria-label*="Share"]', 'button[aria-label*="Bagikan"]', '[class*="ytp-share"]',
        '.ytp-gradient-top', '.ytp-cards-button', '.ytp-cards-teaser',
        '.ytp-cards-teaser-channel-avatar', '.ytp-cards-teaser-box', '.ytp-cards-teaser-text', '.ytp-cards-teaser-label',
        '[class*="ytp-chrome-top"]', '[class*="ytp-title"]', '[class*="cards-teaser"]',
        '[class*="channel"]', '[class*="avatar"]', '[class*="author"]',
        'a[href*="/@"]', 'a[href*="/channel/"]', 'a[href*="/user/"]', 'a[href*="/c/"]',
        'img[src*="ggpht.com"]', 'img[src*="googleusercontent.com"]',
        '[style*="ggpht.com"]', '[style*="googleusercontent.com"]',

        '.ytp-watermark', '.ytp-youtube-button', '.ytp-watch-on-youtube-button',
        '.ytp-impression-link', '.ytp-impression-link-logo', '.ytp-impression-link-text',
        '[class*="ytp-watermark"]', '[class*="ytp-youtube"]', '[class*="ytp-impression"]',
        'a[aria-label*="YouTube"]', 'a[href*="youtube.com/watch"]'
    ];
    var combinedSelector = selectors.join(',');

    var lastSentEndscreenIds = '';
    var rafPending = false;
    var cleanTimerId = null;
    var qualityReportInterval = null;

    function hideNode(node) {
        if (!node || !node.style) return;
        node.style.setProperty('display', 'none', 'important');
        node.style.setProperty('opacity', '0', 'important');
        node.style.setProperty('visibility', 'hidden', 'important');
        node.style.setProperty('pointer-events', 'none', 'important');
    }

    var initialFullSweepDone = false;

    function clean() {
        rafPending = false;
        try {
            var ceEls = document.querySelectorAll('.ytp-ce-element, .ytp-ce-video');
            if (ceEls && ceEls.length > 0) {
                var items = [];
                for (var i = 0; i < ceEls.length; i++) {
                    var el = ceEls[i];
                    var a = (el.tagName && el.tagName.toLowerCase() === 'a') ? el : el.querySelector('a');
                    var href = a ? (a.getAttribute('href') || a.href || '') : '';
                    var match = href.match(/[?&]v=([^&]+)/) || href.match(/\/watch\/([^?&]+)/) || href.match(/\/shorts\/([^?&]+)/);
                    var vidId = match ? match[1] : '';
                    if (!vidId) continue;
                    if (items.some(function(it){ return it.id === vidId; })) continue;

                    var titleEl = el.querySelector('.ytp-ce-video-title, .ytp-ce-element-title, [class*="title"]');
                    var title = titleEl ? (titleEl.textContent || '').trim() : '';

                    var durEl = el.querySelector('.ytp-ce-video-duration, [class*="duration"]');
                    var duration = durEl ? (durEl.textContent || '').trim() : '';

                    var chanEl = el.querySelector('.ytp-ce-channel-title, [class*="channel"]');
                    var channel = chanEl ? (chanEl.textContent || '').trim() : '';

                    var imgEl = el.querySelector('img');
                    var thumb = imgEl ? (imgEl.src || imgEl.getAttribute('src') || '') : '';
                    if (!thumb || thumb.indexOf('data:') === 0) {
                        thumb = 'https://i.ytimg.com/vi/' + vidId + '/hqdefault.jpg';
                    }

                    items.push({
                        id: vidId,
                        title: title,
                        channel: channel,
                        duration: duration,
                        thumbnail: thumb
                    });
                }
                var currentIds = items.map(function(it){ return it.id; }).join(',');
                if (currentIds && currentIds !== lastSentEndscreenIds) {
                    lastSentEndscreenIds = currentIds;
                    window.parent.postMessage({
                        type: 'PHANTOM_ENDSCREEN_DATA',
                        items: JSON.stringify(items)
                    }, '*');
                }
            }
        } catch(e) {}

        if (!initialFullSweepDone && document.querySelector('.html5-video-player')) {
            try {
                var els = document.querySelectorAll(combinedSelector);
                for (var i = 0; i < els.length; i++) {
                    hideNode(els[i]);
                }
                initialFullSweepDone = true;
            } catch(e) {}
        }

        try {
            var player = document.querySelector('.html5-video-player');
            if (player) {
                player.style.setProperty('width', '100%', 'important');
                player.style.setProperty('height', '100%', 'important');
                if (player.children) {
                    var kids = player.children;
                    for (var k = 0; k < kids.length; k++) {
                        var child = kids[k];
                        var cName = (child.className || '').toString().toLowerCase();
                        if (child.classList.contains('html5-video-container') ||
                            child.tagName.toLowerCase() === 'video' ||
                            cName.indexOf('caption') !== -1 ||
                            cName.indexOf('subtitle') !== -1) {
                            continue;
                        }
                        hideNode(child);
                    }
                }
            }
            var container = document.querySelector('.html5-video-container');
            if (container && container.style) {
                container.style.setProperty('width', '100%', 'important');
                container.style.setProperty('height', '100%', 'important');
                container.style.setProperty('top', '0px', 'important');
                container.style.setProperty('left', '0px', 'important');
            }
            var vid = document.querySelector('video.html5-main-video') || document.querySelector('video');
            if (vid && vid.style) {
                vid.style.setProperty('width', '100%', 'important');
                vid.style.setProperty('height', '100%', 'important');
                vid.style.setProperty('top', '0px', 'important');
                vid.style.setProperty('left', '0px', 'important');
                vid.style.setProperty('object-fit', 'contain', 'important');
            }
            var deadNodes = document.querySelectorAll('.ytp-pause-overlay, .ytp-share-panel, .ytp-watermark, .ytp-paid-content-overlay, .ytp-cued-thumbnail-overlay');
            for (var d = 0; d < deadNodes.length; d++) {
                try { deadNodes[d].remove(); } catch(err) {}
            }
        } catch(e) {}
    }

    function scheduleClean() {
        if (isIframeBackground || rafPending) return;
        rafPending = true;
        cleanTimerId = setTimeout(clean, 550);
    }

    clean();
    var obs = new MutationObserver(scheduleClean);
    if (document.documentElement) {
        obs.observe(document.documentElement, {
            childList: true,
            subtree: true
        });
    }
    document.addEventListener('DOMContentLoaded', clean);
    window.addEventListener('load', clean);

    window.addEventListener('message', function(e) {
        if (e.data && e.data.type === 'PHANTOM_SET_QUALITY') {
            var q = e.data.quality;
            var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (p) {
                try { if (typeof p.setPlaybackQualityRange === 'function') p.setPlaybackQualityRange(q, q); } catch(err) {}
                try { if (typeof p.setPlaybackQuality === 'function') p.setPlaybackQuality(q); } catch(err) {}
            }
        } else if (e.data && e.data.type === 'PHANTOM_SET_CAPTIONS') {
            try {
                var isEnabled = !!e.data.enabled;
                var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                if (p) {
                    if (isEnabled) {
                        try { if (typeof p.loadModule === 'function') p.loadModule('captions'); } catch(err) {}
                        try {
                            var tl = (typeof p.getOption === 'function') ? p.getOption('captions', 'tracklist') : null;
                            if (tl && tl.length > 0) {
                                var chosen = null;
                                for (var i = 0; i < tl.length; i++) {
                                    if (tl[i].languageCode === 'id' || tl[i].languageCode === 'in') {
                                        chosen = tl[i]; break;
                                    }
                                }
                                if (!chosen) {
                                    for (var i = 0; i < tl.length; i++) {
                                        if (tl[i].languageCode === 'jv' || tl[i].languageCode === 'en') {
                                            chosen = tl[i]; break;
                                        }
                                    }
                                }
                                if (!chosen) chosen = tl[0];
                                p.setOption('captions', 'track', chosen);
                            } else {
                                p.setOption('captions', 'track', { 'languageCode': 'id' });
                            }
                        } catch(err) {}
                    } else {
                        try {
                            if (typeof p.unloadModule === 'function') p.unloadModule('captions');
                            if (typeof p.setOption === 'function') p.setOption('captions', 'track', {});
                        } catch(err) {}
                    }
                }
                var subBtn = document.querySelector('.ytp-subtitles-button');
                if (subBtn) {
                    var pressed = (subBtn.getAttribute('aria-pressed') === 'true');
                    if (isEnabled && !pressed) {
                        subBtn.click();
                    } else if (!isEnabled && pressed) {
                        subBtn.click();
                    }
                }
                var sEl = document.getElementById('phantom-caption-lock');
                if (!sEl) {
                    sEl = document.createElement('style');
                    sEl.id = 'phantom-caption-lock';
                    document.head.appendChild(sEl);
                }
                if (isEnabled) {
                    sEl.textContent = '.ytp-caption-window-container, .caption-window, .captions-text, .caption-visual-line, .ytp-caption-segment { display: block !important; visibility: visible !important; opacity: 1 !important; z-index: 2147483647 !important; }';
                } else {
                    sEl.textContent = '.ytp-caption-window-container, .caption-window, .captions-text, .caption-visual-line, .ytp-caption-segment { display: none !important; visibility: hidden !important; opacity: 0 !important; }';
                }
            } catch(err) {}
        } else if (e.data && e.data.type === 'PHANTOM_SET_AUDIO_ONLY') {
            try {
                var audioOnly = !!e.data.enabled;
                var aEl = document.getElementById('phantom-audio-only-lock');
                if (!aEl) {
                    aEl = document.createElement('style');
                    aEl.id = 'phantom-audio-only-lock';
                    (document.head || document.documentElement).appendChild(aEl);
                }
                if (audioOnly) {
                    aEl.textContent = 'html, body, #player, #movie_player, .html5-video-player { width: 1px !important; height: 1px !important; overflow: hidden !important; contain: strict !important; } .html5-video-container, video.html5-main-video, video { opacity: 0 !important; pointer-events: none !important; width: 1px !important; height: 1px !important; }';
                    var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                    if (p) {
                        try { if (typeof p.setPlaybackQualityRange === 'function') p.setPlaybackQualityRange('tiny', 'tiny'); } catch(err) {}
                        try { if (typeof p.setPlaybackQuality === 'function') p.setPlaybackQuality('tiny'); } catch(err) {}
                    }
                } else {
                    aEl.textContent = '';
                }
            } catch(err) {}
        } else if (e.data && e.data.type === 'PHANTOM_SET_SURFACE_ACTIVE') {
            try {
                var isActive = !!e.data.active;
                var sLock = document.getElementById('phantom-surface-suspend-lock');
                if (!sLock) {
                    sLock = document.createElement('style');
                    sLock.id = 'phantom-surface-suspend-lock';
                    (document.head || document.documentElement).appendChild(sLock);
                }
                var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                if (!isActive) {
                    sLock.textContent = 'html, body, #player, #movie_player, .html5-video-player { width: 1px !important; height: 1px !important; overflow: hidden !important; contain: strict !important; } .html5-video-container, video.html5-main-video, video { opacity: 0 !important; pointer-events: none !important; width: 1px !important; height: 1px !important; }';
                    if (p) {
                        try { if (typeof p.setPlaybackQualityRange === 'function') p.setPlaybackQualityRange('tiny', 'tiny'); } catch(err) {}
                        try { if (typeof p.setPlaybackQuality === 'function') p.setPlaybackQuality('tiny'); } catch(err) {}
                    }
                } else {
                    sLock.textContent = '';
                    var rq = e.data.restoreQuality;
                    if (p && rq && rq !== 'auto') {
                        try { if (typeof p.setPlaybackQualityRange === 'function') p.setPlaybackQualityRange(rq, rq); } catch(err) {}
                        try { if (typeof p.setPlaybackQuality === 'function') p.setPlaybackQuality(rq); } catch(err) {}
                    }
                }
            } catch(err) {}
        } else if (e.data && e.data.type === 'PHANTOM_MEDIA_WAKE') {
            isIframePlaybackDesired = true;
            flushDeferredRafs();
        } else if (e.data && e.data.type === 'PHANTOM_MEDIA_SLEEP') {
            isIframePlaybackDesired = false;
            if (isIframeBackground && !isIframeMediaActive) {
                clearBgRafTimers();
            }
        } else if (e.data && e.data.type === 'PHANTOM_CLEAN') {
            if (!isIframeBackground) {
                clean();
            }
        } else if (e.data && e.data.type === 'PHANTOM_SET_BACKGROUND_MODE') {
            try {
                isIframeBackground = !!e.data.background;
                if (isIframeBackground) {
                    obs.disconnect();
                    if (cleanTimerId) {
                        clearTimeout(cleanTimerId);
                        cleanTimerId = null;
                    }
                    rafPending = false;
                    if (qualityReportInterval) {
                        clearInterval(qualityReportInterval);
                        qualityReportInterval = null;
                    }
                    if (!isIframeMediaActive && !isIframePlaybackDesired) {
                        clearBgRafTimers();
                    }
                } else {
                    flushDeferredRafs();
                    if (document.documentElement) {
                        obs.observe(document.documentElement, {
                            childList: true,
                            subtree: true
                        });
                    }
                    clean();
                    reportIframeQuality();
                    if (!qualityReportInterval) {
                        qualityReportInterval = setInterval(reportIframeQuality, 4000);
                    }
                }
            } catch(err) {}
        } else if (e.data && e.data.type === 'PHANTOM_ENSURE_AUDIO_HEALTH') {
            enforceAudioHealth();
        }
    });

    var lastQualityReportKey = '';
    function reportIframeQuality() {
        try {
            var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (p) {
                var levels = (typeof p.getAvailableQualityLevels === 'function') ? p.getAvailableQualityLevels() : [];
                var current = (typeof p.getPlaybackQuality === 'function') ? p.getPlaybackQuality() : 'auto';
                var tracks = (typeof p.getOption === 'function') ? p.getOption('captions', 'tracklist') : [];
                var subBtn = document.querySelector('.ytp-subtitles-button');
                var hasCaps = !!(tracks && tracks.length > 0) || (subBtn != null && subBtn.style.display !== 'none');
                var key = current + '|' + (levels ? levels.join(',') : '') + '|' + (hasCaps ? '1' : '0');
                if (key !== lastQualityReportKey) {
                    lastQualityReportKey = key;
                    window.parent.postMessage({
                        type: 'PHANTOM_QUALITY_REPORT',
                        current: current,
                        levels: levels,
                        hasCaptions: hasCaps
                    }, '*');
                }
            }
        } catch(e) {}
    }

    var isEnforcingAudio = false;
    var audioFixWindowStart = 0;
    var audioFixBurstCount = 0;
    function enforceAudioHealth() {
        if (isEnforcingAudio) return;
        var now = Date.now();
        if (now - audioFixWindowStart > 2000) {
            audioFixWindowStart = now;
            audioFixBurstCount = 0;
        }
        if (audioFixBurstCount >= 8) return;

        isEnforcingAudio = true;
        try {
            var fixed = false;
            var vid = document.querySelector('video.html5-main-video') || document.querySelector('video');
            if (vid) {
                if (vid.muted) {
                    vid.muted = false;
                    fixed = true;
                }
                if (typeof vid.volume === 'number' && vid.volume < 0.99) {
                    vid.volume = 1.0;
                    fixed = true;
                }
            }
            var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (p) {
                if (typeof p.isMuted === 'function' && p.isMuted()) {
                    if (typeof p.unMute === 'function') {
                        p.unMute();
                        fixed = true;
                    }
                }
                if (typeof p.getVolume === 'function' && p.getVolume() < 100) {
                    if (typeof p.setVolume === 'function') {
                        p.setVolume(100);
                        fixed = true;
                    }
                }
            }
            if (fixed) {
                audioFixBurstCount++;
            }
        } catch(e) {
        } finally {
            isEnforcingAudio = false;
        }
    }
    document.addEventListener('play', enforceAudioHealth, true);
    document.addEventListener('playing', enforceAudioHealth, true);
    document.addEventListener('loadeddata', enforceAudioHealth, true);
    document.addEventListener('canplay', enforceAudioHealth, true);
    document.addEventListener('volumechange', enforceAudioHealth, true);

    qualityReportInterval = setInterval(reportIframeQuality, 4000);
    document.addEventListener('DOMContentLoaded', reportIframeQuality);
    window.addEventListener('load', reportIframeQuality);
})();
"""
}
