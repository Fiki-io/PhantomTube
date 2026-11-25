package com.phantom.tube.ui.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.R
import com.phantom.tube.core.theme.GlassBorderDim
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.GlassHandleColor
import com.phantom.tube.core.theme.GlassSheetScrim
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeDark
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.phantomGlassSheet
import com.phantom.tube.ui.components.IosSpringSpecs
import com.phantom.tube.ui.components.PhantomMotion
import com.phantom.tube.ui.components.iosBounceClick

@Composable
private fun GameHubSheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(CircleShape)
                .background(GlassHandleColor)
        )
    }
}

@Composable
private fun GameHubSheetHeader(
    title: String,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GameHubSheetDragHandle()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                ),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 52.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
                    .clip(CircleShape)
                    .iosBounceClick(scaleDown = 0.88f, onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GlassSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = GlassBorderDim
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsSingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetShape = remember { RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        scrimColor = GlassSheetScrim,
        shape = sheetShape,
        dragHandle = null
    ) {
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .phantomGlassSheet(shape = sheetShape)
        ) {
            GameHubSheetHeader(
                title = title,
                onDismiss = onDismiss
            )

            val maxListHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.65f).coerceAtLeast(240.dp)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight),
                contentPadding = PaddingValues(bottom = navBarBottom + 28.dp)
            ) {
                itemsIndexed(options) { index, (optionValue, label) ->
                    val isSelected = optionValue == selectedValue
                    val optionColor by animateColorAsState(
                        targetValue = if (isSelected) TextPrimary else TextSecondary,
                        animationSpec = IosSpringSpecs.ColorFade,
                        label = "settings_choice_color"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosBounceClick(scaleDown = 0.985f) {
                                onSelect(optionValue)
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 15.5.sp
                            ),
                            color = optionColor,
                            modifier = Modifier.weight(1f)
                        )
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = PhantomMotion.PopScaleEnter,
                            exit = PhantomMotion.PopScaleExit
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.player_selected),
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < options.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            thickness = 0.5.dp,
                            color = GlassBorderDim
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetShape = remember { RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp) }
    val actionShape = remember { RoundedCornerShape(14.dp) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        scrimColor = GlassSheetScrim,
        shape = sheetShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .phantomGlassSheet(shape = sheetShape)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            GameHubSheetHeader(
                title = title,
                onDismiss = onDismiss
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp
                ),
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(actionShape)
                        .background(GlassSurface)
                        .border(0.75.dp, GlassBorderLight, actionShape)
                        .iosBounceClick(scaleDown = 0.95f, onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dialog_cancel),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.5.sp
                        ),
                        color = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(actionShape)
                        .background(if (isDestructive) YouTubeRed else TextPrimary)
                        .iosBounceClick(scaleDown = 0.95f) {
                            onConfirm()
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = confirmLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = if (isDestructive) Color.White else YouTubeDark
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsFeedbackDialog(
    onSendFeedback: (typeIndex: Int, message: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetShape = remember { RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp) }
    val chipShape = remember { RoundedCornerShape(12.dp) }
    val actionShape = remember { RoundedCornerShape(14.dp) }
    var selectedType by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val types = listOf(
        stringResource(R.string.feedback_type_bug),
        stringResource(R.string.feedback_type_feature),
        stringResource(R.string.feedback_type_general)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        scrimColor = GlassSheetScrim,
        shape = sheetShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .phantomGlassSheet(shape = sheetShape)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            GameHubSheetHeader(
                title = stringResource(R.string.feedback_dialog_title),
                onDismiss = onDismiss
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    types.forEachIndexed { idx, label ->
                        val isSelected = selectedType == idx
                        val chipBg by animateColorAsState(
                            targetValue = if (isSelected) TextPrimary else GlassSurface,
                            animationSpec = IosSpringSpecs.ColorFade,
                            label = "feedback_type_bg"
                        )
                        val chipBorder by animateColorAsState(
                            targetValue = if (isSelected) TextPrimary else GlassBorderLight,
                            animationSpec = IosSpringSpecs.ColorFade,
                            label = "feedback_type_border"
                        )
                        val chipText by animateColorAsState(
                            targetValue = if (isSelected) YouTubeDark else TextSecondary,
                            animationSpec = IosSpringSpecs.ColorFade,
                            label = "feedback_type_text"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                                .clip(chipShape)
                                .background(chipBg)
                                .border(0.75.dp, chipBorder, chipShape)
                                .iosBounceClick(scaleDown = 0.94f) { selectedType = idx }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = chipText,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(actionShape)
                        .background(GlassSurface)
                        .border(0.75.dp, GlassBorderLight, actionShape)
                        .padding(14.dp)
                ) {
                    if (message.isEmpty()) {
                        Text(
                            text = stringResource(R.string.feedback_message_hint),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = TextMuted
                        )
                    }
                    BasicTextField(
                        value = message,
                        onValueChange = { message = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.feedback_device_info_note),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                val canSend = message.isNotBlank()
                val sendBg by animateColorAsState(
                    targetValue = if (canSend) TextPrimary else GlassSurface,
                    animationSpec = IosSpringSpecs.ColorFade,
                    label = "feedback_send_bg"
                )
                val sendText by animateColorAsState(
                    targetValue = if (canSend) YouTubeDark else TextMuted,
                    animationSpec = IosSpringSpecs.ColorFade,
                    label = "feedback_send_text"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(actionShape)
                            .background(GlassSurface)
                            .border(0.75.dp, GlassBorderLight, actionShape)
                            .iosBounceClick(scaleDown = 0.95f, onClick = onDismiss)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_cancel),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.5.sp
                            ),
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(actionShape)
                            .background(sendBg)
                            .iosBounceClick(scaleDown = 0.95f, enabled = canSend) {
                                onSendFeedback(selectedType, message.trim())
                                onDismiss()
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.feedback_send_button),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            ),
                            color = sendText
                        )
                    }
                }
            }
        }
    }
}

