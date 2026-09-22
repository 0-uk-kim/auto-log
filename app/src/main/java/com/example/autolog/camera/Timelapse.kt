package com.example.autolog.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim
import kotlin.time.Duration

const val TAG_TIMELAPSE_TOGGLE = "timelapse-toggle"

/** 타임랩스 배속 (#65). 평소처럼 찍은 뒤 이 배수만큼 빠르게 다시 담는다. [Off]는 일반 촬영이다. */
enum class TimelapseSpeed(val factor: Int) {
    Off(1),
    Five(5),
    Ten(10),
    Thirty(30),
    ;

    val isOn: Boolean
        get() = this != Off

    fun next(): TimelapseSpeed = entries[(ordinal + 1) % entries.size]

    /** [recorded]만큼 찍으면 완성본이 얼마나 되는지. 촬영 중에 결과 길이를 미리 보여 준다. */
    fun outputOf(recorded: Duration): Duration = recorded / factor
}

/** 타임랩스 전환. 타이머 바로 위에 같은 원형으로 둔다. 꺼져 있으면 아이콘, 켜져 있으면 배속이 보인다. */
@Composable
fun TimelapseToggle(
    speed: TimelapseSpeed,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (speed.isOn) {
        stringResource(R.string.camera_timelapse_speed, speed.factor)
    } else {
        stringResource(R.string.camera_timelapse_off)
    }
    val description = stringResource(R.string.camera_timelapse_toggle, label)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(CameraScrim)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(8.dp)
            .testTag(TAG_TIMELAPSE_TOGGLE),
        contentAlignment = Alignment.Center,
    ) {
        if (speed.isOn) {
            // 두 자리 배속에 ×까지 붙으면 타이머의 초보다 길다. 원 크기를 맞추려고 한 단계 작은 글자를 쓴다.
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CameraControlTint,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.size(20.dp).wrapContentSize(unbounded = true),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_timelapse),
                contentDescription = null,
                tint = CameraControlTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
