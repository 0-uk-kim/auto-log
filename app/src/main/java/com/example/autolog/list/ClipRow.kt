package com.example.autolog.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.data.clip.Clip
import com.example.autolog.ui.rememberClipThumbnail
import com.example.autolog.ui.theme.GlassBorder
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.ListDimens
import com.example.autolog.ui.theme.Spacing

fun clipRowTag(position: Int) = "clip-row-$position"

const val TAG_EDITED_BADGE = "clip-edited-badge"

fun clipCheckboxTag(position: Int) = "clip-checkbox-$position"

/**
 * 목록의 한 줄. 썸네일에 붙은 번호는 장식이 아니라 **브이로그에 이어붙는 순서**다 (planning 3-3).
 *
 * [isDragging]이면 그림자를 줘서 줄이 목록에서 들린 것처럼 보이게 한다 — 지금 무엇을 끌고 있는지
 * 손가락에 가려도 알 수 있어야 한다 (#16).
 *
 * [selected]가 null이 아니면 삭제 모드다 — 손잡이 자리에 체크박스를 두고, 줄을 누르면 체크가 바뀐다 (#38).
 * 삭제 모드가 아닐 때 길게 누르면 [onLongClick]으로 삭제 모드에 들어간다 (#102).
 */
@Composable
fun ClipRow(
    clip: Clip,
    position: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        shape = MaterialTheme.shapes.large,
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        // 고른 줄은 노란 테두리, 끄는 줄은 밝은 테두리로 — 나머지 줄은 바탕과 겨우 갈리는 가는 선만 둔다.
        border = when {
            selected == true -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
            isDragging -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            else -> BorderStroke(1.dp, GlassBorder)
        },
        shadowElevation = if (isDragging) 12.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onLongClick = onLongClick, onClick = onClick)
                .padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xs)
                .testTag(clipRowTag(position)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ClipThumbnail(clip = clip, order = position + 1)

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = formatClipTime(clip.endedAt),
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = formatClipDuration(clip.playedDurationMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (clip.isEdited) EditedBadge()
                }
            }

            if (selected != null) {
                // 체크는 줄 클릭이 맡는다 — 체크박스가 따로 받으면 한 번 누름이 두 번 처리된다.
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.secondary,
                        checkmarkColor = MaterialTheme.colorScheme.onSecondary,
                        uncheckedColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier
                        .padding(Spacing.sm)
                        .testTag(clipCheckboxTag(position)),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = stringResource(R.string.clip_list_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 끄는 것은 이 손잡이에서만 시작한다 — 줄 전체에 걸면 목록 스크롤과 다툰다.
                    modifier = dragHandleModifier.padding(Spacing.sm + Spacing.xs),
                )
            }
        }
    }
}

/**
 * 편집 여부 표시 (planning 3-3). 조각을 저장한 클립에만 붙는다.
 */
@Composable
private fun EditedBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.clip_list_edited_badge),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
            .testTag(TAG_EDITED_BADGE)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = Spacing.xs + 2.dp, vertical = 1.dp),
    )
}

/**
 * 9:16 세로 촬영 고정이라(planning 6-1) 썸네일도 세로 비율로 둔다.
 * 왼쪽 아래 숫자는 장식이 아니라 브이로그에 이어붙는 순서다.
 */
@Composable
private fun ClipThumbnail(clip: Clip, order: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(ListDimens.clipThumbnailWidth)
            .height(ListDimens.clipThumbnail)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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

        // 아래로 갈수록 어둡게 깔아 밝은 장면에서도 순서 번호가 떠 보인다.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to CameraScrim)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.xs)
                .size(ListDimens.orderNumber)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$order",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondary,
            )
        }
    }
}
