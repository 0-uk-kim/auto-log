package com.example.autolog.list

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 격자에서 끈 거리와 실제 이동이 맞는지 잰다.
 *
 * 칸 자리는 고정이고 항목만 그 자리를 옮겨 다닌다 — 실제 격자도 자리를 바꾸면 다음 프레임에 그렇게 다시 놓인다.
 */
class GridReorderTest {

    private val columns = 3
    private val cellWidth = 100f
    private val cellHeight = 178f
    private val itemCount = 12

    private var order = (0 until itemCount).toList()
    private val reorder = GridReorder(
        cells = {
            order.indices.map { i ->
                val left = (i % columns) * cellWidth
                val top = (i / columns) * cellHeight
                GridCell(i, Rect(left, top, left + cellWidth, top + cellHeight))
            }
        },
        onMove = { from, to -> order = order.moved(from, to) },
    )

    @Test
    fun `오른쪽 칸으로 반 넘게 끌면 한 칸 옮긴다`() {
        drag(from = 0, Offset(cellWidth * 0.6f, 0f))

        assertEquals(listOf(1, 0) + (2 until itemCount), order)
    }

    @Test
    fun `반에 못 미치게 끌면 제자리에 남는다`() {
        drag(from = 0, Offset(cellWidth * 0.4f, cellHeight * 0.4f))

        assertEquals((0 until itemCount).toList(), order)
    }

    @Test
    fun `아래 줄로 끌면 바로 밑 칸 자리로 간다`() {
        drag(from = 0, Offset(0f, cellHeight))

        assertEquals(listOf(1, 2, 3, 0) + (4 until itemCount), order)
    }

    /** 옮긴 **뒤**에 손가락을 거의 멈춰도 다음 칸이 또 걸리지 않는지 (B0). */
    @Test
    fun `한 칸 옮긴 뒤 멈춰 있으면 더 옮기지 않는다`() {
        reorder.start(0)
        reorder.drag(Offset(cellWidth * 0.6f, 0f))
        repeat(10) { reorder.drag(Offset(1f, 0f)) }

        assertEquals(listOf(1, 0) + (2 until itemCount), order)
        assertEquals(1, reorder.draggingIndex)
    }

    @Test
    fun `끌었다가 도로 가져오면 처음 순서로 돌아온다`() {
        reorder.start(0)
        steps(Offset(cellWidth * 2, 0f))
        assertEquals(listOf(1, 2, 0) + (3 until itemCount), order)

        steps(Offset(-cellWidth * 2, 0f))

        assertEquals((0 until itemCount).toList(), order)
    }

    @Test
    fun `목록이 흐른 만큼 칸도 손가락 밑에 남는다`() {
        reorder.start(0)
        reorder.scrolled(cellHeight)

        assertEquals(Offset(0f, cellHeight), reorder.offset)
    }

    private fun drag(from: Int, by: Offset) {
        reorder.start(from)
        steps(by)
        reorder.stop()
    }

    /** 손가락 이동을 여덟 번에 나눠 넣는다 — 한 번에 몰아 넣으면 지나가는 칸의 판정을 건너뛴다. */
    private fun steps(by: Offset) {
        repeat(8) { reorder.drag(by / 8f) }
    }
}
