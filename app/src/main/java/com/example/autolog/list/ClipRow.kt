package com.example.autolog.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.data.clip.Clip
import com.example.autolog.ui.rememberClipThumbnail
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.ListDimens
import com.example.autolog.ui.theme.Spacing

fun clipRowTag(position: Int) = "clip-row-$position"

/**
 * 목록의 한 줄. 앞에 붙은 번호는 장식이 아니라 **브이로그에 이어붙는 순서**다 (planning 3-3).
 */
@Composable
fun ClipRow(
    clip: Clip,
    position: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(clipRowTag(position)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "${position + 1}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(ListDimens.orderNumber),
        )

        ClipThumbnail(clip)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(
                    R.string.clip_list_row_ended_at,
                    formatClipTime(clip.endedAt),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = clip.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 9:16 세로 촬영 고정이라(planning 6-1) 썸네일도 세로 비율로 둔다. */
@Composable
private fun ClipThumbnail(clip: Clip, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(ListDimens.clipThumbnailWidth)
            .height(ListDimens.clipThumbnail)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // 썸네일을 읽어오는 동안에는 자리만 잡아둔다 — 행 높이가 뒤늦게 바뀌면 목록이 출렁인다.
        rememberClipThumbnail(clip.uri)?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = formatClipDuration(clip.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = CameraControlTint,
            modifier = Modifier
                .padding(Spacing.xs)
                .clip(RoundedCornerShape(4.dp))
                .background(CameraScrim)
                .padding(horizontal = Spacing.xs),
        )
    }
}
