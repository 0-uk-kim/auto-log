package com.example.autolog.camera

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint

const val TAG_MODE_BAR = "camera-mode-bar"

/** 셔터 위 모드 줄의 항목 (#100). 왼쪽부터 이 순서로 놓인다. */
enum class CameraMode(@StringRes val label: Int) {
    Hyperlapse(R.string.camera_mode_hyperlapse),
    Video(R.string.camera_mode_video),
    ;

    /** 프리뷰를 쓸어 넘긴 방향의 이웃 모드. 끝이면 그대로다. [towardEnd]는 오른쪽 모드로 가는 쪽이다. */
    fun neighbor(towardEnd: Boolean): CameraMode =
        entries.getOrElse(ordinal + if (towardEnd) 1 else -1) { this }
}

/**
 * 갤럭시 기본 카메라의 모드 줄. 고른 모드가 늘 가운데 오도록 줄 전체가 옆으로 미끄러진다.
 * 촬영 중에는 모드를 바꿀 수 없으니 숨긴다.
 */
@Composable
fun CameraModeBar(
    selected: CameraMode,
    onSelect: (CameraMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 칸 너비가 같으니 고른 칸이 가운데 오려면 그 칸 순번만큼 반대로 민다.
    val shift by animateDpAsState(
        targetValue = MODE_SLOT_WIDTH.dp * ((CameraMode.entries.size - 1) / 2f - selected.ordinal),
        label = "modeShift",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .testTag(TAG_MODE_BAR),
        contentAlignment = Alignment.Center,
    ) {
        Row(modifier = Modifier.wrapContentWidth(unbounded = true).offset(x = shift)) {
            CameraMode.entries.forEach { mode ->
                val isSelected = mode == selected
                val background by animateColorAsState(if (isSelected) SelectedChip else Color.Transparent, label = "modeChip")
                Box(modifier = Modifier.width(MODE_SLOT_WIDTH.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(mode.label),
                        // 밝은 프리뷰 위에서도 읽히도록 그림자를 깐다.
                        style = MaterialTheme.typography.labelLarge.copy(shadow = Shadow(color = Color(0x99000000), blurRadius = 6f)),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) CameraControlTint else CameraControlTint.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(background)
                            .clickable { onSelect(mode) }
                            .semantics { this.selected = isSelected }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private const val MODE_SLOT_WIDTH = 104
private val SelectedChip = Color(0x4DFFFFFF)
