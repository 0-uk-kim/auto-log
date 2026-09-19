package com.example.autolog.preview

import com.example.autolog.navigation.Preview
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveStartIndexTest {

    @Test
    fun `카메라 좌측 하단으로 들어오면 마지막 클립부터다`() {
        assertEquals(4, Preview.LATEST_CLIP.resolveStartIndex(clipCount = 5))
    }

    @Test
    fun `목록에서 들어오면 탭한 자리 그대로다`() {
        assertEquals(2, 2.resolveStartIndex(clipCount = 5))
    }

    @Test
    fun `목록이 줄어든 뒤 들어와도 범위 안으로 당겨진다`() {
        assertEquals(1, 7.resolveStartIndex(clipCount = 2))
    }

    @Test
    fun `클립이 없으면 0이다`() {
        assertEquals(0, Preview.LATEST_CLIP.resolveStartIndex(clipCount = 0))
        assertEquals(0, 3.resolveStartIndex(clipCount = 0))
    }
}
