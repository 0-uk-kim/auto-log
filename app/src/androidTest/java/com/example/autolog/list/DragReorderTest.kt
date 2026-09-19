package com.example.autolog.list

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 끈 거리와 실제 이동량이 같은지 재는 테스트 (B0).
 *
 * 손가락이 한 행을 지나면 한 칸만 움직여야 한다. 예전 구현은 목록이 스스로 스크롤을 되감는
 * 바람에 한 번 옮길 때마다 다음 목표가 또 걸려, 한 행을 끌어도 바닥까지 떨어졌다.
 *
 * 한 행마다 이벤트를 여러 번 나눠 넣고 그 사이에 레이아웃이 돌게 둔다 — 한 번에 몰아 넣으면
 * 프레임 사이에서 벌어지는 이 문제가 그대로 지나간다.
 */
class DragReorderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rowHeight = 72.dp
    private val itemCount = 40

    private var rowHeightPx = 0f
    private var touchSlopPx = 0f
    private lateinit var order: () -> List<Int>

    @Test
    fun `한 행을 끌면 한 칸만 내려간다`() {
        showList()

        drag(item = 0, rows = 1f)

        assertEquals(listOf(1, 0) + (2 until itemCount), order())
    }

    @Test
    fun `세 행을 끌면 세 칸 내려간다`() {
        showList()

        drag(item = 0, rows = 3f)

        assertEquals(listOf(1, 2, 3, 0) + (4 until itemCount), order())
    }

    @Test
    fun `위로 한 행을 끌면 한 칸만 올라간다`() {
        showList()

        drag(item = 4, rows = -1f)

        assertEquals(listOf(0, 1, 2, 4, 3) + (5 until itemCount), order())
    }

    @Test
    fun `반 행에 못 미치게 끌면 제자리에 남는다`() {
        showList()

        drag(item = 0, rows = 0.4f)

        assertEquals((0 until itemCount).toList(), order())
    }

    /**
     * 한 칸 옮긴 **뒤**에 손가락을 거의 멈춰도 계속 떨어지지 않는지 (B0의 핵심).
     *
     * 자리를 옮기면 목록이 그 줄을 붙잡아 스스로 스크롤을 되감는다. 그러면 다음 줄이 또 목표로
     * 걸리고, 손가락이 멈춰 있어도 한 프레임에 한 칸씩 바닥까지 내려간다.
     */
    @Test
    fun `한 칸 옮긴 뒤 멈춰 있으면 더 내려가지 않는다`() {
        val state = showList()

        composeRule.runOnIdle { state.onDragStart(0) }
        composeRule.runOnIdle { state.onDrag(Offset(0f, rowHeightPx * 0.6f)) }
        composeRule.waitForIdle()
        assertEquals(listOf(1, 0) + (2 until itemCount), order())

        // 손가락이 사실상 멈춘 상태로 이벤트만 더 들어온다.
        repeat(10) { composeRule.runOnIdle { state.onDrag(Offset(0f, 1f)) } }

        assertEquals(listOf(1, 0) + (2 until itemCount), order())
    }

    @Test
    fun `위로 한 칸 옮긴 뒤 멈춰 있으면 더 올라가지 않는다`() {
        val state = showList()

        composeRule.runOnIdle { state.onDragStart(4) }
        composeRule.runOnIdle { state.onDrag(Offset(0f, -rowHeightPx * 0.6f)) }
        composeRule.waitForIdle()
        assertEquals(listOf(0, 1, 2, 4, 3) + (5 until itemCount), order())

        repeat(10) { composeRule.runOnIdle { state.onDrag(Offset(0f, -1f)) } }

        assertEquals(listOf(0, 1, 2, 4, 3) + (5 until itemCount), order())
    }

    /** 끌었다가 도로 제자리로 가져오면 순서도 돌아와야 한다 — 칸을 지날 때마다 남는 거리 계산을 건다. */
    @Test
    fun `두 칸 내렸다가 도로 올리면 처음 순서로 돌아온다`() {
        val state = showList()

        composeRule.runOnIdle { state.onDragStart(0) }
        composeRule.runOnIdle { state.onDrag(Offset(0f, rowHeightPx * 2)) }
        composeRule.waitForIdle()
        assertEquals(listOf(1, 2, 0) + (3 until itemCount), order())

        composeRule.runOnIdle { state.onDrag(Offset(0f, -rowHeightPx * 2)) }
        composeRule.waitForIdle()

        assertEquals((0 until itemCount).toList(), order())
    }

    /** 손가락을 [rows] 행만큼 끈다. 한 행을 여덟 번에 나눠 넣어 프레임 사이 상태까지 지나가게 한다. */
    private fun drag(item: Int, rows: Float) {
        val handle = composeRule.onNodeWithTag(handleTag(item))
        val steps = (8 * kotlin.math.abs(rows)).toInt().coerceAtLeast(1)
        val stepPx = rows * rowHeightPx / steps

        handle.performTouchInput { down(center) }
        // 슬롭을 정확히 채우기만 한다 — 넘지 않으므로 아직 이동으로 세지 않는다.
        handle.performTouchInput { moveBy(Offset(0f, touchSlopPx * rows.sign())) }
        repeat(steps) {
            handle.performTouchInput { moveBy(Offset(0f, stepPx)) }
        }
        handle.performTouchInput { up() }
        composeRule.waitForIdle()
    }

    private fun Float.sign() = if (this < 0) -1f else 1f

    /**
     * 화면과 같은 경로로 목록을 흘린다 — 자리 바꿈은 ViewModel의 StateFlow를 거쳐 돌아오므로
     * 이벤트와 화면 사이에 한 박자가 있다. 그 틈에서 벌어지는 문제라 여기서도 그대로 재현한다.
     */
    private fun showList(): DragDropState {
        val source = MutableStateFlow((0 until itemCount).toList())
        order = { source.value }
        lateinit var dragDropState: DragDropState

        composeRule.setContent {
            val items by source.collectAsState()
            rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
            touchSlopPx = LocalViewConfiguration.current.touchSlop

            dragDropState = rememberReorderableRows(
                items = items,
                onMove = { from, to -> source.value = source.value.moved(from, to) },
            )
        }
        composeRule.waitForIdle()
        return dragDropState
    }

    /** `ClipList`와 같은 뼈대 — 손잡이에서만 끌고, 끌리는 줄만 애니메이션 없이 띄워 그린다. */
    @Composable
    private fun rememberReorderableRows(items: List<Int>, onMove: (Int, Int) -> Unit): DragDropState {
        val lazyListState = rememberLazyListState()
        val dragDropState = rememberDragDropState(
            lazyListState = lazyListState,
            onMove = onMove,
            onDrop = {},
        )

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = rowHeight),
        ) {
            itemsIndexed(items, key = { _, item -> item }) { position, item ->
                val isDragging = position == dragDropState.draggingItemIndex
                val currentPosition by rememberUpdatedState(position)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .then(
                            if (isDragging) {
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationY = dragDropState.draggingItemOffset
                                    }
                            } else {
                                Modifier.animateItem()
                            },
                        ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .testTag(handleTag(item))
                            .size(rowHeight)
                            .pointerInput(item) {
                                detectDragGestures(
                                    onDragStart = { dragDropState.onDragStart(currentPosition) },
                                    onDragEnd = dragDropState::onDragInterrupted,
                                    onDragCancel = dragDropState::onDragInterrupted,
                                    onDrag = { change, offset ->
                                        change.consume()
                                        dragDropState.onDrag(offset)
                                    },
                                )
                            },
                    )
                }
            }
        }

        return dragDropState
    }

    private fun handleTag(item: Int) = "handle-$item"
}
