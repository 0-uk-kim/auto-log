package com.example.autolog.camera

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val MinSwipeDistance = 64.dp

/** 가로 성분이 세로의 절반을 넘으면 비스듬한 드래그로 보고 무시한다. */
internal fun isVerticalSwipe(drag: Offset, minDistancePx: Float): Boolean =
    abs(drag.y) >= minDistancePx && abs(drag.x) * 2 <= abs(drag.y)

/**
 * 프리뷰를 위나 아래로 쓸면 렌즈를 뒤집는다 (#57).
 * 한 손가락일 때만 본다 — 두 손가락 핀치 줌이 세로로 움직여도 전환되지 않게 한다.
 * 체인 맨 뒤에 둬야 한다. 인식한 손을 뗄 때 소비해 바깥 탭 감지기가 더블탭의 첫 탭으로 세지 않게 한다.
 */
fun Modifier.lensSwipe(onSwipe: () -> Unit): Modifier = pointerInput(onSwipe) {
    val minDistancePx = MinSwipeDistance.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) return@awaitEachGesture
            val change = event.changes.first()
            if (!change.pressed) {
                if (isVerticalSwipe(change.position - down.position, minDistancePx)) {
                    change.consume()
                    onSwipe()
                }
                return@awaitEachGesture
            }
        }
    }
}
