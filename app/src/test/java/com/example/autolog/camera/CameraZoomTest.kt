package com.example.autolog.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `배율 애니메이션은 양 끝에서 요청한 배율과 같다`() {
        assertEquals(0.6f, interpolateZoom(0.6f, 2f, 0f), 1e-4f)
        assertEquals(2f, interpolateZoom(0.6f, 2f, 1f), 1e-4f)
    }

    @Test
    fun `배율 애니메이션은 곱셈 기준으로 잇는다`() {
        // 0.5x→2x의 로그 중간은 1x. 감속 때문에 t=0.5면 이미 중간을 넘는다.
        val mid = interpolateZoom(0.5f, 2f, 0.5f)
        assertTrue(mid > 1f && mid < 2f)
        assertTrue(interpolateZoom(2f, 0.5f, 0.5f) < 1f)
    }

    @Test
    fun `선택되지 않은 버튼은 x와 앞자리 0을 뗀다`() {
        assertEquals(".6", formatZoomShort(0.6f))
        assertEquals("2", formatZoomShort(2f))
    }

    @Test
    fun `다이얼을 왼쪽으로 끌면 확대 오른쪽이면 축소다`() {
        assertTrue(dragZoom(1f, -50f, 100f) > 1f)
        assertTrue(dragZoom(1f, 50f, 100f) < 1f)
        // 로그 간격이라 같은 거리를 오가면 제자리다.
        assertEquals(1f, dragZoom(dragZoom(1f, -80f, 100f), 80f, 100f), 1e-4f)
    }

    @Test
    fun `다이얼 숫자는 넓은 쪽 끝과 1 2 3 5 10에만 단다`() {
        val labels = ZoomRange(0.6f, 10f).ticks().mapNotNull { it.label }
        assertEquals(listOf(".6", "1", "2", "3", "5", "10"), labels)
    }

    @Test
    fun `다이얼 눈금은 범위 양 끝을 넘지 않는다`() {
        val ticks = ZoomRange(0.6f, 10f).ticks()
        assertEquals(0.6f, ticks.first().ratio, 1e-4f)
        assertEquals(10f, ticks.last().ratio, 1e-4f)
    }
}
