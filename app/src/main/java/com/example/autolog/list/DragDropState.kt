package com.example.autolog.list

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.channels.Channel

/**
 * 목록 순서 변경. Compose Foundation에 재정렬 LazyColumn이 아직 없어서 직접 만든다.
 *
 * 드래그는 **줄 끝 손잡이에서만** 시작한다. 목록 전체에 걸면 LazyColumn의 스크롤이 같은
 * 포인터 이벤트를 먼저 가져가 첫 이동에서 제스처가 취소된다(실측) — 손잡이는 스크롤보다
 * 안쪽 노드라 이동을 먼저 받는다.
 *
 * 끄는 동안에는 목록의 순서만 바꾸고(메모리), 손을 떼는 순간 한 번만 저장한다 —
 * 한 칸 지날 때마다 Room에 쓰면 드래그 한 번에 쓰기가 수십 번 일어난다.
 */
class DragDropState internal constructor(
    private val lazyListState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal val scrollChannel = Channel<Float>()

    private var draggedDelta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    /**
     * 끌고 있는 줄을 원래 자리에서 얼마나 띄워 그릴지.
     *
     * 누적 이동량에서 **현재 레이아웃 위치**를 빼서 매번 다시 구한다. 자리가 바뀌면 레이아웃
     * 위치도 같이 바뀌므로, 보정을 따로 하지 않아도 손가락 밑에 그대로 붙어 있는다.
     */
    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { initialOffset + draggedDelta - it.offset } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    internal fun onDragStart(index: Int) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.also {
                draggingItemIndex = it.index
                initialOffset = it.offset
            }
    }

    internal fun onDragInterrupted() {
        if (draggingItemIndex != null) onDrop()
        draggingItemIndex = null
        draggedDelta = 0f
        initialOffset = 0
    }

    internal fun onDrag(offset: Offset) {
        draggedDelta += offset.y

        val dragging = draggingItemLayoutInfo ?: return
        val startOffset = dragging.offset + draggingItemOffset
        val endOffset = startOffset + dragging.size
        val middleOffset = startOffset + dragging.size / 2f

        val target = lazyListState.layoutInfo.visibleItemsInfo.find { item ->
            middleOffset.toInt() in item.offset..(item.offset + item.size) && item.index != dragging.index
        }

        if (target != null) {
            onMove(dragging.index, target.index)
            draggingItemIndex = target.index
            return
        }

        // 화면 끝까지 끌면 목록이 따라 흐르게 한다 — 안 그러면 보이는 범위 밖으로 옮길 수 없다.
        val overscroll = when {
            draggedDelta > 0 ->
                (endOffset - lazyListState.layoutInfo.viewportEndOffset).coerceAtLeast(0f)

            draggedDelta < 0 ->
                (startOffset - lazyListState.layoutInfo.viewportStartOffset).coerceAtMost(0f)

            else -> 0f
        }
        if (overscroll != 0f) scrollChannel.trySend(overscroll)
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
): DragDropState {
    val currentMove by rememberUpdatedState(onMove)
    val currentDrop by rememberUpdatedState(onDrop)
    val state = remember(lazyListState) {
        DragDropState(
            lazyListState = lazyListState,
            onMove = { from, to -> currentMove(from, to) },
            onDrop = { currentDrop() },
        )
    }

    LaunchedEffect(state) {
        for (diff in state.scrollChannel) {
            lazyListState.scrollBy(diff)
        }
    }

    return state
}

/** 순서 변경의 핵심 연산. 원본을 건드리지 않고 [from]의 항목을 [to] 자리로 옮긴 새 목록을 만든다. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
