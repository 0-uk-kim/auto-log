package com.example.autolog.data.clip

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.time.Instant

/**
 * 앱이 촬영한 클립 하나. 값은 MediaStore 행에서 읽어내고, [isEdited]만 Room이 기억한다.
 *
 * 날짜 묶기·정렬 키는 둘 다 [endedAt]이다 — 자정을 걸친 클립은 종료 시각이 속한 날짜에 들어간다
 * (planning 6 "날짜 기준").
 */
data class Clip(
    val id: Long,
    val displayName: String,
    val durationMs: Long,
    val startedAt: Instant,
    /**
     * 편집 기능이 들어오는 2·3차에 켜진다. 1차에는 항상 false지만 필드와 표시를 미리 연결해 둬서
     * 나중 마이그레이션 비용을 줄인다 (planning 5 "1차 개발 시 유의", #18).
     */
    val isEdited: Boolean = false,
) {
    val endedAt: Instant get() = startedAt.plusMillis(durationMs)

    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
}
