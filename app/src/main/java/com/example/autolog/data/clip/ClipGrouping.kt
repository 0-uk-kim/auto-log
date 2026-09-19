package com.example.autolog.data.clip

import java.time.LocalDate
import java.time.ZoneId

/**
 * 클립을 촬영 날짜로 묶는다. 날짜 하나가 곧 브이로그 하나라서, 이 묶음이 모든 화면의 단위가 된다.
 *
 * 기준은 **촬영 종료 시각**이다 — 23:59에 시작해 자정을 넘긴 클립은 끝난 날짜에 들어간다 (planning 6 "날짜 기준").
 * 묶음 안은 종료 시각 오름차순이고, 바깥은 최근 날짜가 먼저다.
 */
fun List<Clip>.groupByRecordedDate(zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Clip>> =
    sortedBy { it.endedAt }
        .groupBy { it.recordedDate(zone) }
        .toSortedMap(reverseOrder())

fun List<Clip>.clipsOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Clip> =
    filter { it.recordedDate(zone) == date }.sortedBy { it.endedAt }

fun Clip.recordedDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    endedAt.atZone(zone).toLocalDate()
