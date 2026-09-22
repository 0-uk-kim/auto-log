package com.example.autolog.camera

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.Spacing

const val TAG_MUTE_TOGGLE = "mute-toggle"

/** 좌측 상단 음소거 전환 (#44). 녹화 중에도 누를 수 있다 — 그 시점부터 소리가 꺼지거나 켜진다. */
@Composable
fun MuteToggle(
    isMuted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (isMuted) R.string.camera_sound_muted else R.string.camera_sound_on)
    val description = stringResource(R.string.camera_mute_toggle, label)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(CameraScrim)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(TAG_MUTE_TOGGLE),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic),
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
