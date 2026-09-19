package com.example.autolog.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraDimens
import com.example.autolog.ui.theme.CameraScrim

const val TAG_CLIP_LIST = "clip-list-button"

/** 우측 하단 목록 진입. 좌측 썸네일과 같은 크기·모서리로 두어 하단 두 통로의 무게를 맞춘다. */
@Composable
fun ClipListButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(CameraDimens.cornerAction)
            .clip(RoundedCornerShape(12.dp))
            .background(CameraScrim)
            .clickable(onClick = onClick)
            .testTag(TAG_CLIP_LIST),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_clip_list),
            contentDescription = stringResource(R.string.camera_open_clip_list),
            tint = CameraControlTint,
        )
    }
}
