package com.example.autolog.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim

const val TAG_ZOOM_CONTROL = "zoom-control"

/**
 * 배율 빠른 전환 (#42). 핀치로 버튼 사이 배율이 되면 그 구간의 버튼이 실제 배율을 보여준다 —
 * 현재 배율 표시를 따로 두지 않고 버튼 하나로 겸한다.
 */
@Composable
fun ZoomControl(
    range: ZoomRange,
    ratio: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = range.presets()
    val active = presets.activePreset(ratio)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(CameraScrim)
            .padding(4.dp)
            .testTag(TAG_ZOOM_CONTROL),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { preset ->
            val isActive = preset == active
            val label = formatZoom(if (isActive) ratio else preset)
            val description = stringResource(R.string.camera_zoom_preset, formatZoom(preset))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isActive) ActiveChip else Color.Transparent)
                    .clickable { onSelect(preset) }
                    .semantics {
                        contentDescription = description
                        selected = isActive
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) ActiveLabel else CameraControlTint,
                )
            }
        }
    }
}

private val ActiveChip = Color(0x33FFFFFF)
private val ActiveLabel = Color(0xFFFFD60A)
