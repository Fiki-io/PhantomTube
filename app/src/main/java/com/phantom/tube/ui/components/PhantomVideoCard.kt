package com.phantom.tube.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.phantom.tube.core.image.PhantomAsyncImage
import com.phantom.tube.core.image.PhantomImageRequest
import com.phantom.tube.R
import com.phantom.tube.PhantomApp
import com.phantom.tube.core.theme.ControlSurfaceMuted
import com.phantom.tube.core.theme.GlassSheetBgTop
import com.phantom.tube.core.theme.GlassSheetTopRim
import com.phantom.tube.core.theme.MediaPlaceholderBg
import com.phantom.tube.core.theme.OverlayBadgeScrim
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.util.safeStartActivity
import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.ui.screens.player.components.DownloadFormatSheet

/**
 * Kartu item video standar:
 * - Thumbnail 16:9 dengan durasi
 * - Indikator progres tonton riil
 * - Avatar channel
 * - Judul video, nama channel, dan info penayangan
 * - Tombol opsi
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhantomVideoCard(
    video: VideoItem,
    modifier: Modifier = Modifier,
    progressFraction: Float = 0f,
    onChannelClick: ((String) -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onDownloadClick: ((VideoItem) -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    val repository = remember(context) {
        (context.applicationContext as? PhantomApp)?.repository
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_card_${video.id}")
            .clip(RoundedCornerShape(12.dp))
            .iosBounceClick(
                scaleDown = 0.975f,
                alphaDown = 0.95f,
                onClick = onClick
            )
            .padding(bottom = 12.dp)
    ) {
        // Thumbnail
        val thumbRequest = remember(video.thumbnailUrl, context) {
            PhantomImageRequest.Builder(context)
                .data(video.thumbnailUrl)
                .size(384, 216)
                .allowRgb565(true)
                .build()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MediaPlaceholderBg)
        ) {
            PhantomAsyncImage(
                model = thumbRequest,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            // Durasi video atau Badge Playlist
            if (video.isPlaylist || !video.playlistId.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp)
                        .background(
                            color = OverlayBadgeScrim,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = PhantomIcons.QueueMusic,
                            contentDescription = stringResource(R.string.player_playlist_badge),
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = video.durationText.ifBlank { stringResource(R.string.player_playlist_badge) },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            } else if (video.durationText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp)
                        .background(
                            color = OverlayBadgeScrim,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.durationText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White
                    )
                }
            }

            // Real Watch Progress Bar from SQLite history
            if (progressFraction > 0f) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progressFraction.coerceIn(0f, 1f),
                    animationSpec = IosSpringSpecs.Gentle,
                    label = "video_card_progress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(OverlayBadgeScrim)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(YouTubeRed)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Info: Avatar + Detail
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            val targetChannelId = video.channelId.ifBlank { video.channelTitle }
            var avatarUrl by remember(video.id, video.channelAvatarUrl) {
                val clean = InnerTubeHelpers.normalizeUrl(video.channelAvatarUrl)
                mutableStateOf(clean.ifBlank { ChannelAvatarCache.get(video.channelId, video.channelTitle) })
            }

            LaunchedEffect(video.id, video.channelId, video.channelTitle, avatarUrl) {
                if (avatarUrl.isBlank() && video.channelId.isNotBlank() && video.channelId.startsWith("UC")) {
                    val resolved = ChannelAvatarCache.resolveAvatar(video.channelId, video.channelTitle)
                    if (!resolved.isNullOrBlank()) {
                        avatarUrl = resolved
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ControlSurfaceMuted)
                    .then(
                        if (onChannelClick != null && targetChannelId.isNotBlank()) {
                            Modifier.iosBounceClick(scaleDown = 0.90f) { onChannelClick(targetChannelId) }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl.isNotBlank()) {
                    val avatarRequest = remember(avatarUrl, context) {
                        PhantomImageRequest.Builder(context)
                            .data(avatarUrl)
                            .size(72, 72)
                            .allowRgb565(true)
                            .build()
                    }
                    PhantomAsyncImage(
                        model = avatarRequest,
                        contentDescription = video.channelTitle,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (video.isPlaylist || !video.playlistId.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(ControlSurfaceMuted, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.player_mix_badge),
                            tint = YouTubeRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    val initials = video.channelTitle.trim().take(1).uppercase()
                    if (initials.isNotBlank()) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
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
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    ),
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                val metaString = InnerTubeHelpers.formatVideoMeta(
                    channelTitle = video.channelTitle,
                    viewCountText = video.viewCountText,
                    publishedTimeText = video.publishedTimeText
                )

                Text(
                    text = metaString,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("video_more_${video.id}")
                        .iosBounceClick(scaleDown = 0.86f) {
                            if (onMoreClick != null) {
                                onMoreClick()
                            } else {
                                showMenu = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(GlassSheetBgTop)
                        .border(0.85.dp, GlassSheetTopRim, RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.player_download),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = PhantomIcons.Download,
                                contentDescription = stringResource(R.string.player_download),
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            if (onDownloadClick != null) {
                                onDownloadClick(video)
                            } else {
                                showDownloadSheet = true
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.player_share),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.player_share),
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, video.title)
                                putExtra(Intent.EXTRA_TEXT, "${video.title}\nhttps://youtu.be/${video.id}")
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.safeStartActivity(shareIntent)
                        }
                    )
                }
            }
        }
    }

    if (showDownloadSheet && repository != null) {
        DownloadFormatSheet(
            video = video,
            repository = repository,
            onDismiss = { showDownloadSheet = false }
        )
    }
}
