package com.example.autolog.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraHighlight

const val TAG_CLIP_LIST = "clip-list-button"
const val TAG_TIMER_TOGGLE = "record-timer-toggle"
const val TAG_TIMELAPSE_TOGGLE = "timelapse-toggle"
const val TAG_MUTE_TOGGLE = "mute-toggle"

private enum class TopBarPanel { Timer, Speed }

/**
 * 상단 빠른 설정 (#100). 갤럭시 기본 카메라처럼 아이콘을 배경 없이 한 줄로 두고,
 * 선택지가 여럿인 항목은 누르면 그 줄이 선택지로 바뀐다. 하나 고르면 다시 아이콘 줄로 돌아온다.
 *
 * 촬영 중에는 음소거만 남긴다 — 녹화 도중에도 소리를 끄고 켤 수 있다 (#44).
 */
@Composable
fun CameraTopBar(
    state: CameraUiState,
    onOpenClipList: () -> Unit,
    onSelectTimer: (RecordTimer) -> Unit,
    onSelectSpeed: (TimelapseSpeed) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var panel by rememberSaveable { mutableStateOf<TopBarPanel?>(null) }
    LaunchedEffect(state.isCapturing) { if (state.isCapturing) panel = null }
    val settingsAlpha by animateFloatAsState(if (state.isCapturing) 0f else 1f, label = "topBarAlpha")

    AnimatedContent(
        targetState = panel,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT.dp),
        label = "topBar",
    ) { open ->
        when (open) {
            TopBarPanel.Timer -> OptionRow(
                icon = R.drawable.ic_timer,
                options = RecordTimer.entries,
                selected = state.timer,
                label = { timerLabel(it) },
                onSelect = { onSelectTimer(it); panel = null },
                onClose = { panel = null },
            )

            TopBarPanel.Speed -> OptionRow(
                icon = R.drawable.ic_timelapse,
                options = TimelapseSpeed.choices,
                selected = state.hyperlapseSpeed,
                label = { stringResource(R.string.camera_timelapse_speed, it.factor) },
                onSelect = { onSelectSpeed(it); panel = null },
                onClose = { panel = null },
            )

            null -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarIcon(
                    icon = R.drawable.ic_clip_list,
                    description = stringResource(R.string.camera_open_clip_list),
                    onClick = onOpenClipList,
                    modifier = Modifier.alpha(settingsAlpha).testTag(TAG_CLIP_LIST),
                    enabled = !state.isCapturing,
                )

                val timer = timerLabel(state.timer)
                TopBarIcon(
                    icon = R.drawable.ic_timer,
                    description = stringResource(R.string.camera_timer_toggle, timer),
                    onClick = { panel = TopBarPanel.Timer },
                    modifier = Modifier.alpha(settingsAlpha).testTag(TAG_TIMER_TOGGLE),
                    enabled = !state.isCapturing,
                    badge = state.timer.seconds.takeIf { it > 0 }?.toString(),
                )

                // 하이퍼랩스는 소리를 담지 않는다. 그 자리에 배속을 둔다.
                if (state.mode == CameraMode.Hyperlapse) {
                    val speed = stringResource(R.string.camera_timelapse_speed, state.hyperlapseSpeed.factor)
                    TopBarText(
                        text = speed,
                        description = stringResource(R.string.camera_timelapse_toggle, speed),
                        onClick = { panel = TopBarPanel.Speed },
                        modifier = Modifier.alpha(settingsAlpha).testTag(TAG_TIMELAPSE_TOGGLE),
                        enabled = !state.isCapturing,
                    )
                } else {
                    val sound = stringResource(if (state.isMuted) R.string.camera_sound_muted else R.string.camera_sound_on)
                    TopBarIcon(
                        icon = if (state.isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic,
                        description = stringResource(R.string.camera_mute_toggle, sound),
                        onClick = onToggleMute,
                        modifier = Modifier.testTag(TAG_MUTE_TOGGLE),
                    )
                }
            }
        }
    }
}

@Composable
private fun timerLabel(timer: RecordTimer): String =
    if (timer == RecordTimer.Off) {
        stringResource(R.string.camera_timer_off)
    } else {
        stringResource(R.string.camera_timer_seconds, timer.seconds)
    }

@Composable
private fun TopBarIcon(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: String? = null,
) {
    Box(
        // 원으로 자르면 곁에 붙인 숫자가 잘린다. 누름 효과만 원형으로 둔다.
        modifier = modifier
            .size(TOUCH_TARGET.dp)
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = ripple(bounded = false, radius = (TOUCH_TARGET / 2).dp),
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // 켜진 설정은 갤럭시처럼 아이콘을 노랗게 칠하고 값을 곁에 붙인다.
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (badge != null) CameraHighlight else CameraControlTint,
            modifier = Modifier.size(24.dp),
        )
        badge?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = CameraHighlight,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun TopBarText(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(TOUCH_TARGET.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CameraControlTint,
        )
    }
}

/** 한 항목의 선택지. 왼쪽 아이콘은 무엇을 고르는 중인지 알리고, 누르면 고르지 않고 닫는다. */
@Composable
private fun <T> OptionRow(
    icon: Int,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(TOUCH_TARGET.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = CameraControlTint)
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val text = label(option)
                Box(
                    modifier = Modifier
                        .height(TOUCH_TARGET.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelect(option) }
                        .semantics { this.selected = isSelected }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) CameraHighlight else CameraControlTint,
                    )
                }
            }
        }
    }
}

private const val TOP_BAR_HEIGHT = 56
private const val TOUCH_TARGET = 48
