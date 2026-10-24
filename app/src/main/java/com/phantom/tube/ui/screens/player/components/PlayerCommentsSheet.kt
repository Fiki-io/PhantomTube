package com.phantom.tube.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.data.model.VideoComment
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.R
import com.phantom.tube.ui.components.PhantomLinkText
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

@Composable
fun CommentItemRow(
    comment: VideoComment,
    onTimestampClick: ((Float) -> Unit)? = null,
    onChannelClick: ((String, String) -> Unit)? = null,
    onVideoLinkClick: ((VideoItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val authorTargetChannel = comment.authorChannelId.ifBlank {
        comment.authorHandle.takeIf { it.startsWith("@") }.orEmpty()
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Author Avatar
        if (comment.authorAvatarUrl.isNotBlank()) {
            AsyncImage(
                model = comment.authorAvatarUrl,
                contentDescription = comment.authorName,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(
                        if (authorTargetChannel.isNotBlank() && onChannelClick != null) {
                            Modifier.clickable {
                                onChannelClick(authorTargetChannel, comment.authorName.ifBlank { authorTargetChannel })
                            }
                        } else Modifier
                    ),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333))
                    .then(
                        if (authorTargetChannel.isNotBlank() && onChannelClick != null) {
                            Modifier.clickable {
                                onChannelClick(authorTargetChannel, comment.authorName.ifBlank { authorTargetChannel })
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.authorName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Author handle + Published time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorHandle.ifBlank { comment.authorName },
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = if (authorTargetChannel.isNotBlank() && onChannelClick != null) {
                        Modifier.clickable {
                            onChannelClick(authorTargetChannel, comment.authorName.ifBlank { authorTargetChannel })
                        }
                    } else Modifier
                )
                if (comment.publishedTimeText.isNotBlank()) {
                    Text(
                        text = " • ${comment.publishedTimeText}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Comment content with clickable URLs, timestamps, and @handles
            PhantomLinkText(
                text = comment.contentText,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                onTimestampClick = onTimestampClick,
                onChannelClick = onChannelClick,
                onVideoLinkClick = onVideoLinkClick
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Actions: Like count, Dislike, Reply
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = stringResource(R.string.player_stat_likes),
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    if (comment.likeCountText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = comment.likeCountText,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ThumbDown,
                    contentDescription = stringResource(R.string.player_dislike),
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = stringResource(R.string.player_reply),
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Reply Count Link
            if (comment.replyCountText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.player_replies_count, comment.replyCountText),
                    color = Color(0xFF3EA6FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun PlayerCommentsSheet(
    visible: Boolean,
    isFullscreen: Boolean,
    commentsList: List<VideoComment>,
    commentsTotalCountText: String,
    isLoadingComments: Boolean,
    hasMoreComments: Boolean,
    onLoadMore: () -> Unit,
    onTimestampClick: ((Float) -> Unit)? = null,
    onChannelClick: ((String, String) -> Unit)? = null,
    onVideoLinkClick: ((VideoItem) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commentsSheetOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedCommentsSheetOffsetY by animateFloatAsState(
        targetValue = commentsSheetOffsetY,
        animationSpec = tween(durationMillis = 200),
        label = "comments_sheet_offset"
    )

    LaunchedEffect(visible) {
        if (visible) {
            commentsSheetOffsetY = 0f
        }
    }

    val commentsHandleDragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                commentsSheetOffsetY = (commentsSheetOffsetY + dragAmount).coerceAtLeast(0f)
            },
            onDragEnd = {
                if (commentsSheetOffsetY > 140f) {
                    onDismiss()
                } else {
                    commentsSheetOffsetY = 0f
                }
            },
            onDragCancel = {
                commentsSheetOffsetY = 0f
            }
        )
    }

    AnimatedVisibility(
        visible = visible && !isFullscreen,
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(animationSpec = tween(100)) + slideOutVertically(animationSpec = tween(150, easing = FastOutLinearInEasing)) { it },
        modifier = modifier
    ) {
        val commentsLazyState = rememberLazyListState()

        // Infinite scroll for comments
        LaunchedEffect(commentsLazyState, hasMoreComments, isLoadingComments) {
            snapshotFlow {
                val info = commentsLazyState.layoutInfo
                val total = info.totalItemsCount
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && last >= total - 3
            }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    if (!isLoadingComments && hasMoreComments) {
                        onLoadMore()
                    }
                }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            // Invisible top spacer reserving the height of status bar + 16:9 Video Player Box
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .aspectRatio(16f / 9f)
            )

            // Sheet container filling the space below the video, NEVER overlapping the video
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset { IntOffset(0, animatedCommentsSheetOffsetY.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color(0xFF212121))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(commentsHandleDragModifier)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 38.dp, height = 4.dp)
                                .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )
                    }

                    // Header: Komentar + Count + Close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(commentsHandleDragModifier),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.player_comments),
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (commentsTotalCountText.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = commentsTotalCountText,
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(1.dp)
                            .background(Color(0xFF2E2E2E))
                    )

                    // Comments List
                    if (commentsList.isEmpty() && isLoadingComments) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = YouTubeRed,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else if (commentsList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.player_no_comments_sheet),
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = commentsLazyState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
                        ) {
                            items(
                                items = commentsList,
                                key = { it.id.ifBlank { "${it.authorName}_${it.contentText.hashCode()}" } },
                                contentType = { "comment" }
                            ) { comment ->
                                CommentItemRow(
                                    comment = comment,
                                    onTimestampClick = onTimestampClick,
                                    onChannelClick = onChannelClick,
                                    onVideoLinkClick = onVideoLinkClick
                                )
                            }

                            if (isLoadingComments) {
                                item(key = "loading_more_comments", contentType = "loader") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = YouTubeRed,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
