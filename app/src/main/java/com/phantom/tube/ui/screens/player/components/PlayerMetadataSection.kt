package com.phantom.tube.ui.screens.player.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phantom.tube.R
import com.phantom.tube.core.database.SubscriptionEntity
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurfaceLight
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.VideoComment
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.ui.components.iosBounceClick
import kotlinx.coroutines.launch

@Composable
fun PlayerMetadataSection(
    video: VideoItem,
    nextQueueData: NextQueue?,
    activeAvatarUrl: String,
    commentsList: List<VideoComment>,
    commentsTotalCountText: String,
    repository: PhantomRepository,
    onOpenDescription: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenDownload: () -> Unit,
    onChannelSelected: (channelId: String, channelTitle: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Video Title
        val activeTitle = nextQueueData?.currentVideo?.title?.ifBlank { video.title } ?: video.title
        Text(
            text = activeTitle,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Sub-info row (Handle • Likes • Views • Date • ...selengkapnya)
        val activeHandle = nextQueueData?.channelHandle?.let { if (it.startsWith("@")) it else "@$it" } ?: ""
        val activeViews = InnerTubeHelpers.normalizeViewCount(
            nextQueueData?.fullViewCountText?.ifBlank { video.viewCountText } ?: video.viewCountText
        )
        val activeDate = InnerTubeHelpers.normalizePublishedTime(
            nextQueueData?.dateText?.ifBlank { video.publishedTimeText } ?: video.publishedTimeText
        )
        val activeLikeCount = nextQueueData?.likeCountText ?: ""

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDescription),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                buildAnnotatedString {
                    if (activeHandle.isNotBlank()) {
                        append(activeHandle)
                        append(" • ")
                    }
                    if (activeLikeCount.isNotBlank()) {
                        append(activeLikeCount)
                        append(" ")
                        append(stringResource(R.string.player_stat_likes).lowercase())
                        append(" • ")
                    }
                    if (activeViews.isNotBlank()) {
                        append("$activeViews • ")
                    }
                    if (activeDate.isNotBlank()) {
                        append("$activeDate • ")
                    }
                    withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.player_more_details))
                    }
                },
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Channel Row (Avatar, Name, Subscriber count, Subscribe button)
        val targetChannelId = nextQueueData?.currentVideo?.channelId?.ifBlank { video.channelId } ?: video.channelId
        val targetChannelTitle = nextQueueData?.currentVideo?.channelTitle?.ifBlank { video.channelTitle } ?: video.channelTitle
        val isSubscribed by repository.isSubscribed(targetChannelId).collectAsState(initial = false)
        val currentAvatar = nextQueueData?.currentVideo?.channelAvatarUrl?.ifBlank { activeAvatarUrl } ?: activeAvatarUrl

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .iosBounceClick(scaleDown = 0.96f) {
                        val tId = targetChannelId.ifBlank { targetChannelTitle }
                        if (tId.isNotBlank()) {
                            onChannelSelected(tId, targetChannelTitle)
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(YouTubeSurfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentAvatar.isNotBlank()) {
                        AsyncImage(
                            model = currentAvatar,
                            contentDescription = targetChannelTitle,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = YouTubeRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = targetChannelTitle.ifBlank { "Channel" },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subsText = nextQueueData?.channelSubscriberCountText ?: ""
                    if (subsText.isNotBlank()) {
                        Text(
                            text = subsText,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSubscribed) Color(0xFF272727) else Color.White)
                    .iosBounceClick(scaleDown = 0.90f) {
                        scope.launch {
                            if (isSubscribed) {
                                repository.unsubscribe(targetChannelId)
                            } else {
                                repository.subscribe(
                                    SubscriptionEntity(
                                        channelId = targetChannelId,
                                        channelTitle = targetChannelTitle,
                                        channelHandle = nextQueueData?.channelHandle ?: "",
                                        channelAvatarUrl = currentAvatar,
                                        subscriberCountText = nextQueueData?.channelSubscriberCountText ?: ""
                                    )
                                )
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = if (isSubscribed) stringResource(R.string.channel_subscribed) else stringResource(R.string.channel_subscribe),
                    color = if (isSubscribed) Color.White else Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Action Buttons Row (Like/Dislike, Share, Download)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF272727))
                    .iosBounceClick(scaleDown = 0.94f) {
                        onOpenDescription()
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = stringResource(R.string.player_like),
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = activeLikeCount.ifBlank { stringResource(R.string.player_like) },
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color(0x33FFFFFF))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.ThumbDown,
                    contentDescription = stringResource(R.string.player_dislike),
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF272727))
                    .iosBounceClick(scaleDown = 0.92f) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, activeTitle)
                            putExtra(Intent.EXTRA_TEXT, "$activeTitle\nhttps://youtu.be/${video.id}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(shareIntent)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.player_share),
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.player_share),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val isDownloaded by repository.isDownloaded(video.id).collectAsState(initial = false)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF272727))
                    .iosBounceClick(scaleDown = 0.92f, onClick = onOpenDownload)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.Download,
                    contentDescription = stringResource(if (isDownloaded) R.string.player_downloaded else R.string.player_download),
                    tint = if (isDownloaded) Color(0xFF4CAF50) else TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(if (isDownloaded) R.string.player_downloaded else R.string.player_download),
                    color = if (isDownloaded) Color(0xFF4CAF50) else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Comments Preview Card
        val totalCommentsDisplay = commentsTotalCountText.ifBlank {
            nextQueueData?.commentsCountText ?: ""
        }
        val topCommentSnippet = commentsList.firstOrNull()?.contentText
            ?: nextQueueData?.topComment?.contentText
            ?: stringResource(R.string.player_comments_hint)
        val topCommentAvatar = commentsList.firstOrNull()?.authorAvatarUrl
            ?: nextQueueData?.topComment?.authorAvatarUrl
            ?: ""

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .iosBounceClick(scaleDown = 0.97f, onClick = onOpenComments)
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.player_comments),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (totalCommentsDisplay.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = totalCommentsDisplay,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (topCommentAvatar.isNotBlank()) {
                        AsyncImage(
                            model = topCommentAvatar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = topCommentSnippet,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
