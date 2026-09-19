package com.example.autolog.list

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {

    private val clips = listOf("a", "b", "c", "d")

    @Test
    fun `아래로 옮기면 사이 항목들이 앞으로 당겨진다`() {
        assertEquals(listOf("b", "c", "a", "d"), clips.moved(from = 0, to = 2))
    }

    @Test
    fun `위로 옮기면 사이 항목들이 뒤로 밀린다`() {
        assertEquals(listOf("a", "d", "b", "c"), clips.moved(from = 3, to = 1))
    }

    @Test
    fun `제자리로 옮기면 그대로다`() {
        assertEquals(clips, clips.moved(from = 2, to = 2))
    }

    @Test
    fun `범위를 벗어난 자리는 무시한다`() {
        assertEquals(clips, clips.moved(from = 0, to = 4))
        assertEquals(clips, clips.moved(from = -1, to = 1))
    }
}
