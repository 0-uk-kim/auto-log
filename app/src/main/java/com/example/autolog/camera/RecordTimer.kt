package com.example.autolog.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim

const val TAG_COUNTDOWN = "record-countdown"

/**
 * 촬영 버튼을 누른 뒤 녹화가 시작되기까지 기다리는 시간 (#61).
 * 갤럭시 기본 카메라와 같은 끔·2초·5초·10초다 (#100).
 */
enum class RecordTimer(val seconds: Int) {
    Off(0),
    Two(2),
    Five(5),
    Ten(10),
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
