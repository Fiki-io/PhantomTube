package com.phantom.tube.ui.screens.channel.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phantom.tube.R
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.GlassBorderDim
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeDark
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.data.model.ChannelProfile
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.ui.components.iosBounceClick

enum class ChannelTabType(val labelRes: Int) {
    HOME(R.string.channel_tab_home),
    VIDEOS(R.string.channel_tab_videos),
    ABOUT(R.string.channel_about)
}

enum class ChannelSortOrder(val labelRes: Int) {
    LATEST(R.string.channel_sort_latest),
    POPULAR(R.string.channel_sort_popular),
    OLDEST(R.string.channel_sort_oldest)
}

@Composable
fun ChannelTopBar(
    title: String,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    onShareChannel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(YouTubeDark.copy(alpha = 0.96f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (isSearchActive) onToggleSearch() else onBack()
            },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GlassSurface)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onToggleSearch,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GlassSurface)
        ) {
            Icon(
                imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription = stringResource(R.string.search_hint),
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onShareChannel,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GlassSurface)
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = stringResource(R.string.player_share),
                tint = TextPrimary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
fun ChannelHeaderSection(
    profile: ChannelProfile,
    isSubscribed: Boolean,
    onToggleSubscribe: () -> Unit,
    onOpenAboutSheet: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (profile.bannerUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(106.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardBackground)
            ) {
                AsyncImage(
                    model = profile.bannerUrl,
                    contentDescription = profile.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.35f)
                                )
                            )
                        )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CardBackground)
                    .border(2.dp, GlassBorderLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (profile.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = profile.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = profile.title.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = YouTubeRed
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (profile.isVerified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Verified",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val handleAndMeta = buildString {
                    if (profile.handle.isNotBlank()) {
                        append(profile.handle)
                    }
                    if (profile.subscriberCountText.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(profile.subscriberCountText)
                    }
                    if (profile.videoCountText.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(profile.videoCountText)
                    }
                }
                if (handleAndMeta.isNotBlank()) {
                    Text(
                        text = handleAndMeta,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (profile.description.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAboutSheet)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profile.description.replace("\n", " "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.player_more_details),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val subBgColor by animateColorAsState(
            targetValue = if (isSubscribed) GlassSurface else Color.White,
            animationSpec = tween(200),
            label = "sub_btn_bg"
        )
        val subTextColor by animateColorAsState(
            targetValue = if (isSubscribed) TextPrimary else Color.Black,
            animationSpec = tween(200),
            label = "sub_btn_txt"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(subBgColor)
                    .border(
                        width = if (isSubscribed) 1.dp else 0.dp,
                        color = if (isSubscribed) GlassBorderLight else Color.Transparent,
                        shape = RoundedCornerShape(21.dp)
                    )
                    .iosBounceClick(scaleDown = 0.96f, onClick = onToggleSubscribe),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSubscribed) Icons.Rounded.NotificationsActive else Icons.Rounded.Check,
                        contentDescription = null,
                        tint = subTextColor,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSubscribed) {
                            stringResource(R.string.channel_subscribed)
                        } else {
                            stringResource(R.string.channel_subscribe)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = subTextColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun ChannelTabBar(
    tabs: List<ChannelTabType>,
    selectedTab: ChannelTabType,
    onSelectTab: (ChannelTabType) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else GlassSurface,
                    animationSpec = tween(180),
                    label = "ch_tab_bg"
                )
                val txtColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else TextSecondary,
                    animationSpec = tween(180),
                    label = "ch_tab_txt"
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(bgColor)
                        .border(
                            width = if (isSelected) 0.dp else 1.dp,
                            color = if (isSelected) Color.Transparent else GlassBorderLight,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = txtColor
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = GlassBorderDim
        )
    }
}

@Composable
fun ChannelSortFilterBar(
    selectedSort: ChannelSortOrder,
    onSelectSort: (ChannelSortOrder) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChannelSortOrder.entries.forEach { order ->
            val isSelected = order == selectedSort
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) YouTubeRed.copy(alpha = 0.16f) else GlassSurface)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) YouTubeRed else GlassBorderLight,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectSort(order) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(order.labelRes),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) YouTubeRed else TextSecondary
                )
            }
        }
    }
}

@Composable
fun ChannelCompactVideoRow(
    video: VideoItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosBounceClick(scaleDown = 0.985f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(156.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBackground)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (video.durationText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.durationText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                ),
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val meta = listOf(video.viewCountText, video.publishedTimeText)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChannelAboutCard(
    profile: ChannelProfile,
    onChannelClick: ((String, String) -> Unit)? = null,
    onVideoLinkClick: ((VideoItem) -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, GlassBorderLight, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = YouTubeSurface
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.channel_about_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (profile.description.isNotBlank()) {
                com.phantom.tube.ui.components.PhantomLinkText(
                    text = profile.description,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = TextSecondary,
                    onChannelClick = onChannelClick,
                    onVideoLinkClick = onVideoLinkClick
                )
            } else {
                Text(
                    text = stringResource(R.string.channel_no_description),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = TextSecondary
                )
            }

            if (profile.externalLinksText.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = GlassBorderDim)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.channel_links),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                com.phantom.tube.ui.components.PhantomLinkText(
                    text = profile.externalLinksText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    linkColor = YouTubeRed,
                    onChannelClick = onChannelClick,
                    onVideoLinkClick = onVideoLinkClick
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = GlassBorderDim)
            Spacer(modifier = Modifier.height(14.dp))

            val details = mutableListOf<Pair<String, String>>()
            if (profile.handle.isNotBlank()) details.add("Handle" to profile.handle)
            if (profile.subscriberCountText.isNotBlank()) details.add("Subscribers" to profile.subscriberCountText)
            if (profile.videoCountText.isNotBlank()) details.add("Videos" to profile.videoCountText)
            if (profile.id.isNotBlank()) details.add("Channel ID" to profile.id)

            details.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelEmptyStateBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelAboutBottomSheet(
    profile: ChannelProfile,
    onChannelClick: ((String, String) -> Unit)? = null,
    onVideoLinkClick: ((VideoItem) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = YouTubeSurface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        ChannelAboutCard(
            profile = profile,
            onChannelClick = onChannelClick,
            onVideoLinkClick = onVideoLinkClick
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}
