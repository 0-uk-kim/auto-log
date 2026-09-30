package com.example.autolog.data.clip

import com.example.autolog.data.db.ClipSegmentEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipSegmentsTest {

    private val duration = 10_000L
    private val whole = ClipSegments.whole(duration)

    @Test
    fun 분할하고_가운데_조각을_지우면_중간이_잘린다() {
        val cut = whole.splitAt(3_000, duration).splitAt(7_000, duration).remove(1)

        assertEquals(listOf(ClipSegment(0, 3_000), ClipSegment(7_000, duration)), cut.items)
        assertEquals(6_000L, cut.lengthMs)
        assertFalse(cut.coversWhole(duration))
    }

    @Test
    fun 분할만_하면_잘린_것이_없다() {
        val split = whole.splitAt(4_000, duration)

        assertEquals(2, split.items.size)
        assertTrue(split.coversWhole(duration))
    }

    @Test
    fun 최소_길이보다_짧은_조각이_생기는_자리는_나누지_않는다() {
        assertFalse(whole.canSplitAt(400, duration))
        assertFalse(whole.canSplitAt(9_600, duration))
        assertEquals(whole, whole.splitAt(400, duration))
    }

    @Test
    fun 짧은_클립도_가운데에서_나눌_수_있다() {
        assertTrue(ClipSegments.whole(1_500).canSplitAt(750, 1_500))
    }

    @Test
    fun 조각_사이_빈_곳은_나눌_수_없다() {
        val cut = whole.splitAt(3_000, duration).splitAt(7_000, duration).remove(1)

        assertFalse(cut.canSplitAt(5_000, duration))
    }

    @Test
    fun 마지막_한_조각은_지우지_않는다() {
        assertFalse(whole.canRemove)
        assertEquals(whole, whole.remove(0))
    }

    @Test
    fun 손잡이는_이웃_조각을_넘지_못한다() {
        val cut = whole.splitAt(3_000, duration).splitAt(7_000, duration).remove(1)

        assertEquals(3_000L, cut.withStart(1, 1_000, duration).items[1].startMs)
        assertEquals(7_000L, cut.withEnd(0, 9_000, duration).items[0].endMs)
    }

    @Test
    fun 손잡이는_자기_조각을_최소_길이_아래로_줄이지_못한다() {
        val moved = whole.withEnd(0, 6_000, duration).withStart(0, 5_900, duration)

        assertEquals(ClipSegment(5_500, 6_000), moved.items.single())
    }

    @Test
    fun 빈_곳에서는_다음_조각으로_끝을_지나면_첫_조각으로_건너뛴다() {
        val cut = ClipSegments.of(listOf(ClipSegment(1_000, 3_000), ClipSegment(6_000, 8_000)), duration)

        assertNull(cut.nextPlayableFrom(2_000))
        assertEquals(6_000L, cut.nextPlayableFrom(3_000))
        assertEquals(1_000L, cut.nextPlayableFrom(8_500))
        assertEquals(1_000L, cut.nextPlayableFrom(0))
    }

    @Test
    fun 저장된_조각은_클립_길이에_맞추고_겹치면_합친다() {
        val loaded = ClipSegments.of(
            listOf(ClipSegment(8_000, 12_000), ClipSegment(1_000, 4_000), ClipSegment(3_000, 5_000), ClipSegment(9_700, 9_900)),
            duration,
        )

        assertEquals(listOf(ClipSegment(1_000, 5_000), ClipSegment(8_000, duration)), loaded.items)
    }

    @Test
    fun 최소_길이보다_짧은_클립은_전체가_한_조각이다() {
        val short = ClipSegments.whole(300)

        assertFalse(short.canSplitAt(150, 300))
        assertEquals(ClipSegment(0, 300), short.withStart(0, 150, 300).items.single())
    }

    @Test
    fun 저장된_조각을_입히면_재생_길이가_바뀐다() {
        val clip = clip().withSegments(
            listOf(ClipSegmentEntity(1, 0, 2_000), ClipSegmentEntity(1, 5_000, 6_000)),
        )

        assertEquals(3_000L, clip.playedDurationMs)
        assertEquals(duration, clip.durationMs)
    }

    @Test
    fun 원본이_짧아져_조각이_전체가_되면_자르지_않은_것이다() {
        val clip = clip(durationMs = 3_000).withSegments(listOf(ClipSegmentEntity(1, 0, 8_000)))

        assertNull(clip.segments)
    }

    private fun clip(durationMs: Long = duration) = Clip(
        id = 1,
        displayName = "a.mp4",
        durationMs = durationMs,
        startedAt = Instant.EPOCH,
    )
}
