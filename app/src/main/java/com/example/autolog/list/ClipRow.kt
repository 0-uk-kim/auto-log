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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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

const val TAG_EDITED_BADGE = "clip-edited-badge"

/**
 * 목록의 한 줄. 앞에 붙은 번호는 장식이 아니라 **브이로그에 이어붙는 순서**다 (planning 3-3).
 *
 * [isDragging]이면 그림자를 줘서 줄이 목록에서 들린 것처럼 보이게 한다 — 지금 무엇을 끌고 있는지
 * 손가락에 가려도 알 수 있어야 한다 (#16).
 */
@Composable
fun ClipRow(
    clip: Clip,
    position: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        tonalElevation = if (isDragging) 4.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
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

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(
                            R.string.clip_list_row_ended_at,
                            formatClipTime(clip.endedAt),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (clip.isEdited) EditedBadge()
                }
                Text(
                    text = clip.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                painter = painterResource(R.drawable.ic_drag_handle),
                contentDescription = stringResource(R.string.clip_list_drag_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                // 끄는 것은 이 손잡이에서만 시작한다 — 줄 전체에 걸면 목록 스크롤과 다툰다.
                modifier = dragHandleModifier.padding(Spacing.sm),
            )
        }
    }
}

/** 9:16 세로 촬영 고정이라(planning 6-1) 썸네일도 세로 비율로 둔다. */
/**
 * 편집 여부 표시 (planning 3-3). 1차는 편집 기능이 없어 항상 꺼져 있고, 2·3차에 켜진다 —
 * 필드와 표시를 미리 이어 두면 그때 목록을 다시 손보지 않아도 된다.
 */
@Composable
private fun EditedBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.clip_list_edited_badge),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
            .testTag(TAG_EDITED_BADGE)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = Spacing.xs),
    )
}

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
