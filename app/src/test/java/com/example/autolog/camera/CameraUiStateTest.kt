package com.example.autolog.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraUiStateTest {

    @Test
    fun `가로를 골랐는데 세워 들면 눕히라고 한다`() {
        val state = CameraUiState(orientation = CaptureOrientation.Landscape, isDeviceSideways = false)
        assertEquals(CaptureOrientation.Landscape, state.turnHint)
    }

    @Test
    fun `세로를 골랐는데 눕혀 들면 세우라고 한다`() {
        val state = CameraUiState(orientation = CaptureOrientation.Portrait, isDeviceSideways = true)
        assertEquals(CaptureOrientation.Portrait, state.turnHint)
    }

    @Test
    fun `맞게 들고 있으면 알리지 않는다`() {
        assertNull(CameraUiState(orientation = CaptureOrientation.Portrait, isDeviceSideways = false).turnHint)
        assertNull(CameraUiState(orientation = CaptureOrientation.Landscape, isDeviceSideways = true).turnHint)
    }

    @Test
    fun `녹화 중에는 알리지 않는다`() {
        val state = CameraUiState(
            isRecording = true,
            orientation = CaptureOrientation.Portrait,
            isDeviceSideways = true,
        )
        assertNull(state.turnHint)
    }
}
