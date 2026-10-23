package com.phantom.tube.ui.screens.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phantom.tube.core.database.DownloadEntity
import com.phantom.tube.core.theme.BubbleDockBorder
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.SystemStatusBarColor
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeDark
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.player.offline.OfflineAudioPlayerManager
import com.phantom.tube.ui.screens.player.components.OfflineVideoPlayerDialog
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

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
    val scope = rememberCoroutineScope()

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
        String.format(Locale.US, "%.1f MB", totalBytes / (1024f * 1024f))
    }

    val audioTracks = remember(downloads) {
        downloads.filter { it.format.equals("MP3", ignoreCase = true) }
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
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

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

            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Clear else Icons.Default.Search,
                    contentDescription = stringResource(R.string.nav_search),
                    tint = if (isSearchActive) YouTubeRed else TextSecondary
                )
            }
        }

        // SEARCH BAR FIELD
        AnimatedVisibility(visible = isSearchActive) {
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // FILTER CHIPS ROW (Horizontally Scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedFilter == DownloadFilter.ALL,
                onClick = { selectedFilter = DownloadFilter.ALL },
                label = { Text(stringResource(R.string.downloads_filter_all, downloads.size), fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = YouTubeRed,
                    selectedLabelColor = Color.White,
                    containerColor = GlassSurface,
                    labelColor = TextSecondary
                ),
                border = null
            )

            FilterChip(
                selected = selectedFilter == DownloadFilter.AUDIO,
                onClick = { selectedFilter = DownloadFilter.AUDIO },
                label = { Text(stringResource(R.string.downloads_filter_audio, downloads.count { it.format.equals("MP3", true) }), fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = YouTubeRed,
                    selectedLabelColor = Color.White,
                    containerColor = GlassSurface,
                    labelColor = TextSecondary
                ),
                border = null
            )

            FilterChip(
                selected = selectedFilter == DownloadFilter.VIDEO,
                onClick = { selectedFilter = DownloadFilter.VIDEO },
                label = { Text(stringResource(R.string.downloads_filter_video, downloads.count { it.format.equals("MP4", true) }), fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = YouTubeRed,
                    selectedLabelColor = Color.White,
                    containerColor = GlassSurface,
                    labelColor = TextSecondary
                ),
                border = null
            )
        }

        // PLAYBACK ACTION BAR (Putar Semua, Acak, Ulangi)
        if (audioTracks.isNotEmpty() && (selectedFilter == DownloadFilter.ALL || selectedFilter == DownloadFilter.AUDIO)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sleek Pill-shaped "Putar Semua" Button
                Button(
                    onClick = {
                        val tracksToPlay = if (isShuffle) audioTracks.shuffled() else audioTracks
                        OfflineAudioPlayerManager.playQueue(context, tracksToPlay, 0)
                        Toast.makeText(context, context.getString(R.string.downloads_toast_playing, tracksToPlay.size), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.downloads_play_all_count, audioTracks.size),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Shuffle & Repeat Quick Action Toggles
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Shuffle Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isShuffle) YouTubeRed.copy(alpha = 0.22f) else GlassSurface)
                            .border(1.dp, if (isShuffle) YouTubeRed else BubbleDockBorder, CircleShape)
                            .clickable {
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
                            tint = if (isShuffle) YouTubeRed else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Quick Repeat Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (repeatMode != com.phantom.tube.player.offline.OfflineRepeatMode.OFF) YouTubeRed.copy(alpha = 0.22f) else GlassSurface)
                            .border(1.dp, if (repeatMode != com.phantom.tube.player.offline.OfflineRepeatMode.OFF) YouTubeRed else BubbleDockBorder, CircleShape)
                            .clickable {
                                OfflineAudioPlayerManager.toggleRepeatMode(context)
                                val msg = when (OfflineAudioPlayerManager.repeatMode.value) {
                                    com.phantom.tube.player.offline.OfflineRepeatMode.OFF -> context.getString(R.string.downloads_repeat_off)
                                    com.phantom.tube.player.offline.OfflineRepeatMode.ALL -> context.getString(R.string.downloads_repeat_all)
                                    com.phantom.tube.player.offline.OfflineRepeatMode.ONE -> context.getString(R.string.downloads_repeat_one)
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (repeatMode == com.phantom.tube.player.offline.OfflineRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = stringResource(R.string.downloads_repeat_all),
                            tint = if (repeatMode != com.phantom.tube.player.offline.OfflineRepeatMode.OFF) YouTubeRed else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // DOWNLOADS LIST / EMPTY STATE
        if (filteredList.isEmpty()) {
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
                            .background(GlassSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 130.dp)
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
                                // Putar musik dengan antrean dari semua audio yang ada
                                val startIdx = audioTracks.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                OfflineAudioPlayerManager.playQueue(context, audioTracks, startIdx)
                            } else {
                                // Buka pemutar video MP4 offline
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

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.downloads_delete_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = stringResource(R.string.downloads_delete_confirm_desc, item.title),
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            deleteDownloadedFile(context, item)
                            repository.deleteDownload(item.id)
                            if (activeTrack?.id == item.id) {
                                OfflineAudioPlayerManager.stop(context)
                            }
                            itemToDelete = null
                            Toast.makeText(context, context.getString(R.string.downloads_toast_deleted), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                ) {
                    Text(stringResource(R.string.dialog_delete), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.dialog_cancel), color = TextSecondary)
                }
            },
            containerColor = YouTubeDark
        )
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
    var showMenu by remember { mutableStateOf(false) }
    val isAudio = item.format.equals("MP3", ignoreCase = true)
    val sizeMb = String.format(Locale.US, "%.1f MB", item.fileSize / (1024f * 1024f))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentActive) Color(0xFF261818) else CardBackground)
            .border(
                1.dp,
                if (isCurrentActive) YouTubeRed.copy(alpha = 0.5f) else CardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onItemClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with Format & Playing Badge
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181818)),
            contentAlignment = Alignment.Center
        ) {
            if (item.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = item.thumbnailUrl,
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
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
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
                color = if (isCurrentActive) YouTubeRed else TextPrimary,
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
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.settings_player_title),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(YouTubeDark)
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
        val uri = if (item.fileUri.isNotBlank()) Uri.parse(item.fileUri) else Uri.fromFile(File(item.filePath))
        val mimeType = if (item.format.equals("MP3", ignoreCase = true)) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.downloads_chooser_play)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.downloads_toast_no_external), Toast.LENGTH_SHORT).show()
    }
}

private fun shareMediaFile(context: Context, item: DownloadEntity) {
    try {
        val uri = if (item.fileUri.isNotBlank()) Uri.parse(item.fileUri) else Uri.fromFile(File(item.filePath))
        val mimeType = if (item.format.equals("MP3", ignoreCase = true)) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.downloads_chooser_share, item.title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
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
