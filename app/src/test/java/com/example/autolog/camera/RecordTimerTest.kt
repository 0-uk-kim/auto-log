package com.example.autolog.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordTimerTest {

    @Test
    fun `누를 때마다 끔 1초 2초 3초를 돌고 다시 끔으로 온다`() {
        assertEquals(RecordTimer.One, RecordTimer.Off.next())
        assertEquals(RecordTimer.Two, RecordTimer.One.next())
        assertEquals(RecordTimer.Three, RecordTimer.Two.next())
        assertEquals(RecordTimer.Off, RecordTimer.Three.next())
    }
}
