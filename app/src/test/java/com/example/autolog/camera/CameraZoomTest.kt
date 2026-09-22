package com.example.autolog.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CameraZoomTest {

    @Test
    fun `광각이 있으면 가장 넓은 배율부터 버튼을 둔다`() {
        assertEquals(listOf(0.6f, 1f, 2f), ZoomRange(0.6f, 10f).presets())
    }

    @Test
    fun `범위를 벗어난 버튼은 두지 않는다`() {
        assertEquals(listOf(1f), ZoomRange(1f, 1.8f).presets())
    }

    @Test
    fun `배율을 못 바꾸는 카메라는 조작 대상이 아니다`() {
        assertFalse(ZoomRange(1f, 1f).isZoomable)
    }

    @Test
    fun `핀치 배율은 기기 범위 안으로 자른다`() {
        val range = ZoomRange(0.5f, 8f)
        assertEquals(8f, range.clamp(12f))
        assertEquals(0.5f, range.clamp(0.2f))
    }

    @Test
    fun `버튼 사이 배율은 아래쪽 버튼 구간이다`() {
        val presets = listOf(0.5f, 1f, 2f)
        assertEquals(1f, presets.activePreset(1.4f))
        assertEquals(2f, presets.activePreset(5f))
        assertEquals(0.5f, presets.activePreset(0.7f))
        assertEquals(1f, presets.activePreset(0.98f))
    }

    @Test
    fun `배율 표시는 정수면 소수점을 뗀다`() {
        assertEquals("1x", formatZoom(1f))
        assertEquals("2x", formatZoom(1.98f))
        assertEquals("1.4x", formatZoom(1.43f))
        assertEquals("0.6x", formatZoom(0.6f))
    }
}
