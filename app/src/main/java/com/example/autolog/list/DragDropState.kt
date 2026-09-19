package com.example.autolog.list

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.sign
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

    /**
     * 마지막으로 자리를 바꾼 뒤로 끈 거리. 자리 바꿈 판정과 그림 위치를 **둘 다** 이 값으로 정한다.
     *
     * 끌기 시작한 지점을 목록 좌표로 붙들고 있으면 안 된다. 자리를 바꾸면 LazyColumn이 그 줄을
     * 붙잡으려고 스크롤을 되감아 내용이 통째로 밀리는데, 그러면 붙들어 둔 기준점이 어긋나 바로
     * 아래 줄이 다시 목표로 걸린다. 손가락이 멈춰 있어도 이벤트마다 한 칸씩 내려가 한 행만 끌어도
     * 바닥까지 떨어졌다 (B0). 줄의 **현재 자리**에서 재면 무엇이 밀리든 기준이 함께 움직인다.
     */
    private var draggedSinceMove by mutableFloatStateOf(0f)

    /** 마지막으로 손가락이 향한 쪽. 자리를 바꾼 직후에는 [draggedSinceMove]의 부호가 뒤집혀서 못 쓴다. */
    private var dragDirection by mutableFloatStateOf(0f)

    /** 끌고 있는 줄을 제 자리에서 얼마나 띄워 그릴지. */
    val draggingItemOffset: Float
        get() = if (draggingItemIndex == null) 0f else draggedSinceMove

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    internal fun onDragStart(index: Int) {
        if (lazyListState.layoutInfo.visibleItemsInfo.none { it.index == index }) return
        draggingItemIndex = index
        draggedSinceMove = 0f
        dragDirection = 0f
    }

    internal fun onDragInterrupted() {
        if (draggingItemIndex != null) onDrop()
        draggingItemIndex = null
        draggedSinceMove = 0f
        dragDirection = 0f
    }

    internal fun onDrag(offset: Offset) {
        var index = draggingItemIndex ?: return
        draggedSinceMove += offset.y
        if (offset.y != 0f) dragDirection = sign(offset.y)

        // 이웃 줄의 절반을 넘게 지나야 자리가 바뀌고, 바뀐 자리만큼은 덜어낸다 — 그래야 그다음
        // 칸으로 가려면 또 한 행을 온전히 끌어야 한다. 프레임을 건너뛰면 이벤트 하나에 두 행
        // 넘게 들어오므로 지나간 칸은 한 번에 다 넘긴다.
        while (!isAtListEnd(index)) {
            val neighbour = neighbourToward(index) ?: break
            if (neighbour.size == 0 || abs(draggedSinceMove) <= neighbour.size / 2f) break
            pinScroll(index, neighbour.index)
            onMove(index, neighbour.index)
            index = neighbour.index
            draggingItemIndex = index
            draggedSinceMove -= sign(draggedSinceMove) * neighbour.size
        }

        val dragging = draggingItemLayoutInfo ?: return

        // 목록 끝에서는 더 갈 곳이 없다. 끈 거리를 쌓아두면 되돌아올 때 그만큼 헛돈다.
        if (isAtListEnd(index)) {
            draggedSinceMove = draggedSinceMove.coerceIn(-dragging.size / 2f, dragging.size / 2f)
            return
        }

        // 화면 끝까지 끌면 목록이 따라 흐르게 한다 — 안 그러면 보이는 범위 밖으로 옮길 수 없다.
        val startOffset = dragging.offset + draggedSinceMove
        val endOffset = startOffset + dragging.size
        val overscroll = when {
            dragDirection > 0 ->
                (endOffset - lazyListState.layoutInfo.viewportEndOffset).coerceAtLeast(0f)

            dragDirection < 0 ->
                (startOffset - lazyListState.layoutInfo.viewportStartOffset).coerceAtMost(0f)

            else -> 0f
        }
        if (overscroll != 0f) scrollChannel.trySend(overscroll)
    }

    /**
     * 맨 위 줄이 자리를 바꿀 때 목록이 따라 스크롤되지 않게 지금 보이는 자리를 붙잡아 둔다.
     *
     * LazyColumn은 스크롤 위치를 맨 위 줄의 키로 기억한다. 그 줄이 아래로 내려가면 목록은 그 줄을
     * 계속 맨 위에 두려고 내용을 통째로 밀어 올린다 — 손가락은 가만히 있는데 화면이 뛴다.
     */
    private fun pinScroll(from: Int, to: Int) {
        val first = lazyListState.firstVisibleItemIndex
        if (from != first && to != first) return
        lazyListState.requestScrollToItem(first, lazyListState.firstVisibleItemScrollOffset)
    }

    /** 끌린 쪽으로 맞닿은 줄. 화면 밖이면 null이고, 그때는 목록을 흘려 보이게 만든 뒤에 옮긴다. */
    private fun neighbourToward(index: Int): LazyListItemInfo? {
        val next = if (draggedSinceMove > 0) index + 1 else index - 1
        return lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == next }
    }

    private fun isAtListEnd(index: Int) = when {
        draggedSinceMove > 0 -> index == lazyListState.layoutInfo.totalItemsCount - 1
        draggedSinceMove < 0 -> index == 0
        else -> true
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
