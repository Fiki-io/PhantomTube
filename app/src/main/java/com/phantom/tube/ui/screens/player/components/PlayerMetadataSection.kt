package com.phantom.tube.ui.screens.player.components

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.widget.Toast
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
import com.phantom.tube.core.image.PhantomAsyncImage
import com.phantom.tube.core.image.PhantomImageRequest
import com.phantom.tube.R
import com.phantom.tube.core.database.SubscriptionEntity
import com.phantom.tube.core.theme.ActionActiveGreen
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.ChapterActiveBg
import com.phantom.tube.core.theme.ControlBorderSubtle
import com.phantom.tube.core.theme.ControlSurfaceMuted
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.PlayerOverlayControlBorder
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurfaceLight
import com.phantom.tube.core.util.safeStartActivity
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.VideoComment
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.ui.components.IosSpringSpecs
import com.phantom.tube.ui.components.PhantomMotion
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
        val cleanHandle = nextQueueData?.channelHandle?.trim().orEmpty()
        val activeHandle = if (cleanHandle.isBlank()) "" else if (cleanHandle.startsWith("@")) cleanHandle else "@$cleanHandle"
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

        val parsedChapters = remember(nextQueueData?.description) {
            com.phantom.tube.data.model.VideoChapterParser.parse(nextQueueData?.description.orEmpty())
        }

        if (parsedChapters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlassSurface)
                    .border(0.75.dp, ControlBorderSubtle, RoundedCornerShape(10.dp))
                    .iosBounceClick(scaleDown = 0.94f, onClick = onOpenDescription)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = ChapterActiveBg,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${stringResource(R.string.player_chapters)} • ${parsedChapters.size}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
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
                    .heightIn(min = 48.dp)
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
                        val channelAvatarRequest = remember(currentAvatar, context) {
                            PhantomImageRequest.Builder(context)
                                .data(currentAvatar)
                                .size(72, 72)
                                .allowRgb565(true)
                                .build()
                        }
                        PhantomAsyncImage(
                            model = channelAvatarRequest,
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
                        text = targetChannelTitle.ifBlank { stringResource(R.string.player_channel_label) },
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

            val subBg by animateColorAsState(
                targetValue = if (isSubscribed) ControlSurfaceMuted else Color.White,
                animationSpec = IosSpringSpecs.ColorFade,
                label = "player_sub_bg"
            )
            val subTextColor by animateColorAsState(
                targetValue = if (isSubscribed) Color.White else Color.Black,
                animationSpec = IosSpringSpecs.ColorFade,
                label = "player_sub_text"
            )
            val subBorder by animateColorAsState(
                targetValue = if (isSubscribed) ControlBorderSubtle else Color.Transparent,
                animationSpec = IosSpringSpecs.ColorFade,
                label = "player_sub_border"
            )
            val actionPillShape = remember { RoundedCornerShape(20.dp) }

            Box(
                modifier = Modifier
                    .clip(actionPillShape)
                    .background(subBg)
                    .border(0.75.dp, subBorder, actionPillShape)
                    .animateContentSize(animationSpec = IosSpringSpecs.TopBarSize)
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
                    color = subTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val isFavorite by repository.isFavorite(video.id).collectAsState(initial = false)
        var isDisliked by remember(video.id) { mutableStateOf(false) }
        val resolvedVideoForFav = remember(video, activeTitle, targetChannelTitle, targetChannelId) {
            video.copy(
                title = activeTitle,
                channelTitle = targetChannelTitle,
                channelId = targetChannelId,
                thumbnailUrl = nextQueueData?.currentVideo?.thumbnailUrl?.ifBlank { video.thumbnailUrl } ?: video.thumbnailUrl,
                durationText = nextQueueData?.currentVideo?.durationText?.ifBlank { video.durationText } ?: video.durationText
            )
        }

        // 4. Action Buttons Row (Like/Dislike, Share, Download)
        val pillShape = remember { RoundedCornerShape(20.dp) }
        val likeTint by animateColorAsState(
            targetValue = if (isFavorite) ChapterActiveBg else TextPrimary,
            animationSpec = IosSpringSpecs.ColorFade,
            label = "player_like_tint"
        )
        val dislikeTint by animateColorAsState(
            targetValue = if (isDisliked) ChapterActiveBg else TextPrimary,
            animationSpec = IosSpringSpecs.ColorFade,
            label = "player_dislike_tint"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .background(ControlSurfaceMuted)
                    .border(0.6.dp, ControlBorderSubtle, pillShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .iosBounceClick(scaleDown = 0.94f) {
                            scope.launch {
                                val wasFav = isFavorite
                                if (!wasFav) {
                                    isDisliked = false
                                }
                                repository.toggleFavorite(resolvedVideoForFav, wasFav)
                                Toast.makeText(
                                    context,
                                    context.getString(if (wasFav) R.string.player_removed else R.string.player_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = isFavorite,
                        transitionSpec = { PhantomMotion.IconSwapTransition },
                        label = "player_like_icon"
                    ) { _ ->
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = stringResource(R.string.player_like),
                            tint = likeTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeLikeCount.ifBlank { stringResource(R.string.player_like) },
                        color = likeTint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(PlayerOverlayControlBorder)
                )
                Box(
                    modifier = Modifier
                        .iosBounceClick(scaleDown = 0.92f) {
                            val nextDislike = !isDisliked
                            isDisliked = nextDislike
                            if (nextDislike && isFavorite) {
                                scope.launch {
                                    repository.toggleFavorite(resolvedVideoForFav, true)
                                }
                            }
                        }
                        .padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isDisliked,
                        transitionSpec = { PhantomMotion.IconSwapTransition },
                        label = "player_dislike_icon"
                    ) { _ ->
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = stringResource(R.string.player_dislike),
                            tint = dislikeTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .background(ControlSurfaceMuted)
                    .border(0.6.dp, ControlBorderSubtle, pillShape)
                    .iosBounceClick(scaleDown = 0.92f) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, activeTitle)
                            putExtra(Intent.EXTRA_TEXT, "$activeTitle\nhttps://youtu.be/${video.id}")
                        }
                        context.safeStartActivity(Intent.createChooser(sendIntent, null))
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
            val downloadTint by animateColorAsState(
                targetValue = if (isDownloaded) ActionActiveGreen else TextPrimary,
                animationSpec = IosSpringSpecs.ColorFade,
                label = "player_download_tint"
            )
            val downloadBorder by animateColorAsState(
                targetValue = if (isDownloaded) ActionActiveGreen.copy(alpha = 0.45f) else ControlBorderSubtle,
                animationSpec = IosSpringSpecs.ColorFade,
                label = "player_download_border"
            )
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .background(ControlSurfaceMuted)
                    .border(0.6.dp, downloadBorder, pillShape)
                    .animateContentSize(animationSpec = IosSpringSpecs.TopBarSize)
                    .iosBounceClick(scaleDown = 0.92f, onClick = onOpenDownload)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = isDownloaded,
                    transitionSpec = { PhantomMotion.IconSwapTransition },
                    label = "player_download_icon"
                ) { downloaded ->
                    Icon(
                        imageVector = if (downloaded) Icons.Default.Check else Icons.Default.Download,
                        contentDescription = stringResource(if (downloaded) R.string.player_downloaded else R.string.player_download),
                        tint = downloadTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(if (isDownloaded) R.string.player_downloaded else R.string.player_download),
                    color = downloadTint,
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
                .animateContentSize(animationSpec = IosSpringSpecs.TopBarSize)
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
                        val commentAvatarRequest = remember(topCommentAvatar, context) {
                            PhantomImageRequest.Builder(context)
                                .data(topCommentAvatar)
                                .size(72, 72)
                                .allowRgb565(true)
                                .build()
                        }
                        PhantomAsyncImage(
                            model = commentAvatarRequest,
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
