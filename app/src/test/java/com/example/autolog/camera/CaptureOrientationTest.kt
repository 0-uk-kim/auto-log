package com.example.autolog.camera

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureOrientationTest {

    @Test
    fun `세로는 기기를 어떻게 들든 세로로 찍는다`() {
        assertEquals(Surface.ROTATION_0, CaptureOrientation.Portrait.targetRotation(Surface.ROTATION_90))
    }

    @Test
    fun `가로는 기기가 눕혀진 쪽을 따르고 세워 들면 왼쪽으로 본다`() {
        val landscape = CaptureOrientation.Landscape
        assertEquals(Surface.ROTATION_270, landscape.targetRotation(Surface.ROTATION_270))
        assertEquals(Surface.ROTATION_90, landscape.targetRotation(Surface.ROTATION_90))
        assertEquals(Surface.ROTATION_90, landscape.targetRotation(Surface.ROTATION_0))
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
