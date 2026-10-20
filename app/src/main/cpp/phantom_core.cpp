#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>

namespace {

static volatile int g_engine_status = 1; // 1 = active, 0 = locked/tampered

// Official permanent release.jks SHA-256 fingerprint
static const uint8_t RELEASE_CERT_SHA256[32] = {
    0xae, 0xda, 0x80, 0xa7, 0x2d, 0xb6, 0xca, 0xb4,
    0x24, 0x6a, 0x2b, 0x3b, 0xa4, 0x6d, 0xa8, 0x52,
    0x94, 0xc6, 0xea, 0x9a, 0x4f, 0xf8, 0x49, 0x86,
    0x12, 0xe0, 0x62, 0xd8, 0x00, 0xa7, 0x16, 0x78
};

// Official debug.keystore SHA-256 fingerprint (for dev testing)
static const uint8_t DEBUG_CERT_SHA256[32] = {
    0x9b, 0x0c, 0x9b, 0xf4, 0x9d, 0x70, 0xa4, 0xb5,
    0x56, 0xd9, 0x2f, 0x5d, 0x9f, 0xdd, 0x57, 0x88,
    0xdc, 0x84, 0x81, 0x04, 0x47, 0x35, 0x9e, 0x66,
    0xf6, 0x39, 0x89, 0x4a, 0xaf, 0x2e, 0x17, 0x03
};

// AI Studio cloud debug.keystore SHA-256 fingerprint (for cloud dev testing)
static const uint8_t DEV_DEBUG_CERT_SHA256[32] = {
    0xcd, 0x62, 0xc7, 0x50, 0x86, 0x25, 0xd2, 0x29,
    0xfe, 0xf3, 0xcb, 0x91, 0x15, 0x8a, 0x27, 0x77,
    0xb6, 0x03, 0x77, 0xd1, 0xb5, 0x5b, 0xf4, 0x76,
    0x32, 0x88, 0xd7, 0x0f, 0x3e, 0xe6, 0xce, 0xe6
};

#define ROTRIGHT(a,b) (((a) >> (b)) | ((a) << (32-(b))))
#define CH(x,y,z) (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x,y,z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define EP0(x) (ROTRIGHT(x,2) ^ ROTRIGHT(x,13) ^ ROTRIGHT(x,22))
#define EP1(x) (ROTRIGHT(x,6) ^ ROTRIGHT(x,11) ^ ROTRIGHT(x,25))
#define SIG0(x) (ROTRIGHT(x,7) ^ ROTRIGHT(x,18) ^ ((x) >> 3))
#define SIG1(x) (ROTRIGHT(x,17) ^ ROTRIGHT(x,19) ^ ((x) >> 10))

static const uint32_t SHA256_K[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

typedef struct {
    uint8_t data[64];
    uint32_t datalen;
    uint64_t bitlen;
    uint32_t state[8];
} PHANTOM_SHA256_CTX;

static void phantom_sha256_transform(PHANTOM_SHA256_CTX *ctx, const uint8_t data[]) {
    uint32_t a, b, c, d, e, f, g, h, i, j, t1, t2, m[64];
    for (i = 0, j = 0; i < 16; ++i, j += 4)
        m[i] = (data[j] << 24) | (data[j + 1] << 16) | (data[j + 2] << 8) | (data[j + 3]);
    for ( ; i < 64; ++i)
        m[i] = SIG1(m[i - 2]) + m[i - 7] + SIG0(m[i - 15]) + m[i - 16];
    a = ctx->state[0]; b = ctx->state[1]; c = ctx->state[2]; d = ctx->state[3];
    e = ctx->state[4]; f = ctx->state[5]; g = ctx->state[6]; h = ctx->state[7];
    for (i = 0; i < 64; ++i) {
        t1 = h + EP1(e) + CH(e,f,g) + SHA256_K[i] + m[i];
        t2 = EP0(a) + MAJ(a,b,c);
        h = g; g = f; f = e; e = d + t1;
        d = c; c = b; b = a; a = t1 + t2;
    }
    ctx->state[0] += a; ctx->state[1] += b; ctx->state[2] += c; ctx->state[3] += d;
    ctx->state[4] += e; ctx->state[5] += f; ctx->state[6] += g; ctx->state[7] += h;
}

static void phantom_sha256_calc(const uint8_t data[], size_t len, uint8_t hash[32]) {
    PHANTOM_SHA256_CTX ctx;
    ctx.datalen = 0; ctx.bitlen = 0;
    ctx.state[0] = 0x6a09e667; ctx.state[1] = 0xbb67ae85;
    ctx.state[2] = 0x3c6ef372; ctx.state[3] = 0xa54ff53a;
    ctx.state[4] = 0x510e527f; ctx.state[5] = 0x9b05688c;
    ctx.state[6] = 0x1f83d9ab; ctx.state[7] = 0x5be0cd19;

    for (size_t idx = 0; idx < len; ++idx) {
        ctx.data[ctx.datalen] = data[idx];
        ctx.datalen++;
        if (ctx.datalen == 64) {
            phantom_sha256_transform(&ctx, ctx.data);
            ctx.bitlen += 512;
            ctx.datalen = 0;
        }
    }

    uint32_t i = ctx.datalen;
    if (ctx.datalen < 56) {
        ctx.data[i++] = 0x80;
        while (i < 56) ctx.data[i++] = 0x00;
    } else {
        ctx.data[i++] = 0x80;
        while (i < 64) ctx.data[i++] = 0x00;
        phantom_sha256_transform(&ctx, ctx.data);
        memset(ctx.data, 0, 56);
    }
    ctx.bitlen += ctx.datalen * 8;
    ctx.data[63] = ctx.bitlen;
    ctx.data[62] = ctx.bitlen >> 8;
    ctx.data[61] = ctx.bitlen >> 16;
    ctx.data[60] = ctx.bitlen >> 24;
    ctx.data[59] = ctx.bitlen >> 32;
    ctx.data[58] = ctx.bitlen >> 40;
    ctx.data[57] = ctx.bitlen >> 48;
    ctx.data[56] = ctx.bitlen >> 56;
    phantom_sha256_transform(&ctx, ctx.data);

    for (i = 0; i < 4; ++i) {
        hash[i]      = (ctx.state[0] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 4]  = (ctx.state[1] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 8]  = (ctx.state[2] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 12] = (ctx.state[3] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 16] = (ctx.state[4] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 20] = (ctx.state[5] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 24] = (ctx.state[6] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 28] = (ctx.state[7] >> (24 - i * 8)) & 0x000000ff;
    }
}

const char* LOCKED_ENGINE_HTML = R"HTML(
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
)HTML";

const char* RAW_ENGINE_HTML = R"HTML(
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
            width: 100%;
            height: 100%;
            background-color: #000000;
            overflow: hidden;
            -webkit-user-select: none;
            user-select: none;
            -webkit-tap-highlight-color: transparent;
        }
        #player {
            width: 100%;
            height: 100%;
            pointer-events: none;
            transform: translateZ(0);
            will-change: transform;
            backface-visibility: hidden;
        }
        iframe {
            width: 100% !important;
            height: 100% !important;
            border: 0 !important;
            outline: 0 !important;
            transform: translateZ(0);
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

        function onPlayerReady(event) {
            if (window.PhantomBridge) {
                window.PhantomBridge.onReady();
            }
            startTimeTicker();
            setTimeout(sendStoryboard, 1000);
            if (event.target && typeof event.target.playVideo === 'function') {
                event.target.playVideo();
            }
        }

        function onPlayerStateChange(event) {
            if (event.data === 1 || event.data === 3) {
                startTimeTicker();
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
            } else {
                stopTimeTicker();
            }
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

        function stopTimeTicker() {
            if (timeTicker) {
                clearInterval(timeTicker);
                timeTicker = null;
            }
        }

        function startTimeTicker() {
            if (timeTicker) clearInterval(timeTicker);
            timeTicker = setInterval(function() {
                try {
                    if (player && typeof player.getCurrentTime === 'function' && typeof player.getDuration === 'function') {
                        var videoData = (typeof player.getVideoData === 'function') ? player.getVideoData() : null;
                        var loadedId = (videoData && videoData.video_id) ? videoData.video_id : activeVideoId;
                        if (activeVideoId && loadedId && loadedId !== activeVideoId) {
                            return;
                        }
                        var current = player.getCurrentTime() || 0;
                        var duration = player.getDuration() || 0;
                        var loaded = (typeof player.getVideoLoadedFraction === 'function' ? player.getVideoLoadedFraction() : 0) || 0;
                        if (window.PhantomBridge) {
                            window.PhantomBridge.onTimeUpdate(activeVideoId, current, duration, loaded);
                        }
                    }
                } catch(e) {}
            }, 500);
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

        window.loadVideo = function(videoId, startSec) {
            try {
                activeVideoId = videoId;
                stopTimeTicker();
                if (player && typeof player.loadVideoById === 'function') {
                    player.loadVideoById({
                        'videoId': videoId,
                        'startSeconds': startSec || 0
                    });
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
                if (player && typeof player.playVideo === 'function') player.playVideo();
            } catch(e) {}
        };

        window.pauseVideo = function() {
            try {
                stopTimeTicker();
                if (player && typeof player.pauseVideo === 'function') player.pauseVideo();
            } catch(e) {}
        };

        window.seekTo = function(seconds) {
            try {
                if (player && typeof player.seekTo === 'function') player.seekTo(seconds, true);
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
)HTML";

const char* RAW_CSS_RULES = R"CSS(
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
[class*="ytp-pause"] * {
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
)CSS";

const char* RAW_CLEAN_SCRIPT = R"JS(
(function() {
    var combinedSelector = [
        '.ytp-ce-element', '.ytp-ce-video', '.ytp-ce-channel', '.ytp-ce-playlist',
        '.ytp-ce-website', '.ytp-ce-merchandise', '.ytp-ce-covering-overlay',
        '.html5-endscreen', '.html5-ypc-endscreen', '.modern-videowall-endscreen',
        '.ytp-endscreen-content', '.ytp-videowall-still', '.iv-card', '.iv-promo',
        '.video-annotations', '.ytp-bezel', '.ytp-large-play-button', '.ytp-play-button',
        '.ytp-pause-overlay', '.ytp-cued-thumbnail-overlay', '.ytp-spinner',
        '.ytp-chrome-bottom', '.ytp-chrome-top', '.ytp-title', '.ytp-share-button',
        '.ytp-cards-button', '.ytp-cards-teaser', '.ytp-watermark', '.ytp-youtube-button',
        '.ytp-impression-link'
    ].join(',');

    var lastSentEndscreenIds = '';
    var cleanScheduled = false;

    function clean() {
        cleanScheduled = false;
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

        try {
            var els = document.querySelectorAll(combinedSelector);
            for (var i = 0; i < els.length; i++) {
                var node = els[i];
                if (node.style && node.style.display !== 'none') {
                    node.style.setProperty('display', 'none', 'important');
                    node.style.setProperty('pointer-events', 'none', 'important');
                }
            }
        } catch(e) {}

        try {
            var player = document.querySelector('.html5-video-player');
            if (player && player.children) {
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
                    if (child.style && child.style.display !== 'none') {
                        child.style.setProperty('display', 'none', 'important');
                        child.style.setProperty('pointer-events', 'none', 'important');
                    }
                }
            }
        } catch(e) {}
    }

    function scheduleClean() {
        if (cleanScheduled) return;
        cleanScheduled = true;
        setTimeout(clean, 350);
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
        } else if (e.data && e.data.type === 'PHANTOM_CLEAN') {
            scheduleClean();
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
    setInterval(reportIframeQuality, 2500);
    document.addEventListener('DOMContentLoaded', reportIframeQuality);
    window.addEventListener('load', reportIframeQuality);
})();
)JS";

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getEngineHtml(
        JNIEnv* env,
        jobject /* this */) {
    if (g_engine_status == 0) {
        return env->NewStringUTF(LOCKED_ENGINE_HTML);
    }
    return env->NewStringUTF(RAW_ENGINE_HTML);
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getCleanCssRules(
        JNIEnv* env,
        jobject /* this */) {
    if (g_engine_status == 0) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(RAW_CSS_RULES);
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getCleanScript(
        JNIEnv* env,
        jobject /* this */) {
    if (g_engine_status == 0) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(RAW_CLEAN_SCRIPT);
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getVirtualOrigin(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF("https://appassets.androidplatform.net");
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getWebUserAgent(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF(
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    );
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getInnerTubeDesktopUserAgent(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    );
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getSponsorBlockUrl(
        JNIEnv* env,
        jobject /* this */,
        jstring videoId) {
    const char* vidChars = env->GetStringUTFChars(videoId, nullptr);
    char url[512];
    snprintf(url, sizeof(url),
        "https://sponsor.ajay.app/api/skipSegments?videoID=%s&categories=%%5B%%22sponsor%%22%%2C%%22selfpromo%%22%%2C%%22interaction%%22%%2C%%22intro%%22%%2C%%22outro%%22%%5D",
        vidChars ? vidChars : "");
    if (vidChars) {
        env->ReleaseStringUTFChars(videoId, vidChars);
    }

    return env->NewStringUTF(url);
}

/**
 * Classifies the incoming network request URL.
 * Returns:
 *   1 -> In-memory Virtual Engine HTML route ("/engine/render")
 *   2 -> Embed HTML target to inject CSS/cleaner
 *   3 -> Video player CSS target to patch
 *   0 -> Pass-through / no modification
 */
JNIEXPORT jint JNICALL
Java_com_phantom_tube_core_security_PhantomNative_classifyUrl(
        JNIEnv* env,
        jobject /* this */,
        jstring urlStr) {
    const char* chars = env->GetStringUTFChars(urlStr, nullptr);
    if (!chars) return 0;

    jint result = 0;
    if (strstr(chars, "/engine/render") != nullptr) {
        result = 1;
    } else {
        bool isYtHost = (strstr(chars, "youtube.com") != nullptr) ||
                        (strstr(chars, "youtube-nocookie.com") != nullptr);

        if (isYtHost && (strstr(chars, "/embed/") != nullptr || strstr(chars, "/embed?") != nullptr)) {
            result = 2;
        } else {
            bool isMediaHost = isYtHost || (strstr(chars, "googlevideo.com") != nullptr);
            if (isMediaHost && (strstr(chars, ".css") != nullptr || strstr(chars, "/ss/") != nullptr)) {
                result = 3;
            }
        }
    }

    env->ReleaseStringUTFChars(urlStr, chars);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getSearchUrl(
        JNIEnv* env, jobject /* this */) {
    if (g_engine_status == 0) return env->NewStringUTF("");
    return env->NewStringUTF("https://www.youtube.com/youtubei/v1/search?prettyPrint=false");
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getNextUrl(
        JNIEnv* env, jobject /* this */) {
    if (g_engine_status == 0) return env->NewStringUTF("");
    return env->NewStringUTF("https://www.youtube.com/youtubei/v1/next?prettyPrint=false");
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getBrowseUrl(
        JNIEnv* env, jobject /* this */) {
    if (g_engine_status == 0) return env->NewStringUTF("");
    return env->NewStringUTF("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false");
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getSuggestUrl(
        JNIEnv* env, jobject /* this */, jint index, jstring queryStr, jstring hlStr, jstring glStr) {
    if (g_engine_status == 0) return env->NewStringUTF("");
    const char* chars = env->GetStringUTFChars(queryStr, nullptr);
    const char* q = chars ? chars : "";
    const char* hlChars = hlStr ? env->GetStringUTFChars(hlStr, nullptr) : "id";
    const char* glChars = glStr ? env->GetStringUTFChars(glStr, nullptr) : "ID";
    char u[1024];

    if (index == 0) {
        snprintf(u, sizeof(u), "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&hl=%s&gl=%s&q=%s", hlChars ? hlChars : "id", glChars ? glChars : "ID", q);
    } else if (index == 1) {
        snprintf(u, sizeof(u), "https://suggestqueries-clients6.youtube.com/complete/search?client=firefox&ds=yt&hl=%s&gl=%s&q=%s", hlChars ? hlChars : "id", glChars ? glChars : "ID", q);
    } else {
        snprintf(u, sizeof(u), "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=%s&gl=%s&q=%s", hlChars ? hlChars : "id", glChars ? glChars : "ID", q);
    }

    if (chars) {
        env->ReleaseStringUTFChars(queryStr, chars);
    }
    if (hlStr && hlChars) {
        env->ReleaseStringUTFChars(hlStr, hlChars);
    }
    if (glStr && glChars) {
        env->ReleaseStringUTFChars(glStr, glChars);
    }
    return env->NewStringUTF(u);
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getUpdateUrl(
        JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF("https://raw.githubusercontent.com/Fiki-io/phantom-version/main/version.json");
}

JNIEXPORT jboolean JNICALL
Java_com_phantom_tube_core_security_PhantomNative_verifyAppSecurity(
        JNIEnv* env,
        jobject /* this */,
        jobject context) {
    if (!context) {
        g_engine_status = 0;
        return JNI_FALSE;
    }

    jclass contextCls = env->GetObjectClass(context);
    if (!contextCls) {
        g_engine_status = 0;
        return JNI_FALSE;
    }

    jmethodID midGetPM = env->GetMethodID(contextCls, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jmethodID midGetPackageName = env->GetMethodID(contextCls, "getPackageName", "()Ljava/lang/String;");
    if (!midGetPM || !midGetPackageName) {
        env->ExceptionClear();
        g_engine_status = 0;
        return JNI_FALSE;
    }

    jobject pm = env->CallObjectMethod(context, midGetPM);
    jstring pkgName = (jstring)env->CallObjectMethod(context, midGetPackageName);
    if (!pm || !pkgName || env->ExceptionCheck()) {
        env->ExceptionClear();
        g_engine_status = 0;
        return JNI_FALSE;
    }

    jclass pmCls = env->GetObjectClass(pm);
    jmethodID midGetPackageInfo = env->GetMethodID(pmCls, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    if (!midGetPackageInfo) {
        env->ExceptionClear();
        g_engine_status = 0;
        return JNI_FALSE;
    }

    // 64 = PackageManager.GET_SIGNATURES
    jobject packageInfo = env->CallObjectMethod(pm, midGetPackageInfo, pkgName, (jint)64);
    if (!packageInfo || env->ExceptionCheck()) {
        env->ExceptionClear();
        g_engine_status = 0;
        return JNI_FALSE;
    }

    jclass piCls = env->GetObjectClass(packageInfo);
    jfieldID fidSignatures = env->GetFieldID(piCls, "signatures", "[Landroid/content/pm/Signature;");
    if (!fidSignatures) {
        env->ExceptionClear();
        g_engine_status = 0;
        return JNI_FALSE;
    }

    jobjectArray sigs = (jobjectArray)env->GetObjectField(packageInfo, fidSignatures);
    if (!sigs || env->GetArrayLength(sigs) == 0) {
        g_engine_status = 0;
        return JNI_FALSE;
    }

    jobject sig0 = env->GetObjectArrayElement(sigs, 0);
    if (!sig0) {
        g_engine_status = 0;
        return JNI_FALSE;
    }

    jclass sigCls = env->GetObjectClass(sig0);
    jmethodID midToByteArray = env->GetMethodID(sigCls, "toByteArray", "()[B");
    if (!midToByteArray) {
        env->ExceptionClear();
        g_engine_status = 0;
        return JNI_FALSE;
    }

    jbyteArray certBytes = (jbyteArray)env->CallObjectMethod(sig0, midToByteArray);
    if (!certBytes) {
        g_engine_status = 0;
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(certBytes);
    jbyte* elements = env->GetByteArrayElements(certBytes, nullptr);
    if (!elements) {
        g_engine_status = 0;
        return JNI_FALSE;
    }

    uint8_t hash[32];
    phantom_sha256_calc((const uint8_t*)elements, (size_t)len, hash);
    env->ReleaseByteArrayElements(certBytes, elements, JNI_ABORT);

    bool matchRelease = (memcmp(hash, RELEASE_CERT_SHA256, 32) == 0);
    bool matchDebug = (memcmp(hash, DEBUG_CERT_SHA256, 32) == 0);
    bool matchDevDebug = (memcmp(hash, DEV_DEBUG_CERT_SHA256, 32) == 0);

    if (matchRelease || matchDebug || matchDevDebug) {
        return JNI_TRUE;
    } else {
        g_engine_status = 0;
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_phantom_tube_core_security_PhantomNative_applyVersionControl(
        JNIEnv* env,
        jobject /* this */,
        jint currentCode,
        jint minCode,
        jboolean forceUpdate) {
    if (g_engine_status == 0) {
        return JNI_FALSE;
    }
    if (forceUpdate || (minCode > 0 && currentCode < minCode)) {
        g_engine_status = 0;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_phantom_tube_core_security_PhantomNative_isEngineLocked(
        JNIEnv* env,
        jobject /* this */) {
    return (g_engine_status == 0) ? JNI_TRUE : JNI_FALSE;
}

// =========================================================================
// PHANTOM OFFLINE DOWNLOADER ENDPOINTS
// Catatan: Jika website konverter mati atau ganti domain baru,
// cukup ubah URL di bawah ini secara langsung.
// =========================================================================

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getDownloadKeyUrl(
        JNIEnv* env,
        jobject /* this */,
        jstring videoId) {
    if (g_engine_status == 0) return env->NewStringUTF("");

    const char* vidChars = env->GetStringUTFChars(videoId, nullptr);
    char fullUrl[256];
    // Endpoint pengambilan token otorisasi konverter
    snprintf(fullUrl, sizeof(fullUrl), "https://cnv.cx/v2/sanity/key?id=%s", vidChars ? vidChars : "");
    if (vidChars) {
        env->ReleaseStringUTFChars(videoId, vidChars);
    }
    return env->NewStringUTF(fullUrl);
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getDownloadConverterUrl(
        JNIEnv* env,
        jobject /* this */) {
    if (g_engine_status == 0) return env->NewStringUTF("");
    // Endpoint eksekusi konversi MP3/MP4
    return env->NewStringUTF("https://cnv.cx/v2/converter");
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getDownloadRefererUrl(
        JNIEnv* env,
        jobject /* this */) {
    if (g_engine_status == 0) return env->NewStringUTF("");
    // Header Referer untuk otorisasi request
    return env->NewStringUTF("https://iframe.y2meta-uk.com/");
}

JNIEXPORT jstring JNICALL
Java_com_phantom_tube_core_security_PhantomNative_getDownloadOriginUrl(
        JNIEnv* env,
        jobject /* this */) {
    if (g_engine_status == 0) return env->NewStringUTF("");
    // Header Origin untuk otorisasi request
    return env->NewStringUTF("https://iframe.y2meta-uk.com");
}

} // extern "C"
