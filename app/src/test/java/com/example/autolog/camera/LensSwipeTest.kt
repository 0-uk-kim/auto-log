package com.example.autolog.camera

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LensSwipeTest {

    private val min = 100f

    @Test
    fun `위로 쓸어도 아래로 쓸어도 전환한다`() {
        assertTrue(isVerticalSwipe(Offset(0f, -150f), min))
        assertTrue(isVerticalSwipe(Offset(20f, 150f), min))
    }

    @Test
    fun `짧게 움직인 것은 스와이프가 아니다`() {
        assertFalse(isVerticalSwipe(Offset(0f, 60f), min))
    }

    @Test
    fun `비스듬하거나 가로로 쓴 것은 무시한다`() {
        assertFalse(isVerticalSwipe(Offset(120f, 150f), min))
        assertFalse(isVerticalSwipe(Offset(300f, 0f), min))
    }
}
