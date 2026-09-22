package com.example.autolog.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim

const val TAG_MUTE_TOGGLE = "mute-toggle"

/** 음소거 전환 (#44). 최근 영상 썸네일 바로 위에 둔다 (#55). 녹화 중에도 누를 수 있다 — 그 시점부터 소리가 꺼지거나 켜진다. */
@Composable
fun MuteToggle(
    isMuted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (isMuted) R.string.camera_sound_muted else R.string.camera_sound_on)
    val description = stringResource(R.string.camera_mute_toggle, label)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(CameraScrim)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(8.dp)
            .testTag(TAG_MUTE_TOGGLE),
    ) {
        Icon(
            painter = painterResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic),
            contentDescription = null,
            tint = CameraControlTint,
            modifier = Modifier.size(20.dp),
        )
    }
}
