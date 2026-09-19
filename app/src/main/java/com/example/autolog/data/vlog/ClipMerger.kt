package com.example.autolog.data.vlog

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.autolog.data.clip.Clip
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** 병합이 끝나고 남는 것. 결과물을 MediaStore에 올리는 것은 #27이 한다. */
data class MergeResult(val durationMs: Long, val fileSizeBytes: Long)

/**
 * 하루치 클립을 목록 순서 그대로 한 편으로 이어붙인다 (planning 3-5).
 *
 * 1차는 모든 클립이 같은 규격·같은 방향이라(planning 6-1) 회전 보정도 레터박스도 필요 없고,
 * Transformer가 대부분 구간을 다시 인코딩하지 않고 그대로 옮겨 담는다.
 * 출력 코덱은 공유 대상 플랫폼이 공통으로 요구하는 H.264 + AAC로 못 박는다.
 */
@Singleton
class ClipMerger @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * [clips]를 받은 순서 그대로 [outputPath]에 이어붙인다.
     *
     * Transformer는 Looper가 있는 스레드에서만 시작할 수 있어 메인에서 띄우고, 실제 작업은
     * 자기 내부 스레드에서 돈다. 호출이 취소되면 진행 중인 내보내기도 같이 접는다.
     */
    suspend fun merge(clips: List<Clip>, outputPath: String): MergeResult {
        require(clips.isNotEmpty()) { "이어붙일 클립이 없다" }

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val sequence = EditedMediaItemSequence.Builder(
                    clips.map { EditedMediaItem.Builder(MediaItem.fromUri(it.uri)).build() },
                ).build()

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) {
                                continuation.resume(
                                    MergeResult(
                                        durationMs = result.durationMs,
                                        fileSizeBytes = result.fileSizeBytes,
                                    ),
                                )
                            }

                            override fun onError(
                                composition: Composition,
                                result: ExportResult,
                                exception: ExportException,
                            ) {
                                continuation.resumeWithException(exception)
                            }
                        },
                    )
                    .build()

                transformer.start(Composition.Builder(sequence).build(), outputPath)

                // 취소는 아무 스레드에서나 올 수 있는데 Transformer는 자기를 띄운 스레드만 받는다.
                continuation.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { transformer.cancel() }
                }
            }
        }
    }
}
