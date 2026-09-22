package com.example.autolog.camera

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraUiStateTest {

    @Test
    fun `가로를 골랐는데 세워 들면 눕히라고 한다`() {
        val state = CameraUiState(orientation = CaptureOrientation.Landscape, deviceRotation = Surface.ROTATION_0)
        assertEquals(CaptureOrientation.Landscape, state.turnHint)
    }

    @Test
    fun `세로를 골랐는데 눕혀 들면 세우라고 한다`() {
        val state = CameraUiState(orientation = CaptureOrientation.Portrait, deviceRotation = Surface.ROTATION_90)
        assertEquals(CaptureOrientation.Portrait, state.turnHint)
    }

    @Test
    fun `맞게 들고 있으면 알리지 않는다`() {
        assertNull(CameraUiState(orientation = CaptureOrientation.Portrait, deviceRotation = Surface.ROTATION_0).turnHint)
        assertNull(CameraUiState(orientation = CaptureOrientation.Landscape, deviceRotation = Surface.ROTATION_90).turnHint)
    }

    @Test
    fun `녹화 중에는 알리지 않는다`() {
        val state = CameraUiState(
            isRecording = true,
            orientation = CaptureOrientation.Portrait,
            deviceRotation = Surface.ROTATION_90,
        )
        assertNull(state.turnHint)
    }

    @Test
    fun `카운트다운 중에는 알리지 않는다`() {
        val state = CameraUiState(
            countdown = 3,
            orientation = CaptureOrientation.Portrait,
            deviceRotation = Surface.ROTATION_90,
        )
        assertNull(state.turnHint)
    }
}
