package com.phantom.tube.ui.screens.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phantom.tube.R
import com.phantom.tube.core.database.DownloadEntity
import com.phantom.tube.core.image.PhantomAsyncImage
import com.phantom.tube.core.image.PhantomImageRequest
import com.phantom.tube.core.theme.BubbleDockBorder
import com.phantom.tube.core.theme.BubblePillGlassBorderBottom
import com.phantom.tube.core.theme.BubblePillGlassBorderTop
import com.phantom.tube.core.theme.BubblePillGlassBottom
import com.phantom.tube.core.theme.BubblePillGlassTop
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.DownloadActiveRowBg
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.MediaPlaceholderBg
import com.phantom.tube.core.theme.MenuBorderSubtle
import com.phantom.tube.core.theme.MenuSurfaceDark
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.OverlayBadgeScrim
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.phantomGlassSurface
import com.phantom.tube.core.util.LocalNotificationPermissionRequester
import com.phantom.tube.core.util.safeStartActivity
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.player.offline.OfflineAudioPlayerManager
import com.phantom.tube.player.offline.OfflineRepeatMode
import com.phantom.tube.ui.components.IosSpringSpecs
import com.phantom.tube.ui.components.PhantomMotion
import com.phantom.tube.ui.components.iosBounceClick
import com.phantom.tube.ui.screens.player.components.OfflineVideoPlayerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DownloadFilter {
    ALL, AUDIO, VIDEO
}

/**
 * Layar lengkap Manajemen & Pemutaran Unduhan Offline (MP3 & MP4).
 */
