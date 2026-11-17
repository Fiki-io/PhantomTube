package com.phantom.tube.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.core.theme.MediaPlaceholderBg
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.OverlayBadgeScrim
import com.phantom.tube.core.theme.OverlayTrackSubtle
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.ui.components.PhantomEmptyStateView
import com.phantom.tube.ui.components.PhantomIconButton
import com.phantom.tube.ui.screens.settings.components.SettingsConfirmDialog
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    repository: PhantomRepository,
    onVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val historyList by repository.getWatchHistory().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
    ) {
        com.phantom.tube.ui.components.PhantomStatusBarStrip()

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.nav_history),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            if (historyList.isNotEmpty()) {
                PhantomIconButton(
                    icon = Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.library_clear_history),
                    modifier = Modifier.testTag("history_clear_btn"),
                    size = 48.dp,
                    iconSize = 20.dp,
                    onClick = {
                        showClearConfirm = true
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    PhantomEmptyStateView(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.empty_history)
                    )
                }
            } else {
                val bottomNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = if (maxWidth >= 600.dp) 2 else 1
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = bottomNavInset + 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = historyList,
                            key = { index, entry -> "hist_${entry.videoId}_$index" },
                            contentType = { _, _ -> "history_card" }
                        ) { _, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_item_${entry.videoId}")
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onVideoClick(
                                            VideoItem(
                                                id = entry.videoId,
                                                title = entry.title,
                                                channelTitle = entry.channelTitle,
                                                channelId = entry.channelId,
                                                thumbnailUrl = entry.thumbnailUrl,
                                                durationText = entry.durationText
                                            )
                                        )
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail with Resume Progress Indicator
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(68.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MediaPlaceholderBg)
                                ) {
                                    val thumbRequest = remember(entry.thumbnailUrl, context) {
                                        ImageRequest.Builder(context)
                                            .data(entry.thumbnailUrl)
                                            .size(240, 136)
                                            .precision(Precision.INEXACT)
                                            .allowRgb565(true)
                                            .allowHardware(false)
                                            .build()
                                    }
                                    AsyncImage(
                                        model = thumbRequest,
                                        contentDescription = entry.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize()
                                    )

                                    if (entry.durationText.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .background(OverlayBadgeScrim, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = entry.durationText,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = Color.White
                                            )
                                        }
                                    }

                                    if (entry.progressFraction > 0f) {
                                        LinearProgressIndicator(
                                            progress = { entry.progressFraction },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .align(Alignment.BottomCenter),
                                            color = YouTubeRed,
                                            trackColor = OverlayTrackSubtle
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Text(
                                        text = entry.channelTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        SettingsConfirmDialog(
            title = stringResource(R.string.settings_clear_watch_history),
            message = stringResource(R.string.dialog_confirm_clear_history),
            confirmLabel = stringResource(R.string.dialog_delete),
            isDestructive = true,
            onConfirm = {
                scope.launch {
                    repository.clearWatchHistory()
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.toast_history_cleared),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}
