package com.example.autolog.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.Spacing

const val TAG_ORIENTATION_TOGGLE = "orientation-toggle"
const val TAG_TURN_HINT = "turn-device-hint"

/** 우측 상단 방향 전환. 누를 때마다 세로·가로를 오간다 — 선택지가 둘뿐이라 메뉴를 두지 않는다. */
@Composable
fun OrientationToggle(
    orientation: CaptureOrientation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (orientation == CaptureOrientation.Portrait) R.string.camera_orientation_portrait
        else R.string.camera_orientation_landscape,
    )
    val description = stringResource(R.string.camera_orientation_toggle, label)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(CameraScrim)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(TAG_ORIENTATION_TOGGLE),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrameGlyph(orientation)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = CameraControlTint,
        )
    }
}

/** 찍힐 프레임 모양을 그대로 그린 작은 사각형. 글자를 읽기 전에 모양으로 먼저 알아보게 한다. */
@Composable
private fun FrameGlyph(orientation: CaptureOrientation) {
    val (width, height) = if (orientation == CaptureOrientation.Portrait) 10.dp to 16.dp else 16.dp to 10.dp
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .border(2.dp, CameraControlTint, RoundedCornerShape(2.dp)),
    )
}

/**
 * 화면은 세로로 고정돼 있어서, 눕혀 든 사람에게는 글자가 옆으로 누워 보인다. 기기를 든 방향만큼
 * 거꾸로 돌려 그 사람이 바로 읽게 한다 — 세로로 세우라는 안내는 눕혀 든 채로 읽는다.
 */
@Composable
fun TurnDeviceHint(target: CaptureOrientation, deviceRotation: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            if (target == CaptureOrientation.Landscape) R.string.camera_turn_sideways else R.string.camera_turn_upright,
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = CameraControlTint,
        modifier = modifier
            .rotate(deviceRotation * 90f)
            .clip(RoundedCornerShape(12.dp))
            .background(CameraScrim)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(TAG_TURN_HINT),
    )
}
