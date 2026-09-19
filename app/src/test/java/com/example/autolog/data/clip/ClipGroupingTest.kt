package com.example.autolog.data.clip

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipGroupingTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `자정을 걸친 클립은 종료 시각이 속한 날짜로 묶인다`() {
        val crossing = clip(id = 1, startedAt = "2026-09-19T23:59:50", durationMs = 20_000)

        val grouped = listOf(crossing).groupByRecordedDate(seoul)

        assertEquals(setOf(LocalDate.of(2026, 9, 20)), grouped.keys)
    }

    @Test
    fun `자정 직전에 끝난 클립은 그날에 남는다`() {
        val justBefore = clip(id = 1, startedAt = "2026-09-19T23:59:50", durationMs = 9_000)

        val grouped = listOf(justBefore).groupByRecordedDate(seoul)

        assertEquals(setOf(LocalDate.of(2026, 9, 19)), grouped.keys)
    }

    @Test
    fun `묶음 안은 종료 시각 오름차순이다`() {
        val late = clip(id = 1, startedAt = "2026-09-19T10:00:00", durationMs = 5_000)
        val early = clip(id = 2, startedAt = "2026-09-19T09:00:00", durationMs = 5_000)

        val today = listOf(late, early).groupByRecordedDate(seoul).values.single()

        assertEquals(listOf(2L, 1L), today.map { it.id })
    }

    @Test
    fun `여러 날짜는 최근 날짜부터 나온다`() {
        val older = clip(id = 1, startedAt = "2026-09-17T10:00:00", durationMs = 5_000)
        val newer = clip(id = 2, startedAt = "2026-09-19T10:00:00", durationMs = 5_000)

        val dates = listOf(older, newer).groupByRecordedDate(seoul).keys.toList()

        assertEquals(listOf(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 17)), dates)
    }

    @Test
    fun `clipsOn은 자정을 걸친 클립을 끝난 날짜에서 찾는다`() {
        val crossing = clip(id = 1, startedAt = "2026-09-19T23:59:50", durationMs = 20_000)
        val sameDay = clip(id = 2, startedAt = "2026-09-20T08:00:00", durationMs = 3_000)

        val clips = listOf(crossing, sameDay)

        assertEquals(emptyList<Long>(), clips.clipsOn(LocalDate.of(2026, 9, 19), seoul).map { it.id })
        assertEquals(listOf(1L, 2L), clips.clipsOn(LocalDate.of(2026, 9, 20), seoul).map { it.id })
    }

    @Test
    fun `묶는 기준 시간대는 기기 시간대를 따른다`() {
        // 서울 09-20 00:00:10 = UTC 09-19 15:00:10 — 같은 클립이 시간대에 따라 다른 날짜에 들어간다.
        val crossing = clip(id = 1, startedAt = "2026-09-19T23:59:50", durationMs = 20_000)

        val inUtc = listOf(crossing).groupByRecordedDate(ZoneId.of("UTC")).keys

        assertEquals(setOf(LocalDate.of(2026, 9, 19)), inUtc)
    }

    private fun clip(id: Long, startedAt: String, durationMs: Long) = Clip(
        id = id,
        displayName = "AUTOLOG_$id.mp4",
        durationMs = durationMs,
        startedAt = LocalDateTime.parse(startedAt).atZone(seoul).toInstant(),
    )
}

