package com.example.autolog.calendar

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarMonthTest {

    @Test
    fun `모든 줄이 일곱 칸이다`() {
        val weeks = weeksOf(YearMonth.of(2026, 9))

        assertEquals(listOf(7, 7, 7, 7, 7), weeks.map { it.size })
    }

    @Test
    fun `1일 앞은 요일만큼 비운다`() {
        // 2026-09-01은 화요일 — 일·월 두 칸이 비어야 한다.
        val first = weeksOf(YearMonth.of(2026, 9)).first()

        assertNull(first[0])
        assertNull(first[1])
        assertEquals(LocalDate.of(2026, 9, 1), first[2])
    }

    @Test
    fun `일요일에 시작하는 달은 앞이 비지 않는다`() {
        // 2026-11-01은 일요일.
        assertEquals(LocalDate.of(2026, 11, 1), weeksOf(YearMonth.of(2026, 11)).first()[0])
    }

    @Test
    fun `날짜는 하루도 빠지지 않는다`() {
        val month = YearMonth.of(2026, 2)

        val days = weeksOf(month).flatten().filterNotNull()

        assertEquals(month.lengthOfMonth(), days.size)
        assertEquals(month.atDay(1), days.first())
        assertEquals(month.atEndOfMonth(), days.last())
    }
}
