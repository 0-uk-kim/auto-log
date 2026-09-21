package com.example.autolog.data.vlog

import com.example.autolog.camera.CaptureOrientation.Landscape
import com.example.autolog.camera.CaptureOrientation.Portrait
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergeFrameTest {

    @Test
    fun `방향이 모두 같으면 크기를 정하지 않는다`() {
        assertNull(mergeFrameFor(listOf(Portrait, Portrait)))
        assertNull(mergeFrameFor(listOf(Landscape)))
    }

    @Test
    fun `섞여 있으면 첫 클립 방향의 FHD로 맞춘다`() {
        assertEquals(MergeFrame(1080, 1920), mergeFrameFor(listOf(Portrait, Landscape, Landscape)))
        assertEquals(MergeFrame(1920, 1080), mergeFrameFor(listOf(Landscape, Portrait)))
    }
}
