package com.example.autolog.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 한국 달력 관례대로 일요일에서 한 주가 시작한다. */
val FirstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY

val WeekDays: List<DayOfWeek> = (0L..6L).map { FirstDayOfWeek.plus(it) }

/**
 * 한 달을 주 단위 격자로 편다. 앞뒤로 빈 칸(null)을 채워 모든 줄을 7칸으로 맞춘다 —
 * 다른 달의 날짜를 끌어와 채우면 "이 달에 영상이 있는지"를 보는 눈이 흐려진다 (#20).
 */
fun weeksOf(month: YearMonth, firstDayOfWeek: DayOfWeek = FirstDayOfWeek): List<List<LocalDate?>> {
    val lead = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells = buildList<LocalDate?> {
        repeat(lead) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
        while (size % 7 != 0) add(null)
    }
    return cells.chunked(7)
}
