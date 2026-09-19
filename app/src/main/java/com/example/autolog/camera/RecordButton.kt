package com.example.autolog.camera

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraDimens
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.RecordRed

const val TAG_RECORD_BUTTON = "record-button"
const val TAG_ELAPSED = "record-elapsed"

/** 원(대기) ↔ 둥근 사각(녹화 중). 아이콘을 바꾸지 않고 안쪽 도형만 변형해 상태를 잇는다. */
@Composable
fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val innerSize by animateDpAsState(if (isRecording) 28.dp else 58.dp, label = "innerSize")
    val innerCorner by animateDpAsState(if (isRecording) 8.dp else 29.dp, label = "innerCorner")
    val description = stringResource(
        if (isRecording) R.string.camera_stop_recording else R.string.camera_start_recording,
    )

    Box(
        modifier = modifier
            .size(CameraDimens.recordButton)
            .border(3.dp, CameraControlTint, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(TAG_RECORD_BUTTON),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(RecordRed),
        )
    }
}

@Composable
fun ElapsedIndicator(elapsed: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(CameraScrim)
            .testTag(TAG_ELAPSED),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = elapsed,
            style = MaterialTheme.typography.labelLarge,
            color = CameraControlTint,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
