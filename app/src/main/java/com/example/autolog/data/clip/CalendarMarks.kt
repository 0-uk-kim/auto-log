package com.example.autolog.data.clip

import android.net.Uri
import java.time.LocalDate

/**
 * 달력이 날짜마다 찍는 두 가지 표시 (planning 3-4).
 *
 * "영상이 있는 날"과 "브이로그를 만든 날"은 사용자에게 뜻이 다르다 — 앞은 아직 할 일이 남은 날,
 * 뒤는 결과물이 나온 날이다. 그래서 하나로 합치지 않는다.
 */
data class CalendarMarks(
    /** 영상이 있는 날마다 그날의 첫 클립 — 브이로그가 시작하는 장면이라 그날의 표지로 쓴다. */
    val covers: Map<LocalDate, Uri> = emptyMap(),
    val datesWithVlog: Set<LocalDate> = emptySet(),
) {
    val datesWithClips: Set<LocalDate> get() = covers.keys
}
