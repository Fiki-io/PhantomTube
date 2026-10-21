package com.phantom.tube.core.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.phantom.tube.R
import java.net.URLDecoder

/**
 * Traverses ContextWrapper chain to retrieve the hosting Activity.
 * Ensures compatibility when LocalContext is wrapped by a ConfigurationContext for localization.
 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is Activity) return ctx
        if (ctx is ContextWrapper) {
            ctx = ctx.baseContext
        } else {
            break
        }
    }
    return null
}

/**
 * Starts an Activity safely whether [this] is an Activity, a localized ContextWrapper,
 * or an Application/Service Context.
 */
fun Context.safeStartActivity(intent: Intent): Boolean {
    return try {
        val activity = findActivity()
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Unwraps YouTube redirect URLs (`https://www.youtube.com/redirect?...&q=...`)
 * and strips trailing sentence punctuation.
 */
fun unwrapYouTubeRedirectUrl(rawUrl: String): String {
    var cleaned = rawUrl.trim()
        .trimEnd(')', ']', '>', '.', ',', ';', '\'', '"')
    if (cleaned.isEmpty()) return ""

    try {
        val lower = cleaned.lowercase()
        if (lower.contains("youtube.com/redirect") && lower.contains("q=")) {
            val uri = Uri.parse(
                if (lower.startsWith("http://") || lower.startsWith("https://")) cleaned
                else "https://$cleaned"
            )
            val qParam = uri.getQueryParameter("q")
            if (!qParam.isNullOrBlank()) {
                cleaned = try {
                    URLDecoder.decode(qParam, "UTF-8")
                } catch (_: Exception) {
                    qParam
                }
            }
        }
    } catch (_: Exception) {
    }
    return cleaned
}

/**
 * Normalizes a web URL or bare domain into an actionable URI string with scheme.
 */
fun normalizeWebUrl(rawUrl: String): String {
    val unwrapped = unwrapYouTubeRedirectUrl(rawUrl)
    if (unwrapped.isBlank()) return ""
    val lower = unwrapped.lowercase()
    return when {
        lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("tel:") -> unwrapped
        lower.startsWith("//") -> "https:$unwrapped"
        else -> "https://$unwrapped"
    }
}

sealed class ParsedAppLink {
    data class YouTubeVideo(
        val videoId: String,
        val playlistId: String? = null,
        val startSeconds: Int = 0
    ) : ParsedAppLink()

    data class YouTubeChannel(
        val channelIdOrHandle: String
    ) : ParsedAppLink()

    data class ExternalWebUrl(
        val url: String
    ) : ParsedAppLink()
}

private val YOUTUBE_HOSTS = setOf(
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "music.youtube.com",
    "youtu.be"
)

private val VIDEO_ID_REGEX = Regex("""^[A-Za-z0-9_-]{11}$""")

/**
 * Parses a URL string and determines whether it points to a YouTube video,
 * a YouTube channel, or a general external web URL.
 */
fun parseAppLink(rawUrl: String): ParsedAppLink {
    val normalized = normalizeWebUrl(rawUrl)
    if (normalized.isBlank()) return ParsedAppLink.ExternalWebUrl("")

    try {
        val uri = Uri.parse(normalized)
        val host = uri.host?.lowercase() ?: return ParsedAppLink.ExternalWebUrl(normalized)
        if (host !in YOUTUBE_HOSTS) {
            return ParsedAppLink.ExternalWebUrl(normalized)
        }

        val timeParam = uri.getQueryParameter("t") ?: uri.getQueryParameter("start")
        val startSec = parseTimeParamSeconds(timeParam)
        val listParam = uri.getQueryParameter("list")?.takeIf { it.isNotBlank() }

        if (host == "youtu.be") {
            val seg = uri.pathSegments.firstOrNull()
            if (seg != null && VIDEO_ID_REGEX.matches(seg)) {
                return ParsedAppLink.YouTubeVideo(
                    videoId = seg,
                    playlistId = listParam,
                    startSeconds = startSec
                )
            }
        } else {
            val pathSegments = uri.pathSegments
            val firstSeg = pathSegments.firstOrNull() ?: ""
            when {
                firstSeg == "watch" -> {
                    val v = uri.getQueryParameter("v")
                    if (v != null && VIDEO_ID_REGEX.matches(v)) {
                        return ParsedAppLink.YouTubeVideo(
                            videoId = v,
                            playlistId = listParam,
                            startSeconds = startSec
                        )
                    }
                }
                firstSeg in setOf("shorts", "live", "embed", "v") -> {
                    val v = pathSegments.getOrNull(1)
                    if (v != null && VIDEO_ID_REGEX.matches(v)) {
                        return ParsedAppLink.YouTubeVideo(
                            videoId = v,
                            playlistId = listParam,
                            startSeconds = startSec
                        )
                    }
                }
                firstSeg == "channel" -> {
                    val chId = pathSegments.getOrNull(1)
                    if (!chId.isNullOrBlank()) {
                        return ParsedAppLink.YouTubeChannel(chId)
                    }
                }
                firstSeg.startsWith("@") && firstSeg.length > 1 -> {
                    return ParsedAppLink.YouTubeChannel(firstSeg)
                }
                firstSeg in setOf("c", "user") -> {
                    val handle = pathSegments.getOrNull(1)
                    if (!handle.isNullOrBlank()) {
                        return ParsedAppLink.YouTubeChannel("@$handle")
                    }
                }
            }
        }
    } catch (_: Exception) {
    }

    return ParsedAppLink.ExternalWebUrl(normalized)
}

/**
 * Extracts the first YouTube link (video or channel) from arbitrary shared text.
 */
fun extractYouTubeLinkFromText(text: String): ParsedAppLink? {
    val urlRegex = Regex("""(?:https?://|www\.)?(?:(?:www\.|m\.|music\.)?youtube\.com/[^\s<>"]+|youtu\.be/[^\s<>"]+)""", RegexOption.IGNORE_CASE)
    val match = urlRegex.find(text) ?: return null
    val parsed = parseAppLink(match.value)
    return if (parsed is ParsedAppLink.YouTubeVideo || parsed is ParsedAppLink.YouTubeChannel) {
        parsed
    } else {
        null
    }
}

private fun parseTimeParamSeconds(raw: String?): Int {
    if (raw.isNullOrBlank()) return 0
    val clean = raw.trim().lowercase().removeSuffix("s")
    clean.toIntOrNull()?.let { return it.coerceAtLeast(0) }
    var total = 0
    val hMatch = Regex("""(\d+)h""").find(clean)
    val mMatch = Regex("""(\d+)m""").find(clean)
    val sMatch = Regex("""(\d+)s?""").find( raw.trim().lowercase() )
    if (hMatch != null) total += (hMatch.groupValues[1].toIntOrNull() ?: 0) * 3600
    if (mMatch != null) total += (mMatch.groupValues[1].toIntOrNull() ?: 0) * 60
    if (sMatch != null && hMatch == null && mMatch == null) {
        total += sMatch.groupValues[1].toIntOrNull() ?: 0
    } else {
        val secExplicit = Regex("""(\d+)s""").find(raw.trim().lowercase())
        if (secExplicit != null) {
            total += secExplicit.groupValues[1].toIntOrNull() ?: 0
        }
    }
    return total.coerceAtLeast(0)
}

/**
 * Opens an external URL in the user's browser with fallback chooser and localized error toast.
 */
fun Context.openExternalUrl(rawUrl: String): Boolean {
    val finalUrl = normalizeWebUrl(rawUrl)
    if (finalUrl.isBlank()) return false

    val uri = try {
        Uri.parse(finalUrl)
    } catch (_: Exception) {
        null
    } ?: return false

    val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }

    if (safeStartActivity(viewIntent)) {
        return true
    }

    val chooserIntent = Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), null)
    if (safeStartActivity(chooserIntent)) {
        return true
    }

    Toast.makeText(
        this,
        getString(R.string.settings_browser_error),
        Toast.LENGTH_SHORT
    ).show()
    return false
}

/**
 * Launches the user's email/messaging app to send feedback, with multi-stage fallback
 * (`ACTION_SENDTO` mailto -> `ACTION_SEND` rfc822 -> `ACTION_SEND` text/plain -> clipboard).
 */
fun Context.sendFeedbackEmail(
    recipient: String,
    subject: String,
    body: String
) {
    val mailtoUri = Uri.parse(
        "mailto:${Uri.encode(recipient)}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
    )
    val sendToIntent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    if (safeStartActivity(sendToIntent)) {
        return
    }

    val rfc822Intent = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    val chooserTitle = getString(R.string.settings_feedback_chooser)
    if (safeStartActivity(Intent.createChooser(rfc822Intent, chooserTitle))) {
        return
    }

    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("email", "$recipient\n\n$subject\n$body"))
    Toast.makeText(
        this,
        getString(R.string.feedback_no_email_app),
        Toast.LENGTH_LONG
    ).show()
}
