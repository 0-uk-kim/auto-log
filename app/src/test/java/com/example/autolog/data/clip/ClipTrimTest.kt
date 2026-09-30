package com.example.autolog.data.clip

import com.example.autolog.data.db.ClipTrimEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTrimTest {

    private val duration = 10_000L

    @Test
    fun 클립_밖으로_나간_값은_클립_안으로_당긴다() {
        val trim = ClipTrim.of(-500, 12_000, duration)

        assertEquals(0L, trim.startMs)
        assertEquals(duration, trim.endMs)
        assertTrue(trim.coversWhole(duration))
    }

    @Test
    fun 시작을_끝에_붙이면_최소_길이만큼_앞에서_멈춘다() {
        val trim = ClipTrim.whole(duration).withEnd(6_000, duration).withStart(5_900, duration)

        assertEquals(5_000L, trim.startMs)
        assertEquals(6_000L, trim.endMs)
    }

    @Test
    fun 끝을_시작에_붙이면_최소_길이만큼_뒤에서_멈춘다() {
        val trim = ClipTrim.whole(duration).withStart(4_000, duration).withEnd(0, duration)

        assertEquals(4_000L, trim.startMs)
        assertEquals(5_000L, trim.endMs)
    }

    @Test
    fun 한쪽_손잡이를_옮겨도_다른_쪽은_그대로다() {
        val trim = ClipTrim.whole(duration).withStart(2_000, duration)

        assertEquals(2_000L, trim.startMs)
        assertEquals(duration, trim.endMs)
        assertFalse(trim.coversWhole(duration))
        assertEquals(8_000L, trim.lengthMs)
    }

    @Test
    fun 최소_길이보다_짧은_클립은_전체가_구간이다() {
        val trim = ClipTrim.whole(600).withStart(300, 600)

        assertEquals(0L, trim.startMs)
        assertEquals(600L, trim.endMs)
    }

    @Test
    fun 저장된_구간을_입히면_재생_길이가_바뀐다() {
        val clip = clip().withTrim(ClipTrimEntity(clipId = 1, startMs = 1_000, endMs = 4_000))

        assertEquals(3_000L, clip.playedDurationMs)
        assertEquals(duration, clip.durationMs)
    }

    @Test
    fun 원본이_짧아져_구간이_전체가_되면_자르지_않은_것이다() {
        val clip = clip(durationMs = 3_000).withTrim(ClipTrimEntity(clipId = 1, startMs = 0, endMs = 8_000))

        assertNull(clip.trim)
    }

    private fun clip(durationMs: Long = duration) = Clip(
        id = 1,
        displayName = "a.mp4",
        durationMs = durationMs,
        startedAt = Instant.EPOCH,
    )
}
