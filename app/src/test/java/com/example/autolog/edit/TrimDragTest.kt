package com.example.autolog.edit

import com.example.autolog.data.clip.ClipSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class TrimDragTest {

    private val segment = ClipSegment(4_000, 10_000)

    @Test
    fun 손잡이_근처를_끌면_그_손잡이다() {
        assertEquals(TrimDrag.Start, trimDragFor(segment, 4_300, reachMs = 500))
        assertEquals(TrimDrag.End, trimDragFor(segment, 9_600, reachMs = 500))
    }

    @Test
    fun 손잡이에서_먼_곳을_끌면_재생_위치를_찾는다() {
        assertEquals(TrimDrag.Scrub, trimDragFor(segment, 7_000, reachMs = 500))
        assertEquals(TrimDrag.Scrub, trimDragFor(segment, 1_000, reachMs = 500))
    }

    @Test
    fun 짧은_조각에서_두_손잡이가_다_닿으면_가까운_쪽이다() {
        val short = ClipSegment(4_000, 4_600)

        assertEquals(TrimDrag.Start, trimDragFor(short, 4_200, reachMs = 500))
        assertEquals(TrimDrag.End, trimDragFor(short, 4_400, reachMs = 500))
    }

    @Test
    fun 선택한_조각이_없으면_찾기다() {
        assertEquals(TrimDrag.Scrub, trimDragFor(null, 4_000, reachMs = 500))
    }
}
