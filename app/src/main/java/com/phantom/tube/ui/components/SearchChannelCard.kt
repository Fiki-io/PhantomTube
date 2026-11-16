package com.phantom.tube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.phantom.tube.R
import com.phantom.tube.core.database.SubscriptionEntity
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBackgroundAlt
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
import com.phantom.tube.data.model.SearchChannelItem
import com.phantom.tube.data.repository.PhantomRepository
import kotlinx.coroutines.launch

@Composable
fun SearchChannelCard(
    channel: SearchChannelItem,
    repository: PhantomRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSubscribed by repository.isSubscribed(channel.id).collectAsState(initial = false)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var avatarUrl by remember(channel.id, channel.avatarUrl) {
                    val clean = InnerTubeHelpers.normalizeUrl(channel.avatarUrl)
                    mutableStateOf(clean.ifBlank { ChannelAvatarCache.get(channel.id, channel.title) })
                }

                LaunchedEffect(channel.id, channel.title, avatarUrl) {
                    if (avatarUrl.isBlank() && channel.id.isNotBlank() && channel.id.startsWith("UC")) {
                        val resolved = ChannelAvatarCache.resolveAvatar(channel.id, channel.title)
                        if (!resolved.isNullOrBlank()) {
                            avatarUrl = resolved
                        }
                    }
                }

                // Large circular avatar
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(CardBackgroundAlt),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        val avatarRequest = remember(avatarUrl, context) {
                            ImageRequest.Builder(context)
                                .data(avatarUrl)
                                .size(144, 144)
                                .precision(Precision.INEXACT)
                                .allowRgb565(true)
                                .allowHardware(false)
                                .build()
                        }
                        AsyncImage(
                            model = avatarRequest,
                            contentDescription = channel.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                        )
                    } else {
                        val initials = channel.title.trim().take(1).uppercase()
                        Text(
                            text = initials,
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title + Verified Check
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channel.title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (channel.isVerified) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.channel_verified),
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Handle & Subscriber & Video stats
                    val metaLine = buildString {
                        if (channel.handle.isNotBlank()) {
                            append(channel.handle)
                        }
                        if (channel.subscriberCountText.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(channel.subscriberCountText)
                        }
                        if (channel.videoCountText.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(channel.videoCountText)
                        }
                    }

                    if (metaLine.isNotBlank()) {
                        Text(
                            text = metaLine,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Description preview snippet
                    if (channel.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = channel.description,
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subscribe action button matching official YouTube styling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isSubscribed) Color(0xFF272727) else Color.White
                    )
                    .clickable {
                        scope.launch {
                            if (isSubscribed) {
                                repository.unsubscribe(channel.id)
                            } else {
                                repository.subscribe(
                                    SubscriptionEntity(
                                        channelId = channel.id,
                                        channelTitle = channel.title,
                                        channelHandle = channel.handle,
                                        channelAvatarUrl = channel.avatarUrl,
                                        subscriberCountText = channel.subscriberCountText
                                    )
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSubscribed) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.channel_subscribed),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.channel_subscribe),
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
