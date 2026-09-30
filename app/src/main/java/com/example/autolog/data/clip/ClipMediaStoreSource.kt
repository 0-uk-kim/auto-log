package com.example.autolog.data.clip

import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.provider.MediaStore
import com.example.autolog.camera.ClipOutput
import com.example.autolog.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * MediaStore에서 앱 촬영분만 읽어온다.
 *
 * 갤러리 전체가 아니라 [ClipOutput.RELATIVE_PATH] 아래 있는 것만 앱 촬영분으로 본다.
 * 앱 밖에서 지워지거나 옮겨질 수 있는 저장소라, 매번 조회해 현재 상태를 그대로 반영한다 (planning 6 "영상 저장 위치").
 */
@Singleton
class ClipMediaStoreSource @Inject constructor(
    private val contentResolver: ContentResolver,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun loadClips(): List<Clip> = withContext(ioDispatcher) {
        val clips = mutableListOf<Clip>()
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            PROJECTION,
            SELECTION,
            arrayOf(RELATIVE_PATH_VALUE),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val ownerColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.OWNER_PACKAGE_NAME)

            while (cursor.moveToNext()) {
                // DATE_TAKEN은 메타데이터의 촬영 시각(ms)이고, 없으면 행이 생긴 시각(s)으로 대신한다.
                // CameraX는 녹화를 시작할 때 행을 만들므로 둘 다 '시작' 시각이다 — 종료 시각은 Clip이 duration으로 더한다.
                val startedAtMillis = cursor.getLong(takenColumn)
                    .takeIf { it > 0L }
                    ?: (cursor.getLong(addedColumn) * 1_000L)
                clips += Clip(
                    id = cursor.getLong(idColumn),
                    displayName = cursor.getString(nameColumn),
                    durationMs = cursor.getLong(durationColumn),
                    startedAt = Instant.ofEpochMilli(startedAtMillis),
                    sizeBytes = cursor.getLong(sizeColumn),
                    isOwned = cursor.getString(ownerColumn) == context.packageName,
                )
            }
        }
        // 정렬 키가 파생값(종료 시각)이라 SQL ORDER BY로는 못 걸고 읽은 뒤에 세운다.
        clips.sortedBy { it.endedAt }
    }

    /** 이 설치에서 찍은 클립은 확인 창 없이 지울 수 있다. 소유권이 없는 것은 [deleteRequest]로 보낸다. */
    suspend fun delete(clips: List<Clip>) = withContext(ioDispatcher) {
        clips.forEach { clip ->
            // 그사이 앱 밖에서 먼저 지워졌을 수 있다. 하나가 실패해도 나머지는 지운다.
            runCatching { contentResolver.delete(clip.uri, null, null) }
        }
    }

    /** 재설치로 소유권이 풀린 클립은 시스템 확인 창을 거쳐야 지울 수 있다 — 창 한 번에 여러 개를 함께 지운다. */
    fun deleteRequest(clips: List<Clip>): IntentSender =
        MediaStore.createDeleteRequest(contentResolver, clips.map { it.uri }).intentSender

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.OWNER_PACKAGE_NAME,
        )

        // 녹화가 끝나기 전(IS_PENDING=1) 행은 아직 재생할 수 없으므로 목록에서 뺀다.
        const val SELECTION =
            "${MediaStore.Video.Media.RELATIVE_PATH}=? AND ${MediaStore.Video.Media.IS_PENDING}=0"

        // MediaStore는 상대 경로를 끝에 '/'를 붙여 저장한다.
        const val RELATIVE_PATH_VALUE = "${ClipOutput.RELATIVE_PATH}/"
    }
}
