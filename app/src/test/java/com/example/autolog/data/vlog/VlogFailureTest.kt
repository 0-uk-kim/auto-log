package com.example.autolog.data.vlog

import com.example.autolog.data.clip.Clip
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlogFailureTest {

    @Test
    fun `필요 용량은 원본 합계보다 크다`() {
        val clips = listOf(clip(sizeBytes = 10_000_000), clip(sizeBytes = 20_000_000))

        val required = requiredBytesFor(clips)

        assertTrue("합계 30MB보다 커야 한다 (실제 $required)", required > 30_000_000)
    }

    @Test
    fun `클립이 없으면 필요 용량도 없다`() {
        assertEquals(0L, requiredBytesFor(emptyList()))
    }

    @Test
    fun `모르는 실패는 일반 실패로 본다`() {
        assertEquals(VlogFailure.MergeFailed, VlogFailure.from(null))
        assertEquals(VlogFailure.MergeFailed, VlogFailure.from("무엇인가"))
    }

    @Test
    fun `알려진 실패는 그대로 돌아온다`() {
        assertEquals(VlogFailure.NotEnoughStorage, VlogFailure.from("NotEnoughStorage"))
        assertEquals(VlogFailure.NoClips, VlogFailure.from("NoClips"))
    }

    private fun clip(sizeBytes: Long) = Clip(
        id = sizeBytes,
        displayName = "AUTOLOG.mp4",
        durationMs = 1_000,
        startedAt = Instant.EPOCH,
        sizeBytes = sizeBytes,
    )
}
