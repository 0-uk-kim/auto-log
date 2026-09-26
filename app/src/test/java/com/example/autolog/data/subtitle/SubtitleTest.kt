package com.example.autolog.data.subtitle

import org.junit.Assert.assertFalse
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
}
