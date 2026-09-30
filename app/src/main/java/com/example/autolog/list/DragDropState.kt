package com.example.autolog.list

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.channels.Channel

/** 화면에 보이는 칸 하나의 자리. 격자 좌표계는 스크롤 영역의 시작점 기준이다. */
data class GridCell(val index: Int, val bounds: Rect)

/**
 * 격자 순서 변경의 판정부. 레이아웃에 기대지 않아 JVM 테스트로 잰다.
 *
 * 끌고 있는 칸의 **가운데**가 다른 칸 안에 들어가면 그 자리로 옮긴다. 옮긴 뒤에는 칸이 새 자리에서 다시
 * 그려지므로, 화면 위치가 그대로 남도록 두 자리의 차이만큼 [offset]을 덜어 낸다. 덜어 내지 않으면
 * 손가락이 멈춰 있어도 다음 칸이 또 걸려 한 번에 끝까지 떨어진다 (B0).
 */
class GridReorder(
    private val cells: () -> List<GridCell>,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingIndex: Int? = null
        private set

    /** 끌고 있는 칸을 제자리에서 얼마나 띄워 그릴지. */
    var offset: Offset = Offset.Zero
        private set

    fun start(index: Int): Boolean {
        if (cells().none { it.index == index }) return false
        draggingIndex = index
        offset = Offset.Zero
        return true
    }

    fun stop() {
        draggingIndex = null
        offset = Offset.Zero
    }

    /** 목록이 따라 흐른 만큼 칸도 손가락 밑에 남긴다. */
    fun scrolled(by: Float) {
        if (draggingIndex != null) offset += Offset(0f, by)
    }

    /** 손가락이 [delta]만큼 움직였다. 끌고 있는 칸이 지금 그려진 자리를 돌려준다. */
    fun drag(delta: Offset): Rect? {
        val index = draggingIndex ?: return null
        offset += delta
        val visible = cells()
        val home = visible.firstOrNull { it.index == index } ?: return null
        val dragged = home.bounds.translate(offset)
        val target = visible.firstOrNull { it.index != index && it.bounds.contains(dragged.center) }
            ?: return dragged
        onMove(index, target.index)
        draggingIndex = target.index
        offset += home.bounds.topLeft - target.bounds.topLeft
        return dragged
    }
}

/**
 * 격자 순서 변경. Compose Foundation에 재정렬 격자가 아직 없어서 직접 만든다.
 *
 * 끄는 것은 칸을 **길게 누른 뒤**에만 시작한다 — 짧은 누름은 미리보기, 그냥 끌면 스크롤이다.
 * 끄는 동안에는 목록의 순서만 바꾸고(메모리), 손을 떼는 순간 한 번만 저장한다 —
 * 한 칸 지날 때마다 Room에 쓰면 드래그 한 번에 쓰기가 수십 번 일어난다.
 */
class DragDropState internal constructor(
    private val gridState: LazyGridState,
    onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    private val reorder = GridReorder(
        cells = {
            gridState.layoutInfo.visibleItemsInfo.map {
                GridCell(it.index, Rect(it.offset.toOffset(), it.size.toSize()))
            }
        },
        onMove = { from, to ->
            pinScroll(from, to)
            onMove(from, to)
        },
    )

    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    var draggingItemOffset by mutableStateOf(Offset.Zero)
        private set

    internal val scrollChannel = Channel<Float>()

    internal fun onDragStart(index: Int) {
        if (!reorder.start(index)) return
        sync()
    }

    internal fun onDragInterrupted() {
        if (draggingItemIndex != null) onDrop()
        reorder.stop()
        sync()
    }

    internal fun onDrag(delta: Offset) {
        val dragged = reorder.drag(delta)
        sync()
        dragged ?: return

        // 화면 끝까지 끌면 목록이 따라 흐르게 한다 — 안 그러면 보이는 범위 밖으로 옮길 수 없다.
        val layout = gridState.layoutInfo
        val overscroll = when {
            delta.y > 0 -> (dragged.bottom - layout.viewportEndOffset).coerceAtLeast(0f)
            delta.y < 0 -> (dragged.top - layout.viewportStartOffset).coerceAtMost(0f)
            else -> 0f
        }
        if (overscroll != 0f) scrollChannel.trySend(overscroll)
    }

    internal fun onScrolled(by: Float) {
        reorder.scrolled(by)
        sync()
    }

    private fun sync() {
        draggingItemIndex = reorder.draggingIndex
        draggingItemOffset = reorder.offset
    }

    /**
     * 맨 앞 칸이 자리를 바꿀 때 격자가 따라 스크롤되지 않게 지금 보이는 자리를 붙잡아 둔다.
     * 격자는 스크롤 위치를 맨 앞 칸의 키로 기억해, 그 칸이 옮겨 가면 내용을 통째로 밀어 버린다.
     */
    private fun pinScroll(from: Int, to: Int) {
        val first = gridState.firstVisibleItemIndex
        if (from != first && to != first) return
        gridState.requestScrollToItem(first, gridState.firstVisibleItemScrollOffset)
    }
}

@Composable
fun rememberDragDropState(
    gridState: LazyGridState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
): DragDropState {
    val currentMove by rememberUpdatedState(onMove)
    val currentDrop by rememberUpdatedState(onDrop)
    val state = remember(gridState) {
        DragDropState(
            gridState = gridState,
            onMove = { from, to -> currentMove(from, to) },
            onDrop = { currentDrop() },
        )
    }

    LaunchedEffect(state) {
        for (diff in state.scrollChannel) {
            state.onScrolled(gridState.scrollBy(diff))
        }
    }

    return state
}

/** 순서 변경의 핵심 연산. 원본을 건드리지 않고 [from]의 항목을 [to] 자리로 옮긴 새 목록을 만든다. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
