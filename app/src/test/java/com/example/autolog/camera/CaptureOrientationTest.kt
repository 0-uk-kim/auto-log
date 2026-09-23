package com.example.autolog.camera

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureOrientationTest {

    @Test
    fun `세워 들면 세로로 찍는다`() {
        assertEquals(Surface.ROTATION_0, recordingRotation(Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_0, recordingRotation(Surface.ROTATION_180))
    }

    @Test
    fun `눕혀 들면 눕혀진 쪽을 따라 가로로 찍는다`() {
        assertEquals(Surface.ROTATION_90, recordingRotation(Surface.ROTATION_90))
        assertEquals(Surface.ROTATION_270, recordingRotation(Surface.ROTATION_270))
    }

    @Test
    fun `기울기를 화면 회전값으로 바꾼다`() {
        assertEquals(Surface.ROTATION_0, degreesToRotation(10))
        assertEquals(Surface.ROTATION_270, degreesToRotation(90))
        assertEquals(Surface.ROTATION_180, degreesToRotation(180))
        assertEquals(Surface.ROTATION_90, degreesToRotation(270))
        assertEquals(Surface.ROTATION_0, degreesToRotation(350))
        assertNull(degreesToRotation(-1))
    }
}
