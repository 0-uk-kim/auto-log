package com.example.autolog.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordTimerTest {

    @Test
    fun `누를 때마다 끔 3초 10초를 돌고 다시 끔으로 온다`() {
        assertEquals(RecordTimer.Three, RecordTimer.Off.next())
        assertEquals(RecordTimer.Ten, RecordTimer.Three.next())
        assertEquals(RecordTimer.Off, RecordTimer.Ten.next())
    }
}
