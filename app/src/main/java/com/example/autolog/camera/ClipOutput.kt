package com.example.autolog.camera

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import androidx.camera.video.MediaStoreOutputOptions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 촬영본은 공용 갤러리(MediaStore)에 저장한다 (planning 6).
 * 앱 촬영분만 골라내는 기준이 [RELATIVE_PATH]라서 P2의 목록 조회도 이 경로에 의존한다.
 */
object ClipOutput {

    const val RELATIVE_PATH = "Movies/AutoLog"

    private val NAME_FORMAT = DateTimeFormatter.ofPattern("'AUTOLOG'_yyyyMMdd_HHmmss")

    fun mediaStoreOptions(
        contentResolver: ContentResolver,
        now: LocalDateTime = LocalDateTime.now(),
    ): MediaStoreOutputOptions {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, now.format(NAME_FORMAT))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
        }
        return MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()
    }
}