@Composable
fun DownloadsScreen(
    repository: PhantomRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val requestNotificationPermission = LocalNotificationPermissionRequester.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val bottomNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val downloads by repository.getDownloads().collectAsState(initial = emptyList())
    val activeTrack by OfflineAudioPlayerManager.currentTrack.collectAsState()
    val isAudioPlaying by OfflineAudioPlayerManager.isPlaying.collectAsState()
    val repeatMode by OfflineAudioPlayerManager.repeatMode.collectAsState()
    val isShuffle by OfflineAudioPlayerManager.isShuffle.collectAsState()

    var selectedFilter by remember { mutableStateOf(DownloadFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var videoToPlayOffline by remember { mutableStateOf<DownloadEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    // Filter downloads based on category & search query
    val filteredList = remember(downloads, selectedFilter, searchQuery) {
        downloads.filter { item ->
            val matchesFilter = when (selectedFilter) {
                DownloadFilter.ALL -> true
                DownloadFilter.AUDIO -> item.format.equals("MP3", ignoreCase = true)
                DownloadFilter.VIDEO -> item.format.equals("MP4", ignoreCase = true)
            }
            val matchesQuery = if (searchQuery.isBlank()) true else {
                item.title.contains(searchQuery, ignoreCase = true) ||
                        item.channelTitle.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }

    val totalSizeMb = remember(downloads) {
        val totalBytes = downloads.sumOf { it.fileSize }
        String.format(Locale.getDefault(), "%.1f MB", totalBytes / (1024f * 1024f))
    }

    val audioTracks = remember(downloads) {
        downloads.filter { it.format.equals("MP3", ignoreCase = true) }
    }
    val audioCount = audioTracks.size
    val videoCount = remember(downloads) {
        downloads.count { it.format.equals("MP4", ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
    ) {
        com.phantom.tube.ui.components.PhantomStatusBarStrip()

        // TOP HEADER BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .testTag("downloads_back_btn")
                    .iosBounceClick(scaleDown = 0.88f, onClick = onBackClick),
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

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.downloads_title),
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.library_files_count, downloads.size, totalSizeMb),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            val searchIconTint by animateColorAsState(
                targetValue = if (isSearchActive) YouTubeRed else TextPrimary,
                animationSpec = IosSpringSpecs.ColorFade,
                label = "downloads_search_tint"
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .testTag("downloads_search_btn")
                    .iosBounceClick(scaleDown = 0.88f) {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
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
                    AnimatedContent(
                        targetState = isSearchActive,
                        transitionSpec = { PhantomMotion.IconSwapTransition },
                        label = "downloads_search_icon"
                    ) { active ->
                        Icon(
                            imageVector = if (active) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = stringResource(R.string.nav_search),
                            tint = searchIconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // SEARCH BAR FIELD
        AnimatedVisibility(
            visible = isSearchActive,
            enter = PhantomMotion.TopBarEnter,
            exit = PhantomMotion.TopBarExit
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.downloads_search_hint), color = TextMuted, fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = YouTubeRed,
                        unfocusedBorderColor = BubbleDockBorder,
                        cursorColor = YouTubeRed
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // LIQUID GLASS SEGMENTED FILTER BAR
        DownloadFilterBar(
            selectedFilter = selectedFilter,
            totalCount = downloads.size,
            audioCount = audioCount,
            videoCount = videoCount,
            onSelectFilter = { selectedFilter = it }
        )

        // PLAYBACK ACTION BAR (Putar Semua, Acak, Ulangi)
        AnimatedVisibility(
            visible = audioTracks.isNotEmpty() && (selectedFilter == DownloadFilter.ALL || selectedFilter == DownloadFilter.AUDIO),
            enter = PhantomMotion.TopBarEnter,
            exit = PhantomMotion.TopBarExit
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sleek Pill-shaped "Putar Semua" Button
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(YouTubeRed)
                        .testTag("downloads_play_all_btn")
                        .iosBounceClick(scaleDown = 0.94f) {
                            requestNotificationPermission()
                            val tracksToPlay = if (isShuffle) audioTracks.shuffled() else audioTracks
                            OfflineAudioPlayerManager.playQueue(context, tracksToPlay, 0)
                            Toast.makeText(context, context.getString(R.string.downloads_toast_playing, tracksToPlay.size), Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.downloads_play_all_count, audioTracks.size),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Shuffle & Repeat Quick Action Toggles
                val shuffleBg by animateColorAsState(
                    targetValue = if (isShuffle) YouTubeRed.copy(alpha = 0.22f) else GlassSurface,
                    animationSpec = IosSpringSpecs.ColorFade,
                    label = "downloads_shuffle_bg"
                )
                val shuffleBorder by animateColorAsState(
                    targetValue = if (isShuffle) YouTubeRed else BubbleDockBorder,
                    animationSpec = IosSpringSpecs.ColorFade,
                    label = "downloads_shuffle_border"
                )
                val shuffleTint by animateColorAsState(
                    targetValue = if (isShuffle) YouTubeRed else TextSecondary,
                    animationSpec = IosSpringSpecs.ColorFade,
                    label = "downloads_shuffle_tint"
                )
                val isRepeatActive = repeatMode != OfflineRepeatMode.OFF
                val repeatBg by animateColorAsState(
                    targetValue = if (isRepeatActive) YouTubeRed.copy(alpha = 0.22f) else GlassSurface,
                    animationSpec = IosSpringSpecs.ColorFade,
                    label = "downloads_repeat_bg"
                )
                val repeatBorder by animateColorAsState(
                    targetValue = if (isRepeatActive) YouTubeRed else BubbleDockBorder,
                    animationSpec = IosSpringSpecs.ColorFade,
                    label = "downloads_repeat_border"
                )
                val repeatTint by animateColorAsState(
                    targetValue = if (isRepeatActive) YouTubeRed else TextSecondary,
                    animationSpec = IosSpringSpecs.ColorFade,
                    label = "downloads_repeat_tint"
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(shuffleBg)
                            .border(1.dp, shuffleBorder, CircleShape)
                            .testTag("downloads_shuffle_btn")
                            .iosBounceClick(scaleDown = 0.90f) {
                                OfflineAudioPlayerManager.toggleShuffle(context)
                                Toast.makeText(
                                    context,
                                    if (!isShuffle) context.getString(R.string.downloads_shuffle_on) else context.getString(R.string.downloads_shuffle_off),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.downloads_shuffle_on),
                            tint = shuffleTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(repeatBg)
                            .border(1.dp, repeatBorder, CircleShape)
                            .testTag("downloads_repeat_btn")
                            .iosBounceClick(scaleDown = 0.90f) {
                                OfflineAudioPlayerManager.toggleRepeatMode(context)
                                val msg = when (OfflineAudioPlayerManager.repeatMode.value) {
                                    OfflineRepeatMode.OFF -> context.getString(R.string.downloads_repeat_off)
                                    OfflineRepeatMode.ALL -> context.getString(R.string.downloads_repeat_all)
                                    OfflineRepeatMode.ONE -> context.getString(R.string.downloads_repeat_one)
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = repeatMode == OfflineRepeatMode.ONE,
                            transitionSpec = { PhantomMotion.IconSwapTransition },
                            label = "downloads_repeat_icon"
                        ) { isOne ->
                            Icon(
                                imageVector = if (isOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = stringResource(R.string.downloads_repeat_all),
                                tint = repeatTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // DOWNLOADS LIST / EMPTY STATE
        AnimatedContent(
            targetState = filteredList.isEmpty(),
            transitionSpec = { PhantomMotion.ContentStateTransition },
            modifier = Modifier.weight(1f),
            label = "downloads_content_state"
        ) { isEmpty ->
            if (isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .phantomGlassSurface(shape = CircleShape, shadowElevation = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) stringResource(R.string.downloads_search_no_results) else stringResource(R.string.downloads_empty_title),
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) stringResource(R.string.downloads_search_try_again) else stringResource(R.string.downloads_empty_desc),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomNavInset + 140.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        val isAudio = item.format.equals("MP3", ignoreCase = true)
                        val isCurrentActive = isAudio && activeTrack?.id == item.id

                        DownloadItemRow(
                            item = item,
                            isCurrentActive = isCurrentActive,
                            isPlaying = isAudioPlaying && isCurrentActive,
                            onItemClick = {
                                if (isAudio) {
                                    requestNotificationPermission()
                                    val startIdx = audioTracks.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                    OfflineAudioPlayerManager.playQueue(context, audioTracks, startIdx)
                                } else {
                                    videoToPlayOffline = item
                                }
                            },
                            onPlayExternal = {
                                openInExternalPlayer(context, item)
                            },
                            onShare = {
                                shareMediaFile(context, item)
                            },
                            onDelete = {
                                itemToDelete = item
                            }
                        )
                    }
                }
            }
        }
    }

    // Video Player Dialog
    videoToPlayOffline?.let { vid ->
        OfflineVideoPlayerDialog(
            video = vid,
            onDismiss = { videoToPlayOffline = null },
            onOpenExternal = {
                openInExternalPlayer(context, vid)
            }
        )
    }

    // Delete Confirmation Dialog (Liquid Glass)
    itemToDelete?.let { item ->
        val dialogShape = remember { RoundedCornerShape(24.dp) }
        val actionShape = remember { RoundedCornerShape(14.dp) }
        Dialog(onDismissRequest = { itemToDelete = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .phantomGlassSurface(shape = dialogShape, shadowElevation = 18.dp)
                    .padding(22.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.downloads_delete_title),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.downloads_delete_confirm_desc, item.title),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(actionShape)
                                .background(GlassSurface)
                                .border(0.75.dp, GlassBorderLight, actionShape)
                                .testTag("downloads_delete_cancel_btn")
                                .iosBounceClick(scaleDown = 0.95f) { itemToDelete = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.dialog_cancel),
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(actionShape)
                                .background(YouTubeRed)
                                .testTag("downloads_delete_confirm_btn")
                                .iosBounceClick(scaleDown = 0.95f) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            deleteDownloadedFile(context, item)
                                        }
                                        repository.deleteDownload(item.id)
                                        if (activeTrack?.id == item.id) {
                                            OfflineAudioPlayerManager.stop(context)
                                        }
                                        itemToDelete = null
                                        Toast.makeText(context, context.getString(R.string.downloads_toast_deleted), Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.dialog_delete),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadFilterBar(
    selectedFilter: DownloadFilter,
    totalCount: Int,
    audioCount: Int,
    videoCount: Int,
    onSelectFilter: (DownloadFilter) -> Unit
) {
    val filters = DownloadFilter.entries
    val activeIndex = filters.indexOf(selectedFilter).coerceAtLeast(0)
    val trackShape = remember { RoundedCornerShape(24.dp) }
    val pillShape = remember { RoundedCornerShape(20.dp) }
    val activePillBrush = remember {
        Brush.verticalGradient(colors = listOf(BubblePillGlassTop, BubblePillGlassBottom))
    }
    val activePillBorderBrush = remember {
        Brush.verticalGradient(colors = listOf(BubblePillGlassBorderTop, BubblePillGlassBorderBottom))
    }
    val animatedIndex by animateFloatAsState(
        targetValue = activeIndex.toFloat(),
        animationSpec = IosSpringSpecs.DockPillGlide,
        label = "downloads_filter_pill_pos"
    )
    val travelStretch = abs(activeIndex.toFloat() - animatedIndex).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(48.dp)
            .phantomGlassSurface(shape = trackShape, shadowElevation = 6.dp)
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val tabWidthPx = totalWidthPx / filters.size

            Box(
                modifier = Modifier
                    .offset { IntOffset((animatedIndex * tabWidthPx).roundToInt(), 0) }
                    .width(maxWidth / filters.size)
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
                filters.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    val txtColor by animateColorAsState(
                        targetValue = if (isSelected) TextPrimary else TextSecondary,
                        animationSpec = IosSpringSpecs.ColorFade,
                        label = "dl_filter_txt"
                    )
                    val label = when (filter) {
                        DownloadFilter.ALL -> stringResource(R.string.downloads_filter_all, totalCount)
                        DownloadFilter.AUDIO -> stringResource(R.string.downloads_filter_audio, audioCount)
                        DownloadFilter.VIDEO -> stringResource(R.string.downloads_filter_video, videoCount)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(pillShape)
                            .testTag("downloads_filter_${filter.name.lowercase()}")
                            .iosBounceClick(scaleDown = 0.94f) { onSelectFilter(filter) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = txtColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItemRow(
    item: DownloadEntity,
    isCurrentActive: Boolean,
    isPlaying: Boolean,
    onItemClick: () -> Unit,
    onPlayExternal: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val isAudio = item.format.equals("MP3", ignoreCase = true)
    val sizeMb = String.format(Locale.getDefault(), "%.1f MB", item.fileSize / (1024f * 1024f))
    val rowShape = remember { RoundedCornerShape(14.dp) }
    val menuShape = remember { RoundedCornerShape(14.dp) }

    val rowBg by animateColorAsState(
        targetValue = if (isCurrentActive) DownloadActiveRowBg else CardBackground,
        animationSpec = IosSpringSpecs.ColorFade,
        label = "dl_row_bg"
    )
    val rowBorder by animateColorAsState(
        targetValue = if (isCurrentActive) YouTubeRed.copy(alpha = 0.5f) else CardBorder,
        animationSpec = IosSpringSpecs.ColorFade,
        label = "dl_row_border"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isCurrentActive) YouTubeRed else TextPrimary,
        animationSpec = IosSpringSpecs.ColorFade,
        label = "dl_row_title"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBg)
            .border(1.dp, rowBorder, rowShape)
            .iosBounceClick(scaleDown = 0.985f, onClick = onItemClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with Format & Playing Badge
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MediaPlaceholderBg),
            contentAlignment = Alignment.Center
        ) {
            if (item.thumbnailUrl.isNotBlank()) {
                val thumbRequest = remember(item.thumbnailUrl, context) {
                    PhantomImageRequest.Builder(context)
                        .data(item.thumbnailUrl)
                        .size(144, 108)
                        .allowRgb565(true)
                        .build()
                }
                PhantomAsyncImage(
                    model = thumbRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isCurrentActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = YouTubeRed,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                // Format badge overlay (MP3 / MP4)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .background(OverlayBadgeScrim, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = item.format,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title, Channel & Size info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = titleColor,
                fontSize = 13.sp,
                fontWeight = if (isCurrentActive) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${item.channelTitle} • ${item.qualityLabel}",
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$sizeMb • " + stringResource(R.string.downloads_saved_offline),
                color = TextMuted,
                fontSize = 10.sp
            )
        }

        // Action menu
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .iosBounceClick(scaleDown = 0.88f) { showMenu = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(MenuSurfaceDark, menuShape)
                    .border(0.75.dp, MenuBorderSubtle, menuShape)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.downloads_play_external), color = TextPrimary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onPlayExternal()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.downloads_share_file), color = TextPrimary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Share, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.downloads_delete_title), color = YouTubeRed, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = YouTubeRed, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun openInExternalPlayer(context: Context, item: DownloadEntity) {
    try {
        val uri = com.phantom.tube.data.download.PhantomDownloader.resolveSafeContentUri(
            context = context,
            rawUriString = item.fileUri,
            fallbackFilePath = item.filePath
        ) ?: throw IllegalStateException("File URI unavailable")
        val mimeType = if (item.format.equals("MP3", ignoreCase = true)) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.downloads_chooser_play))
        if (!context.safeStartActivity(chooser)) {
            Toast.makeText(context, context.getString(R.string.downloads_toast_no_external), Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.downloads_toast_no_external), Toast.LENGTH_SHORT).show()
    }
}

private fun shareMediaFile(context: Context, item: DownloadEntity) {
    try {
        val uri = com.phantom.tube.data.download.PhantomDownloader.resolveSafeContentUri(
            context = context,
            rawUriString = item.fileUri,
            fallbackFilePath = item.filePath
        ) ?: throw IllegalStateException("File URI unavailable")
        val mimeType = if (item.format.equals("MP3", ignoreCase = true)) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.downloads_chooser_share, item.title))
        if (!context.safeStartActivity(chooser)) {
            Toast.makeText(context, context.getString(R.string.downloads_toast_share_failed), Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.downloads_toast_share_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun deleteDownloadedFile(context: Context, item: DownloadEntity) {
    try {
        if (item.fileUri.isNotBlank()) {
            context.contentResolver.delete(Uri.parse(item.fileUri), null, null)
        }
    } catch (_: Exception) {}
    try {
        if (item.filePath.isNotBlank()) {
            val f = File(item.filePath)
            if (f.exists()) f.delete()
        }
    } catch (_: Exception) {}
}
