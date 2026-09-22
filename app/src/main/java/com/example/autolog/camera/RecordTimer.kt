package com.example.autolog.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.Spacing

const val TAG_TIMER_TOGGLE = "record-timer-toggle"
const val TAG_COUNTDOWN = "record-countdown"

/** 촬영 버튼을 누른 뒤 녹화가 시작되기까지 기다리는 시간 (#61). 폰을 세워 두고 자리를 잡을 틈을 준다. */
enum class RecordTimer(val seconds: Int) {
    Off(0),
    Three(3),
    Ten(10),
    ;

    fun next(): RecordTimer = entries[(ordinal + 1) % entries.size]
}

/** 좌측 상단 타이머 전환. 방향 전환과 같은 모양으로, 누를 때마다 끔 → 3초 → 10초를 돈다. */
@Composable
fun RecordTimerToggle(
    timer: RecordTimer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (timer == RecordTimer.Off) {
        stringResource(R.string.camera_timer_off)
    } else {
        stringResource(R.string.camera_timer_seconds, timer.seconds)
    }
    val description = stringResource(R.string.camera_timer_toggle, label)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(CameraScrim)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(TAG_TIMER_TOGGLE),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_timer),
            contentDescription = null,
            tint = CameraControlTint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = CameraControlTint,
        )
    }
}

/** 남은 초. 멀리서 보고 있으므로 크게 그리고, 안내 문구처럼 기기를 든 방향으로 돌려 바로 읽히게 한다. */
@Composable
fun CountdownNumber(secondsLeft: Int, deviceRotation: Int, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = secondsLeft,
        transitionSpec = { (scaleIn(initialScale = 1.4f) + fadeIn()) togetherWith fadeOut() },
        modifier = modifier
            .rotate(deviceRotation * 90f)
            .testTag(TAG_COUNTDOWN),
        label = "countdown",
    ) { seconds ->
        Text(
            text = seconds.toString(),
            color = CameraControlTint,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 120.sp,
                // 프리뷰가 밝아도 숫자가 묻히지 않게 한다. 배경 칩을 두면 화면을 너무 많이 가린다.
                shadow = Shadow(color = CameraScrim, blurRadius = 16f),
            ),
        )
    }
}
