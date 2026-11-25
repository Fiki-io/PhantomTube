package com.phantom.tube.ui.screens.channel.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.core.image.PhantomAsyncImage
import com.phantom.tube.core.image.PhantomImageRequest
import com.phantom.tube.R
import com.phantom.tube.core.theme.BubblePillGlassBorderBottom
import com.phantom.tube.core.theme.BubblePillGlassBorderTop
import com.phantom.tube.core.theme.BubblePillGlassBottom
import com.phantom.tube.core.theme.BubblePillGlassTop
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.GlassBorderDim
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.GlassHandleColor
import com.phantom.tube.core.theme.GlassSheetScrim
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.OverlayBadgeScrim
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeDark
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.core.theme.phantomGlassSheet
import com.phantom.tube.core.theme.phantomGlassSurface
import com.phantom.tube.data.model.ChannelProfile
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.ui.components.IosSpringSpecs
import com.phantom.tube.ui.components.PhantomMotion
import com.phantom.tube.ui.components.iosBounceClick
import kotlin.math.abs
import kotlin.math.roundToInt

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
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .testTag("channel_back_btn")
                .iosBounceClick(scaleDown = 0.88f) {
                    if (isSearchActive) onToggleSearch() else onBack()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
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
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .testTag("channel_search_btn")
                .iosBounceClick(scaleDown = 0.88f, onClick = onToggleSearch),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassSurface),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = { PhantomMotion.IconSwapTransition },
                    label = "channel_search_icon"
                ) { active ->
                    Icon(
                        imageVector = if (active) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search_hint),
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .testTag("channel_share_btn")
                .iosBounceClick(scaleDown = 0.88f, onClick = onShareChannel),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassSurface),
                contentAlignment = Alignment.Center
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
}

@Composable
fun ChannelHeaderSection(
    profile: ChannelProfile,
    isSubscribed: Boolean,
    onToggleSubscribe: () -> Unit,
    onOpenAboutSheet: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (profile.bannerUrl.isNotBlank()) {
            val bannerRequest = remember(profile.bannerUrl, context) {
                PhantomImageRequest.Builder(context)
                    .data(profile.bannerUrl)
                    .size(800, 260)
                    .allowRgb565(true)
                    .build()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(106.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardBackground)
            ) {
                PhantomAsyncImage(
                    model = bannerRequest,
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
                    val avatarRequest = remember(profile.avatarUrl, context) {
                        PhantomImageRequest.Builder(context)
                            .data(profile.avatarUrl)
                            .size(160, 160)
                            .allowRgb565(true)
                            .build()
                    }
                    PhantomAsyncImage(
                        model = avatarRequest,
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
                            contentDescription = stringResource(R.string.channel_verified),
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
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onOpenAboutSheet)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
            animationSpec = IosSpringSpecs.ColorFade,
            label = "sub_btn_bg"
        )
        val subTextColor by animateColorAsState(
            targetValue = if (isSubscribed) TextPrimary else Color.Black,
            animationSpec = IosSpringSpecs.ColorFade,
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
                    .heightIn(min = 48.dp)
                    .testTag("channel_subscribe_btn")
                    .clip(RoundedCornerShape(24.dp))
                    .background(subBgColor)
                    .border(
                        width = if (isSubscribed) 1.dp else 0.dp,
                        color = if (isSubscribed) GlassBorderLight else Color.Transparent,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .iosBounceClick(scaleDown = 0.96f, onClick = onToggleSubscribe),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(
                        targetState = isSubscribed,
                        transitionSpec = { PhantomMotion.IconSwapTransition },
                        label = "channel_sub_icon"
                    ) { subscribed ->
                        Icon(
                            imageVector = if (subscribed) Icons.Rounded.NotificationsActive else Icons.Rounded.Check,
                            contentDescription = null,
                            tint = subTextColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
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
    if (tabs.isEmpty()) return
    val activeIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val trackShape = remember { RoundedCornerShape(26.dp) }
    val pillShape = remember { RoundedCornerShape(22.dp) }
    val activePillBrush = remember {
        Brush.verticalGradient(colors = listOf(BubblePillGlassTop, BubblePillGlassBottom))
    }
    val activePillBorderBrush = remember {
        Brush.verticalGradient(colors = listOf(BubblePillGlassBorderTop, BubblePillGlassBorderBottom))
    }

    val animatedIndex by animateFloatAsState(
        targetValue = activeIndex.toFloat(),
        animationSpec = IosSpringSpecs.DockPillGlide,
        label = "channel_tab_pill_pos"
    )
    val travelStretch = abs(activeIndex.toFloat() - animatedIndex).coerceIn(0f, 1f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(48.dp)
                .phantomGlassSurface(shape = trackShape, shadowElevation = 6.dp)
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val tabWidthPx = totalWidthPx / tabs.size

                Box(
                    modifier = Modifier
                        .offset { IntOffset((animatedIndex * tabWidthPx).roundToInt(), 0) }
                        .width(maxWidth / tabs.size)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                        .graphicsLayer {
                            scaleX = 1f + (travelStretch * 0.10f)
                            scaleY = 1f - (travelStretch * 0.06f)
                        }
                        .clip(pillShape)
                        .background(activePillBrush)
                        .border(0.75.dp, activePillBorderBrush, pillShape)
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = tab == selectedTab
                        val txtColor by animateColorAsState(
                            targetValue = if (isSelected) TextPrimary else TextSecondary,
                            animationSpec = IosSpringSpecs.ColorFade,
                            label = "ch_tab_txt"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(pillShape)
                                .testTag("channel_tab_${tab.name.lowercase()}")
                                .iosBounceClick(scaleDown = 0.94f) {
                                    onSelectTab(tab)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(tab.labelRes),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = txtColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) YouTubeRed.copy(alpha = 0.16f) else GlassSurface,
                animationSpec = IosSpringSpecs.ColorFade,
                label = "ch_sort_bg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) YouTubeRed else GlassBorderLight,
                animationSpec = IosSpringSpecs.ColorFade,
                label = "ch_sort_border"
            )
            val txtColor by animateColorAsState(
                targetValue = if (isSelected) YouTubeRed else TextSecondary,
                animationSpec = IosSpringSpecs.ColorFade,
                label = "ch_sort_txt"
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .iosBounceClick(scaleDown = 0.94f) { onSelectSort(order) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(order.labelRes),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = txtColor
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
    val context = LocalContext.current
    val thumbRequest = remember(video.thumbnailUrl, context) {
        PhantomImageRequest.Builder(context)
            .data(video.thumbnailUrl)
            .size(320, 180)
            .allowRgb565(true)
            .build()
    }
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
            PhantomAsyncImage(
                model = thumbRequest,
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
                        .background(OverlayBadgeScrim)
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
            .widthIn(max = 760.dp)
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
            if (profile.handle.isNotBlank()) details.add(stringResource(R.string.channel_handle_label) to profile.handle)
            if (profile.subscriberCountText.isNotBlank()) details.add(stringResource(R.string.channel_subscribers_label) to profile.subscriberCountText)
            if (profile.videoCountText.isNotBlank()) details.add(stringResource(R.string.channel_videos_label) to profile.videoCountText)
            if (profile.id.isNotBlank()) details.add(stringResource(R.string.channel_id_label) to profile.id)

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
    val sheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = GlassSheetScrim,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        dragHandle = null,
        shape = sheetShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .phantomGlassSheet(shape = sheetShape)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(GlassHandleColor)
                )
            }
            ChannelAboutCard(
                profile = profile,
                onChannelClick = onChannelClick,
                onVideoLinkClick = onVideoLinkClick
            )
        }
    }
}
