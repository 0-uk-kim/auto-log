package com.example.autolog.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraDimens
import com.example.autolog.ui.theme.CameraScrim

const val TAG_LENS_TOGGLE = "lens-toggle"

/** 전면·후면 전환 (#49). 목록 버튼 바로 위에 같은 크기·모서리로 쌓는다 (#75). */
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
            .clip(RoundedCornerShape(12.dp))
            .background(CameraScrim)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .size(CameraDimens.cornerAction)
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
