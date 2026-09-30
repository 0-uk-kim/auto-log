package com.example.autolog.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraDimens
import com.example.autolog.ui.theme.CameraScrim

const val TAG_LENS_TOGGLE = "lens-toggle"

/** 전면·후면 전환 (#49). 갤럭시처럼 셔터 오른쪽 원에 둔다 (#100). */
@Composable
fun LensToggle(
    lens: CameraLens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (lens == CameraLens.Front) R.string.camera_lens_front else R.string.camera_lens_back)
    val description = stringResource(R.string.camera_lens_toggle, label)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(CameraScrim)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .size(CameraDimens.sideAction)
            .testTag(TAG_LENS_TOGGLE),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_flip_camera),
            contentDescription = null,
            tint = CameraControlTint,
        )
    }
}
