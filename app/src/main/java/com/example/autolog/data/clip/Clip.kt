package com.example.autolog.data.clip

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.time.Instant

/**
 * 앱이 촬영한 클립 하나. MediaStore 행에서 그대로 읽어낸 값만 담는다.
 *
 * 날짜 묶기·정렬 키는 둘 다 [endedAt]이다 — 자정을 걸친 클립은 종료 시각이 속한 날짜에 들어간다
 * (planning 6 "날짜 기준").
 */
data class Clip(
    val id: Long,
    val displayName: String,
    val durationMs: Long,
    val startedAt: Instant,
) {
    val endedAt: Instant get() = startedAt.plusMillis(durationMs)

    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
}
