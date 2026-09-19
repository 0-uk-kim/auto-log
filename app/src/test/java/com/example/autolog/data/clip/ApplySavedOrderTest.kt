package com.example.autolog.data.clip

import com.example.autolog.data.db.ClipOrderEntity
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplySavedOrderTest {

    private val day = LocalDate.of(2026, 9, 19)

    @Test
    fun `저장된 순서가 없으면 촬영 종료 시각 순이다`() {
        val clips = listOf(clip(id = 2, endedAtSecond = 20), clip(id = 1, endedAtSecond = 10))

        assertEquals(listOf(1L, 2L), clips.applySavedOrder(emptyList()).map { it.id })
    }

    @Test
    fun `저장된 순서가 촬영 순서를 이긴다`() {
        val clips = listOf(clip(id = 1, endedAtSecond = 10), clip(id = 2, endedAtSecond = 20))

        val ordered = clips.applySavedOrder(listOf(saved(clipId = 2, position = 0), saved(clipId = 1, position = 1)))

        assertEquals(listOf(2L, 1L), ordered.map { it.id })
    }

    @Test
    fun `순서를 정한 뒤 추가된 클립은 뒤에 붙는다`() {
        val clips = listOf(
            clip(id = 1, endedAtSecond = 10),
            clip(id = 2, endedAtSecond = 20),
            clip(id = 3, endedAtSecond = 30),
        )

        val ordered = clips.applySavedOrder(listOf(saved(clipId = 2, position = 0), saved(clipId = 1, position = 1)))

        assertEquals(listOf(2L, 1L, 3L), ordered.map { it.id })
    }

    @Test
    fun `새 클립이 여러 개면 그들끼리는 촬영 순서다`() {
        val clips = listOf(
            clip(id = 1, endedAtSecond = 10),
            clip(id = 3, endedAtSecond = 30),
            clip(id = 4, endedAtSecond = 20),
        )

        val ordered = clips.applySavedOrder(listOf(saved(clipId = 1, position = 0)))

        assertEquals(listOf(1L, 4L, 3L), ordered.map { it.id })
    }

    @Test
    fun `새 클립이 더 일찍 끝났어도 정해진 순서 뒤에 붙는다`() {
        // 자정을 걸친 클립이나 뒤늦게 스캔된 클립이 이미 정한 순서를 밀고 들어오면 안 된다.
        val clips = listOf(clip(id = 1, endedAtSecond = 20), clip(id = 9, endedAtSecond = 5))

        val ordered = clips.applySavedOrder(listOf(saved(clipId = 1, position = 0)))

        assertEquals(listOf(1L, 9L), ordered.map { it.id })
    }

    @Test
    fun `앱 밖에서 지워진 클립의 순서 행은 목록에 영향을 주지 않는다`() {
        val clips = listOf(clip(id = 1, endedAtSecond = 10))

        val ordered = clips.applySavedOrder(
            listOf(saved(clipId = 99, position = 0), saved(clipId = 1, position = 1)),
        )

        assertEquals(listOf(1L), ordered.map { it.id })
    }

    @Test
    fun `편집 여부는 저장된 행에서 따라온다`() {
        val clips = listOf(clip(id = 1, endedAtSecond = 10), clip(id = 2, endedAtSecond = 20))

        val ordered = clips.applySavedOrder(
            listOf(saved(clipId = 2, position = 0, isEdited = true), saved(clipId = 1, position = 1)),
        )

        assertEquals(listOf(true, false), ordered.map { it.isEdited })
    }

    @Test
    fun `순서 행이 없는 클립은 편집된 적 없는 것으로 본다`() {
        val clips = listOf(clip(id = 1, endedAtSecond = 10), clip(id = 7, endedAtSecond = 30))

        val ordered = clips.applySavedOrder(listOf(saved(clipId = 1, position = 0, isEdited = true)))

        assertEquals(listOf(true, false), ordered.map { it.isEdited })
    }

    private fun clip(id: Long, endedAtSecond: Long) = Clip(
        id = id,
        displayName = "AUTOLOG_$id.mp4",
        durationMs = 1_000,
        startedAt = Instant.ofEpochSecond(endedAtSecond).minusMillis(1_000),
    )

    private fun saved(clipId: Long, position: Int, isEdited: Boolean = false) =
        ClipOrderEntity(clipId = clipId, date = day, position = position, isEdited = isEdited)
}
