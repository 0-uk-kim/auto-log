package com.example.autolog.list

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipListFormatTest {

    @Test
    fun `1초 미만은 0초로 보인다`() {
        assertEquals("0:00", formatClipDuration(0))
        assertEquals("0:00", formatClipDuration(999))
    }

    @Test
    fun `초는 두 자리로 채운다`() {
        assertEquals("0:07", formatClipDuration(7_400))
        assertEquals("1:05", formatClipDuration(65_000))
    }

    @Test
    fun `한 시간을 넘기면 시간 자리가 붙는다`() {
        assertEquals("1:02:03", formatClipDuration(3_723_000))
    }

    @Test
    fun `종료 시각은 기기 표준시로 보여준다`() {
        val ended = Instant.parse("2026-09-19T12:34:56Z")
        assertEquals(
            "오후 9:34",
            formatClipTime(ended, ZoneId.of("Asia/Seoul"), Locale.KOREAN),
        )
    }
}
