package com.phantom.tube.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.util.ParsedAppLink
import com.phantom.tube.core.util.openExternalUrl
import com.phantom.tube.core.util.parseAppLink
import com.phantom.tube.data.model.VideoItem

private const val TAG_URL = "PHANTOM_URL"
private const val TAG_TIMESTAMP = "PHANTOM_TIMESTAMP"
private const val TAG_HANDLE = "PHANTOM_HANDLE"

private val URL_AND_DOMAIN_REGEX = Regex(
    """(?:https?://[^\s<>"]+|www\.[^\s<>"]+|\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+(?:com|org|net|io|id|co|me|gg|tv|app|dev|ly|ee|be|gl|ac|edu|gov|info|biz|xyz|ai|sh|to|fm|cc|link|page|site|store|tech|online|watch)(?:/[^\s<>"]*)?)""",
    RegexOption.IGNORE_CASE
)

private val TIMESTAMP_REGEX = Regex("""(?<!\d)(?:\d{1,2}:)?\d{1,2}:\d{2}(?!\d)""")

private val HANDLE_REGEX = Regex("""(?<![A-Za-z0-9._%+-])@[A-Za-z0-9_.-]{3,30}\b""")

private data class DetectedSpan(
    val start: Int,
    val end: Int,
    val tag: String,
    val value: String,
    val underline: Boolean
)

/**
 * Universal rich text component that detects and activates clickable web URLs, bare domain links,
 * YouTube video/channel links, timestamps (`01:23`), and `@channel` handles across descriptions,
 * comments, and channel profiles.
 */
