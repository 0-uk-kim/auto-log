package com.example.autolog.data.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTest {

    private val subtitle = Subtitle(clipId = 1, startMs = 1_000, endMs = 3_000, text = "안녕")

    @Test
    fun 구간_안에서만_보인다() {
        assertFalse(subtitle.isShownAt(999))
        assertTrue(subtitle.isShownAt(1_000))
        assertTrue(subtitle.isShownAt(2_999))
    }

    @Test
    fun 끝_시각에는_이미_사라졌다() {
        assertFalse(subtitle.isShownAt(3_000))
    }

    @Test
    fun 그_위치에_걸친_자막만_보인다() {
        val subtitles = listOf(subtitle, Subtitle(clipId = 1, startMs = 5_000, endMs = 6_000, text = "잘 가"))

        assertEquals("안녕", subtitles.textAt(1_500))
        assertEquals("잘 가", subtitles.textAt(5_500))
        assertNull(subtitles.textAt(4_000))
    }

    @Test
    fun 겹친_자막은_시작_순으로_줄을_바꿔_보인다() {
        val later = Subtitle(clipId = 1, startMs = 2_000, endMs = 4_000, text = "둘째")

        assertEquals("안녕\n둘째", listOf(later, subtitle).textAt(2_500))
    }
}
