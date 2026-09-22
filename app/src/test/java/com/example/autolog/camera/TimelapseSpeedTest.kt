package com.example.autolog.camera

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelapseSpeedTest {

    @Test
    fun `누를 때마다 끔 5배 10배 30배를 돌고 다시 끔으로 온다`() {
        assertEquals(TimelapseSpeed.Five, TimelapseSpeed.Off.next())
        assertEquals(TimelapseSpeed.Ten, TimelapseSpeed.Five.next())
        assertEquals(TimelapseSpeed.Thirty, TimelapseSpeed.Ten.next())
        assertEquals(TimelapseSpeed.Off, TimelapseSpeed.Thirty.next())
    }

    @Test
    fun `완성본 길이는 찍은 시간을 배속으로 나눈 값이다`() {
        assertEquals(3.seconds, TimelapseSpeed.Ten.outputOf(30.seconds))
        assertEquals(30.seconds, TimelapseSpeed.Off.outputOf(30.seconds))
    }

    @Test
    fun `타임랩스를 켜면 음소거를 바꿀 수 없다`() {
        assertTrue(CameraUiState(timelapse = TimelapseSpeed.Off).canToggleMute)
        assertFalse(CameraUiState(timelapse = TimelapseSpeed.Ten).canToggleMute)
    }

    @Test
    fun `완성본을 만드는 동안에는 새로 찍지 않는다`() {
        assertTrue(CameraUiState(timelapseProgress = 0).isEncodingTimelapse)
        assertFalse(CameraUiState().isEncodingTimelapse)
    }
}