@Composable
fun PhantomLinkText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    linkColor: Color = Color(0xFF3EA6FF),
    fontSize: TextUnit = 13.5.sp,
    lineHeight: TextUnit = 20.sp,
    fontWeight: FontWeight? = null,
    style: TextStyle = TextStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTimestampClick: ((Float) -> Unit)? = null,
    onChannelClick: ((channelId: String, channelTitle: String) -> Unit)? = null,
    onVideoLinkClick: ((VideoItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val hasTimestampHandler = onTimestampClick != null
    val hasChannelHandler = onChannelClick != null

    val annotatedText = remember(text, linkColor, hasTimestampHandler, hasChannelHandler) {
        buildLinkAnnotatedString(
            text = text,
            linkColor = linkColor,
            enableTimestamps = hasTimestampHandler,
            enableHandles = hasChannelHandler
        )
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val hasAnnotations = remember(annotatedText) {
        annotatedText.getStringAnnotations(0, annotatedText.length).isNotEmpty()
    }

    val tapModifier = if (hasAnnotations) {
        Modifier.pointerInput(annotatedText, onTimestampClick, onChannelClick, onVideoLinkClick) {
            detectTapGestures { pos ->
                val layout = layoutResult ?: return@detectTapGestures
                val offset = layout.getOffsetForPosition(pos)
                val annotations = annotatedText.getStringAnnotations(start = offset, end = offset)
                val hit = annotations.firstOrNull() ?: return@detectTapGestures

                when (hit.tag) {
                    TAG_URL -> {
                        val rawUrl = hit.item
                        when (val parsed = parseAppLink(rawUrl)) {
                            is ParsedAppLink.YouTubeVideo -> {
                                if (onVideoLinkClick != null) {
                                    onVideoLinkClick(
                                        VideoItem(
                                            id = parsed.videoId,
                                            title = "",
                                            thumbnailUrl = "https://i.ytimg.com/vi/${parsed.videoId}/hqdefault.jpg",
                                            playlistId = parsed.playlistId
                                        )
                                    )
                                } else {
                                    context.openExternalUrl(rawUrl)
                                }
                            }
                            is ParsedAppLink.YouTubeChannel -> {
                                if (onChannelClick != null) {
                                    onChannelClick(parsed.channelIdOrHandle, parsed.channelIdOrHandle)
                                } else {
                                    context.openExternalUrl(rawUrl)
                                }
                            }
                            is ParsedAppLink.ExternalWebUrl -> {
                                context.openExternalUrl(parsed.url)
                            }
                        }
                    }
                    TAG_TIMESTAMP -> {
                        val seconds = hit.item.toFloatOrNull()
                        if (seconds != null) {
                            onTimestampClick?.invoke(seconds)
                        }
                    }
                    TAG_HANDLE -> {
                        val handle = hit.item
                        if (handle.isNotBlank()) {
                            onChannelClick?.invoke(handle, handle)
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Text(
        text = annotatedText,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult = it },
        modifier = modifier.then(tapModifier)
    )
}

private fun buildLinkAnnotatedString(
    text: String,
    linkColor: Color,
    enableTimestamps: Boolean,
    enableHandles: Boolean
): AnnotatedString {
    if (text.isBlank()) return AnnotatedString(text)

    val spans = mutableListOf<DetectedSpan>()

    // 1. Detect Web URLs and Bare Domains
    URL_AND_DOMAIN_REGEX.findAll(text).forEach { match ->
        var rawStart = match.range.first
        var rawEnd = match.range.last + 1
        var matchedText = text.substring(rawStart, rawEnd)

        // Trim trailing sentence punctuation not belonging to the URL
        while (matchedText.isNotEmpty() && matchedText.last() in charArrayOf(')', ']', '>', '.', ',', ';', ':', '!', '?', '\'', '"')) {
            matchedText = matchedText.dropLast(1)
            rawEnd -= 1
        }
        if (rawStart > 0 && text[rawStart - 1] == '@') {
            return@forEach
        }
        if (matchedText.length >= 4 && rawEnd > rawStart) {
            spans.add(
                DetectedSpan(
                    start = rawStart,
                    end = rawEnd,
                    tag = TAG_URL,
                    value = matchedText,
                    underline = true
                )
            )
        }
    }

    // 2. Detect Timestamps (e.g. 0:45, 12:30, 1:02:15) if enabled
    if (enableTimestamps) {
        TIMESTAMP_REGEX.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (spans.none { start < it.end && end > it.start }) {
                val seconds = parseTimestampToSeconds(match.value)
                if (seconds != null) {
                    spans.add(
                        DetectedSpan(
                            start = start,
                            end = end,
                            tag = TAG_TIMESTAMP,
                            value = seconds.toString(),
                            underline = false
                        )
                    )
                }
            }
        }
    }

    // 3. Detect @channel handles if enabled
    if (enableHandles) {
        HANDLE_REGEX.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (spans.none { start < it.end && end > it.start }) {
                val handle = match.value.trimEnd('.', ',', ';', ':', ')', ']')
                val cleanEnd = start + handle.length
                if (handle.length >= 3) {
                    spans.add(
                        DetectedSpan(
                            start = start,
                            end = cleanEnd,
                            tag = TAG_HANDLE,
                            value = handle,
                            underline = false
                        )
                    )
                }
            }
        }
    }

    if (spans.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            addStyle(
                style = SpanStyle(
                    color = linkColor,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (span.underline) TextDecoration.Underline else TextDecoration.None
                ),
                start = span.start,
                end = span.end
            )
            addStringAnnotation(
                tag = span.tag,
                annotation = span.value,
                start = span.start,
                end = span.end
            )
        }
    }
}

private fun parseTimestampToSeconds(raw: String): Float? {
    val parts = raw.split(":").map { it.toIntOrNull() ?: return null }
    return when (parts.size) {
        2 -> {
            val min = parts[0]
            val sec = parts[1]
            if (sec in 0..59) (min * 60 + sec).toFloat() else null
        }
        3 -> {
            val hr = parts[0]
            val min = parts[1]
            val sec = parts[2]
            if (min in 0..59 && sec in 0..59) (hr * 3600 + min * 60 + sec).toFloat() else null
        }
        else -> null
    }
}
