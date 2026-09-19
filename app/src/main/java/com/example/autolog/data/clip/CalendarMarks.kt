package com.example.autolog.data.clip

import java.time.LocalDate

/**
 * 달력이 날짜마다 찍는 두 가지 표시 (planning 3-4).
 *
 * "영상이 있는 날"과 "브이로그를 만든 날"은 사용자에게 뜻이 다르다 — 앞은 아직 할 일이 남은 날,
 * 뒤는 결과물이 나온 날이다. 그래서 하나로 합치지 않는다.
 */
data class CalendarMarks(
    val datesWithClips: Set<LocalDate> = emptySet(),
    val datesWithVlog: Set<LocalDate> = emptySet(),
)
