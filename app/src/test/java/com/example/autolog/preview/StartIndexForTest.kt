package com.example.autolog.preview

import com.example.autolog.data.clip.Clip
import com.example.autolog.navigation.Preview
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class StartIndexForTest {

    /** 순서를 바꾼 목록 — 가장 나중에 찍은 클립(3)이 맨 앞에 와 있다. */
    private val reordered = listOf(
        clip(id = 3, endedAtSecond = 30),
        clip(id = 1, endedAtSecond = 10),
        clip(id = 2, endedAtSecond = 20),
    )

    @Test
    fun `카메라 좌측 하단으로 들어오면 가장 나중에 찍은 클립이다`() {
        assertEquals(0, reordered.startIndexFor(Preview.LATEST_CLIP))
    }

    @Test
    fun `순서를 건드리지 않았다면 그 클립이 마지막 자리에 있다`() {
        val natural = listOf(clip(1, 10), clip(2, 20), clip(3, 30))

        assertEquals(2, natural.startIndexFor(Preview.LATEST_CLIP))
    }

    @Test
    fun `목록에서 들어오면 탭한 자리 그대로다`() {
        assertEquals(2, reordered.startIndexFor(2))
    }

    @Test
    fun `목록이 줄어든 뒤 들어와도 범위 안으로 당겨진다`() {
        assertEquals(2, reordered.startIndexFor(7))
    }

    @Test
    fun `클립이 없으면 0이다`() {
        assertEquals(0, emptyList<Clip>().startIndexFor(Preview.LATEST_CLIP))
        assertEquals(0, emptyList<Clip>().startIndexFor(3))
    }

    private fun clip(id: Long, endedAtSecond: Long) = Clip(
        id = id,
        displayName = "AUTOLOG_$id.mp4",
        durationMs = 1_000,
        startedAt = Instant.ofEpochSecond(endedAtSecond).minusMillis(1_000),
    )
}
