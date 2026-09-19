package com.example.autolog.camera

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.rememberClipThumbnail
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraDimens

const val TAG_LATEST_CLIP = "latest-clip-thumbnail"

/** 직전 촬영본의 썸네일. 촬영 이력이 없으면 아예 그리지 않는다 (planning 3-1). */
@Composable
fun LatestClipThumbnail(
    uri: Uri,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    rememberClipThumbnail(uri)?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.camera_latest_clip),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(CameraDimens.cornerAction)
                .clip(shape)
                .border(2.dp, CameraControlTint, shape)
                .clickable(onClick = onClick)
                .testTag(TAG_LATEST_CLIP),
        )
    }
}
