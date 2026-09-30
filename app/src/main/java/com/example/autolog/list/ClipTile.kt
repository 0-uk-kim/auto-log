package com.example.autolog.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.data.clip.Clip
import com.example.autolog.ui.rememberClipThumbnail
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.GlassBorder
import com.example.autolog.ui.theme.ListDimens
import com.example.autolog.ui.theme.Spacing

fun clipRowTag(position: Int) = "clip-row-$position"

const val TAG_EDITED_BADGE = "clip-edited-badge"
const val TAG_TIMELINE = "clip-timeline"

fun clipCheckboxTag(position: Int) = "clip-checkbox-$position"

/**
 * 스토리보드의 한 칸. 9:16 세로 촬영 고정이라(planning 6-1) 칸도 세로로 세운다.
 * 왼쪽 위 번호는 장식이 아니라 **브이로그에 이어붙는 순서**다 (planning 3-3).
 *
 * [isDragging]이면 칸을 키우고 그림자를 줘서 격자에서 들린 것처럼 보이게 한다 — 손가락에 가려도
 * 무엇을 끌고 있는지 알 수 있어야 한다 (#16). [selected]가 null이 아니면 선택 모드다 (#38).
 * 누름과 길게 누름(끌기)은 부르는 쪽이 [modifier]로 건다.
 */
@Composable
fun ClipTile(
    clip: Clip,
    position: Int,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    selected: Boolean? = null,
) {
    val scale by animateFloatAsState(if (isDragging) 1.06f else 1f, label = "lift")
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (isDragging) 16.dp else 0.dp, shape)
            .aspectRatio(ListDimens.clipThumbnailWidth / ListDimens.clipThumbnail)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                width = if (selected == true) 3.dp else 1.dp,
                color = if (selected == true) MaterialTheme.colorScheme.secondary else GlassBorder,
                shape = shape,
            )
            .testTag(clipRowTag(position)),
    ) {
        // 썸네일을 읽어오는 동안에는 자리만 잡아둔다 — 칸 크기는 비율로 이미 정해져 격자가 출렁이지 않는다.
        rememberClipThumbnail(clip.uri)?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 위아래를 어둡게 깔아 밝은 장면에서도 글자가 떠 보인다.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to CameraScrim,
                        0.25f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to CameraScrim,
                    ),
                ),
        )

        OrderBadge(order = position + 1, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm))

        if (selected != null) {
            SelectionMark(
                selected = selected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm)
                    .testTag(clipCheckboxTag(position)),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.sm),
        ) {
            if (clip.isEdited) EditedBadge()
            Text(
                text = formatClipTime(clip.endedAt),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = CameraControlTint,
            )
            Text(
                text = formatClipDuration(clip.playedDurationMs),
                style = MaterialTheme.typography.labelSmall,
                color = CameraControlTint.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun OrderBadge(order: Int, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ListDimens.orderNumber)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary),
    ) {
        Text(
            text = "$order",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondary,
        )
    }
}

/** 선택 모드의 체크 표시. 체크는 칸 누름이 맡는다 — 따로 받으면 한 번 누름이 두 번 처리된다. */
@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.secondary else CameraScrim)
            .border(1.5.dp, if (selected) Color.Transparent else CameraControlTint, CircleShape),
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 편집 여부 표시 (planning 3-3). 조각을 저장한 클립에만 붙는다. */
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
 * 완성될 브이로그를 한 줄 띠로 미리 보여 준다. 칸 폭은 클립 길이에 비례하고, 왼쪽부터 재생된다.
 * 번호 배지와 함께 "이 순서대로, 이만큼씩 이어붙는다"를 글 없이 읽게 한다. 칸을 누르면 그 클립부터 본다.
 */
@Composable
fun VlogTimeline(clips: List<Clip>, onOpenClip: (Int) -> Unit, modifier: Modifier = Modifier) {
    val total = clips.sumOf { it.playedDurationMs }.coerceAtLeast(1)
    Column(modifier = modifier.testTag(TAG_TIMELINE)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(TimelineHeight)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            clips.forEachIndexed { index, clip ->
                // 아주 짧은 클립도 눌러 볼 수 있을 만큼은 폭을 준다.
                val weight = (clip.playedDurationMs.toFloat() / total).coerceAtLeast(MIN_SEGMENT_WEIGHT)
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(role = Role.Button) { onOpenClip(index) },
                ) {
                    rememberClipThumbnail(clip.uri)?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs + 2.dp)) {
            Text(
                text = formatClipDuration(0L),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatClipDuration(total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TimelineHeight = 56.dp
private const val MIN_SEGMENT_WEIGHT = 0.04f
