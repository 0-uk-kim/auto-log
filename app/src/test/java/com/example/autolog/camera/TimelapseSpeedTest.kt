package com.example.autolog.camera

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelapseSpeedTest {

    @Test
    fun `하이퍼랩스 배속은 갤럭시 카메라와 같은 4 8 16 32배다`() {
        assertEquals(listOf(4, 8, 16, 32), TimelapseSpeed.choices.map { it.factor })
    }

    @Test
    fun `배속이 켜져 있으면 하이퍼랩스 모드다`() {
        assertEquals(CameraMode.Hyperlapse, CameraUiState(timelapse = TimelapseSpeed.Eight).mode)
        assertEquals(CameraMode.Video, CameraUiState(timelapse = TimelapseSpeed.Off).mode)
    }

    @Test
    fun `모드 줄 끝에서 더 쓸면 그대로다`() {
        assertEquals(CameraMode.Video, CameraMode.Hyperlapse.neighbor(towardEnd = true))
        assertEquals(CameraMode.Hyperlapse, CameraMode.Hyperlapse.neighbor(towardEnd = false))
        assertEquals(CameraMode.Video, CameraMode.Video.neighbor(towardEnd = true))
    }

    @Test
    fun `완성본 길이는 찍은 시간을 배속으로 나눈 값이다`() {
        assertEquals(3.seconds, TimelapseSpeed.Eight.outputOf(24.seconds))
        assertEquals(30.seconds, TimelapseSpeed.Off.outputOf(30.seconds))
    }

    @Test
    fun `타임랩스를 켜면 음소거를 바꿀 수 없다`() {
        assertTrue(CameraUiState(timelapse = TimelapseSpeed.Off).canToggleMute)
        assertFalse(CameraUiState(timelapse = TimelapseSpeed.Eight).canToggleMute)
    }

    @Test
    fun `완성본을 만드는 동안에는 새로 찍지 않는다`() {
        assertTrue(CameraUiState(timelapseProgress = 0).isEncodingTimelapse)
        assertFalse(CameraUiState().isEncodingTimelapse)
    }
}
