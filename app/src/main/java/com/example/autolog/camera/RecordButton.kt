package com.example.autolog.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraDimens
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.RecordRed

const val TAG_RECORD_BUTTON = "record-button"
const val TAG_PAUSE_BUTTON = "record-pause-button"
const val TAG_ELAPSED = "record-elapsed"

/**
 * 갤럭시 동영상 셔터 (#100) — 흰 테두리 안의 빨간 원. 녹화 중에는 빨간 원 안에 흰 정지 사각이 떠오른다.
 * 카운트다운 중에도 정지 모양이다 — 이미 촬영이 걸린 상태이고, 누르면 멈춘다는 뜻이 같다.
 */
@Composable
fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCountingDown: Boolean = false,
) {
    val active = isRecording || isCountingDown
    val description = stringResource(
        when {
            isRecording -> R.string.camera_stop_recording
            isCountingDown -> R.string.camera_cancel_countdown
            else -> R.string.camera_start_recording
        },
    )

    Box(
        modifier = modifier
            .size(CameraDimens.recordButton)
            .border(4.dp, CameraControlTint, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(RecordRed)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(TAG_RECORD_BUTTON),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = active, enter = scaleIn(), exit = scaleOut()) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CameraControlTint),
            )
        }
    }
}

/** 녹화 중 셔터 왼쪽의 일시정지·이어 찍기 (#100). 썸네일 자리를 빌린다 — 촬영 중에는 목록으로 갈 수 없다. */
@Composable
fun PauseButton(isPaused: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (isPaused) R.string.camera_resume_recording else R.string.camera_pause_recording)
    Box(
        modifier = modifier
            .size(CameraDimens.sideAction)
            .clip(CircleShape)
            .background(CameraScrim)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(TAG_PAUSE_BUTTON),
        contentAlignment = Alignment.Center,
    ) {
        if (isPaused) {
            // 이어 찍기는 빨간 점 — 셔터와 같은 "녹화" 표시다.
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(RecordRed))
        } else {
            Icon(painter = painterResource(R.drawable.ic_pause), contentDescription = null, tint = CameraControlTint)
        }
    }
}

/** 상단 가운데 경과 시간. 빨간 점을 앞에 두고, 멈춘 동안에는 갤럭시처럼 깜빡인다. */
@Composable
fun ElapsedIndicator(elapsed: String, modifier: Modifier = Modifier, isRecording: Boolean = true, isPaused: Boolean = false) {
    val blink by rememberInfiniteTransition(label = "pauseBlink").animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "pauseBlinkAlpha",
    )
    Row(
        modifier = modifier
            .alpha(if (isPaused) blink else 1f)
            .testTag(TAG_ELAPSED),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRecording) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RecordRed))
        }
        Text(
            text = elapsed,
            style = MaterialTheme.typography.titleMedium.copy(shadow = Shadow(color = CameraScrim, blurRadius = 8f)),
            fontWeight = FontWeight.Medium,
            color = CameraControlTint,
        )
    }
}
