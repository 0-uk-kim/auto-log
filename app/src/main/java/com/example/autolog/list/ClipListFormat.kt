package com.example.autolog.list

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * 클립 길이를 목록 배지에 쓰는 표기로 바꾼다 — `0:07`, `1:05`, 한 시간을 넘기면 `1:02:03`.
 *
 * 1초 미만은 버린다. 갤러리 앱들이 같은 규칙이라 사용자가 따로 읽는 법을 배울 필요가 없다.
 */
fun formatClipDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** 클립이 **끝난** 시각. 날짜를 가르는 기준이 종료 시각이라 목록에도 같은 값을 보여준다 (planning 6). */
fun formatClipTime(
    instant: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    .withLocale(locale)
    .withZone(zone)
    .format(instant)
