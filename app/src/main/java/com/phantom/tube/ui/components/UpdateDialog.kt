package com.phantom.tube.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phantom.tube.R
import com.phantom.tube.core.config.PhantomConfig
import com.phantom.tube.core.theme.DialogChangelogSurface
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.ProgressTrackDark
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.phantomGlassSurface
import com.phantom.tube.core.update.AppUpdateInfo
import com.phantom.tube.core.util.findActivity

sealed class UpdateDialogState {
    data class Available(val info: AppUpdateInfo) : UpdateDialogState()
    data class Downloading(val info: AppUpdateInfo, val progress: Float) : UpdateDialogState()
    data class PermissionRequired(val onOpenSettings: () -> Unit) : UpdateDialogState()
    data class Error(val message: String, val onRetry: () -> Unit) : UpdateDialogState()
}

@Composable
fun UpdateDialog(
    state: UpdateDialogState,
    onDismiss: () -> Unit,
    onStartDownload: (AppUpdateInfo) -> Unit
) {
    val context = LocalContext.current
    val isForceUpdate = when (state) {
        is UpdateDialogState.Available -> state.info.isForceUpdate
        is UpdateDialogState.Downloading -> state.info.isForceUpdate
        else -> PhantomConfig.isLocked()
    }
    val isCancellable = !isForceUpdate && state !is UpdateDialogState.Downloading
    val dialogShape = remember { RoundedCornerShape(26.dp) }
    val changelogShape = remember { RoundedCornerShape(14.dp) }
    val buttonShape = remember { RoundedCornerShape(14.dp) }

    val handleDismissOrExit: () -> Unit = {
        if (isForceUpdate) {
            context.findActivity()?.finishAffinity()
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (isCancellable || isForceUpdate) {
                handleDismissOrExit()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is UpdateDialogState.Downloading,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .phantomGlassSurface(shape = dialogShape, shadowElevation = 20.dp)
                .animateContentSize(animationSpec = IosSpringSpecs.TopBarSize)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(YouTubeRed.copy(alpha = 0.16f))
                        .border(1.dp, YouTubeRed.copy(alpha = 0.32f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = state is UpdateDialogState.Downloading,
                        transitionSpec = { PhantomMotion.IconSwapTransition },
                        label = "update_dialog_header_icon"
                    ) { isDownloading ->
                        Icon(
                            imageVector = if (isDownloading) Icons.Default.Download else Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = YouTubeRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(
                    targetState = state,
                    contentKey = { it::class },
                    transitionSpec = { PhantomMotion.ContentStateTransition },
                    label = "update_dialog_body"
                ) { currentState ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (currentState) {
                            is UpdateDialogState.Available -> {
                                Text(
                                    text = if (isForceUpdate) stringResource(R.string.update_dialog_mandatory_title) else stringResource(R.string.update_dialog_available_title),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isForceUpdate) {
                                        stringResource(R.string.update_dialog_mandatory_desc, currentState.info.versionName)
                                    } else {
                                        stringResource(R.string.update_dialog_available_desc, currentState.info.versionName)
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                if (currentState.info.changelog.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 160.dp)
                                            .clip(changelogShape)
                                            .background(DialogChangelogSurface)
                                            .border(0.75.dp, GlassBorderLight, changelogShape)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(14.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = stringResource(R.string.update_dialog_changelog_title),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = YouTubeRed,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = currentState.info.changelog,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = TextPrimary,
                                                    lineHeight = 18.sp
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(buttonShape)
                                            .background(GlassSurface)
                                            .border(0.75.dp, GlassBorderLight, buttonShape)
                                            .testTag("update_dialog_later_btn")
                                            .iosBounceClick(scaleDown = 0.95f) { handleDismissOrExit() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isCancellable) stringResource(R.string.update_dialog_later) else stringResource(R.string.update_dialog_exit),
                                            color = TextSecondary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(buttonShape)
                                            .background(YouTubeRed)
                                            .testTag("update_dialog_confirm_btn")
                                            .iosBounceClick(scaleDown = 0.95f) { onStartDownload(currentState.info) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.update_dialog_update_now),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            is UpdateDialogState.Downloading -> {
                                val animatedProgress by animateFloatAsState(
                                    targetValue = currentState.progress.coerceIn(0f, 1f),
                                    animationSpec = IosSpringSpecs.Gentle,
                                    label = "update_download_progress"
                                )
                                Text(
                                    text = stringResource(R.string.update_dialog_downloading_title),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val percent = (currentState.progress * 100).toInt()
                                Text(
                                    text = if (currentState.progress > 0f) stringResource(R.string.update_dialog_percent_done, percent) else stringResource(R.string.update_dialog_prep_download),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                if (currentState.progress > 0f) {
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = YouTubeRed,
                                        trackColor = ProgressTrackDark
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = YouTubeRed,
                                        trackColor = ProgressTrackDark
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.update_dialog_auto_install_note),
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                )
                            }

                            is UpdateDialogState.PermissionRequired -> {
                                Text(
                                    text = stringResource(R.string.update_dialog_perm_title),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.update_dialog_perm_desc),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        lineHeight = 18.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(buttonShape)
                                            .background(GlassSurface)
                                            .border(0.75.dp, GlassBorderLight, buttonShape)
                                            .iosBounceClick(scaleDown = 0.95f) { handleDismissOrExit() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isCancellable) stringResource(R.string.dialog_cancel) else stringResource(R.string.update_dialog_exit),
                                            color = TextSecondary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(buttonShape)
                                            .background(YouTubeRed)
                                            .iosBounceClick(scaleDown = 0.95f, onClick = currentState.onOpenSettings),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.update_dialog_open_settings),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            is UpdateDialogState.Error -> {
                                Text(
                                    text = stringResource(R.string.update_dialog_failed_title),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = currentState.message,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(buttonShape)
                                            .background(GlassSurface)
                                            .border(0.75.dp, GlassBorderLight, buttonShape)
                                            .iosBounceClick(scaleDown = 0.95f) { handleDismissOrExit() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isCancellable) stringResource(R.string.action_close) else stringResource(R.string.update_dialog_exit),
                                            color = TextSecondary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(buttonShape)
                                            .background(YouTubeRed)
                                            .iosBounceClick(scaleDown = 0.95f, onClick = currentState.onRetry),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_retry),
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
        }
    }
}

