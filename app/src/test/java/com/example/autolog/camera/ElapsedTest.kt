package com.example.autolog.camera

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedTest {

    @Test
    fun `같은 초 안에서 오는 상태 이벤트는 같은 경과 시간이 된다`() {
        assertEquals(3.seconds, 3_000_000_000L.nanosToWholeSeconds())
        assertEquals(3.seconds, 3_999_999_999L.nanosToWholeSeconds())
    }

    @Test
    fun `초가 넘어가면 경과 시간도 넘어간다`() {
        assertEquals(4.seconds, 4_000_000_000L.nanosToWholeSeconds())
    }

    @Test
    fun `같은 초 안의 이벤트는 상태를 바꾸지 않는다`() {
        val state = CameraUiState(isRecording = true, elapsed = 3_100_000_000L.nanosToWholeSeconds())
        assertEquals(state, state.copy(elapsed = 3_900_000_000L.nanosToWholeSeconds()))
    }
}
