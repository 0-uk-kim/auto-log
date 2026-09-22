package com.example.autolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.autolog.data.subtitle.SubtitleStyle

const val TAG_SUBTITLE_OVERLAY = "subtitle-overlay"

/**
 * 영상 위에 자막 한 줄을 얹는다. 크기·위치는 영상 높이 비율이라([SubtitleStyle]) 병합 결과물과 같은 모양이다.
 * [text]가 null이면 아무것도 그리지 않는다.
 */
@Composable
fun BoxScope.SubtitleOverlay(text: String?) {
    if (text.isNullOrBlank()) return
    BoxWithConstraints(Modifier.matchParentSize()) {
        val density = LocalDensity.current
        val height = constraints.maxHeight.toFloat()
        val fontSize = with(density) { (height * SubtitleStyle.TEXT_SIZE_RATIO).toSp() }
        val padding = with(density) { (height * SubtitleStyle.PADDING_RATIO).toDp() }
        val bottom = with(density) { (height * SubtitleStyle.BOTTOM_MARGIN_RATIO).toDp() }

        Text(
            text = text,
            style = TextStyle(
                color = Color(SubtitleStyle.TEXT_COLOR),
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom)
                .widthIn(max = maxWidth * SubtitleStyle.MAX_WIDTH_RATIO)
                .background(Color(SubtitleStyle.BACKGROUND_COLOR), RoundedCornerShape(padding))
                .padding(horizontal = padding * 2, vertical = padding)
                .testTag(TAG_SUBTITLE_OVERLAY),
        )
    }
}
