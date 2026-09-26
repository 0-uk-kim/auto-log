package com.example.autolog.edit

import com.example.autolog.edit.SubtitleDraft.Companion.DEFAULT_LENGTH_MS
import com.example.autolog.edit.SubtitleDraft.Companion.MIN_LENGTH_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleDraftTest {

    private val duration = 10_000L

    @Test
    fun 새_자막은_지금_위치부터_기본_길이다() {
        val draft = SubtitleDraft.startingAt(3_000, duration)

        assertEquals(3_000, draft.startMs)
        assertEquals(3_000 + DEFAULT_LENGTH_MS, draft.endMs)
    }

    @Test
    fun 끝_가까이에서_시작하면_클립_끝에서_자른다() {
        val draft = SubtitleDraft.startingAt(9_000, duration)

        assertEquals(9_000, draft.startMs)
        assertEquals(duration, draft.endMs)
    }

    @Test
    fun 시작을_끝_뒤로_찍으면_끝이_밀려난다() {
        val draft = SubtitleDraft(startMs = 1_000, endMs = 3_000).withStart(5_000, duration)

        assertEquals(5_000, draft.startMs)
        assertEquals(5_000 + DEFAULT_LENGTH_MS, draft.endMs)
    }

    @Test
    fun 끝을_시작_앞으로_찍으면_시작이_당겨진다() {
        val draft = SubtitleDraft(startMs = 5_000, endMs = 7_000).withEnd(3_000, duration)

        assertEquals(3_000 - DEFAULT_LENGTH_MS, draft.startMs)
        assertEquals(3_000, draft.endMs)
    }

    @Test
    fun 구간이_뒤집히지_않으면_반대편은_그대로다() {
        val draft = SubtitleDraft(startMs = 1_000, endMs = 6_000)

        assertEquals(6_000, draft.withStart(2_000, duration).endMs)
        assertEquals(1_000, draft.withEnd(4_000, duration).startMs)
    }

    @Test
    fun 클립_맨_끝에서_시작을_찍어도_최소_길이는_남는다() {
        val draft = SubtitleDraft(startMs = 0, endMs = 2_000).withStart(duration, duration)

        assertEquals(duration - MIN_LENGTH_MS, draft.startMs)
        assertEquals(duration, draft.endMs)
    }

    @Test
    fun 빈_텍스트는_저장할_수_없다() {
        assertFalse(SubtitleDraft(startMs = 0, endMs = 2_000, text = "  ").canSave)
        assertTrue(SubtitleDraft(startMs = 0, endMs = 2_000, text = "안녕").canSave)
    }

    @Test
    fun 저장할_때_앞뒤_공백을_걷어낸다() {
        assertEquals("안녕", SubtitleDraft(startMs = 0, endMs = 2_000, text = " 안녕 ").toSubtitle(1).text)
    }

    @Test
    fun 자막_시각은_0점1초까지_보인다() {
        assertEquals("0:02.4", formatSubtitleTime(2_468))
        assertEquals("1:05.0", formatSubtitleTime(65_000))
    }
}
