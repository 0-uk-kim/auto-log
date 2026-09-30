package com.example.autolog.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordTimerTest {

    @Test
    fun `타이머 선택지는 갤럭시 카메라와 같은 끔 2초 5초 10초다`() {
        assertEquals(listOf(0, 2, 5, 10), RecordTimer.entries.map { it.seconds })
    }
}
