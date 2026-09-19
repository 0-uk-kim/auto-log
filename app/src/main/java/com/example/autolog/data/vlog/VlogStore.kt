package com.example.autolog.data.vlog

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import com.example.autolog.camera.ClipOutput
import com.example.autolog.data.db.VlogDao
import com.example.autolog.data.db.VlogEntity
import com.example.autolog.di.IoDispatcher
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 완성된 브이로그를 갤러리에 올리고 그 사실을 기록한다 (planning 3-5, 3-6).
 *
 * 저장 위치는 촬영본 **아래 폴더**다 — 같은 폴더에 두면 목록 스캔(#11)이 결과물까지 클립으로
 * 집어 온다. 스캔은 경로가 정확히 `Movies/AutoLog/`인 행만 보므로 한 단계만 내려가면 갈린다.
 */
/** 이미 만들어 둔 브이로그 한 편. */
data class SavedVlog(val uri: Uri, val durationMs: Long, val createdAt: Instant)

@Singleton
class VlogStore @Inject constructor(
    private val contentResolver: ContentResolver,
    private val vlogDao: VlogDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * [merged] 파일을 갤러리로 옮기고 Room에 그날 것으로 남긴다.
     *
     * 날짜가 기본 키라 같은 날에 다시 만들면 기록이 덮어써진다 (planning 6 "브이로그 재생성").
     * 그때 **이전 결과물도 같이 지운다** — 안 지우면 갤러리에 옛 브이로그가 그대로 남는다.
     */
    suspend fun save(date: LocalDate, merged: File): Uri = withContext(ioDispatcher) {
        val previous = vlogDao.byDate(date)

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, date.format(NAME_FORMAT))
            put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
            // 다 옮기기 전에는 갤러리에 보이지 않게 막아 둔다.
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("갤러리에 브이로그를 만들 수 없다")

        runCatching {
            contentResolver.openOutputStream(uri)?.use { out -> merged.inputStream().use { it.copyTo(out) } }
                ?: error("브이로그를 쓸 수 없다")
        }.onFailure {
            contentResolver.delete(uri, null, null)
            throw it
        }

        contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
            null,
            null,
        )

        vlogDao.upsert(
            VlogEntity(date = date, mediaId = ContentUris.parseId(uri), createdAt = Instant.now()),
        )

        previous?.let {
            deleteMedia(it.mediaId)
            // 옛 결과물이 아직 있는 동안 넣었으므로 MediaStore가 이름 뒤에 "(1)"을 붙여 두었다.
            // 자리를 비운 지금 제 이름으로 되돌린다 — 안 하면 다시 만들 때마다 숫자가 늘어난다.
            renameTo(uri, date)
        }
        merged.delete()

        uri
    }

    private fun renameTo(uri: Uri, date: LocalDate) {
        runCatching {
            contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, date.format(NAME_FORMAT))
                },
                null,
                null,
            )
        }
    }

    /**
     * 그날 이미 만들어 둔 결과물. 길이는 Room이 아니라 MediaStore에 있으므로 거기서 읽는다 —
     * 같은 값을 두 곳에 두면 앱 밖에서 지워졌을 때 어긋난다.
     */
    suspend fun saved(date: LocalDate): SavedVlog? = withContext(ioDispatcher) {
        val entity = vlogDao.byDate(date) ?: return@withContext null
        val uri = ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            entity.mediaId,
        )
        val duration = contentResolver.query(
            uri,
            arrayOf(MediaStore.Video.Media.DURATION),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

        // 앱 밖에서 지워졌으면 기록만 남은 것이다 — 없는 결과물을 있다고 하지 않는다.
        duration?.let { SavedVlog(uri = uri, durationMs = it, createdAt = entity.createdAt) }
    }

    private fun deleteMedia(mediaId: Long) {
        runCatching {
            contentResolver.delete(
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId),
                null,
                null,
            )
        }
    }

    private companion object {
        const val RELATIVE_PATH = "${ClipOutput.RELATIVE_PATH}/Vlogs"
        const val MIME_TYPE = "video/mp4"
        val NAME_FORMAT = DateTimeFormatter.ofPattern("'AUTOLOG_VLOG'_yyyyMMdd")
    }
}
