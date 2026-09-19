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
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.autolog.data.clip.Clip
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
     * [clips]를 받은 순서 그대로 [outputPath]에 이어붙이고, 진행률을 [onProgress]로 흘린다.
     *
     * Transformer는 Looper가 있는 스레드에서만 시작할 수 있어 메인에서 띄우고, 실제 작업은
     * 자기 내부 스레드에서 돈다. 호출이 취소되면 진행 중인 내보내기도 같이 접는다.
     */
    suspend fun merge(
        clips: List<Clip>,
        outputPath: String,
        onProgress: (percent: Int) -> Unit = {},
    ): MergeResult {
        require(clips.isNotEmpty()) { "이어붙일 클립이 없다" }

        return withContext(Dispatchers.Main) {
            val completion = CompletableDeferred<MergeResult>()

            val sequence = EditedMediaItemSequence.Builder(
                clips.map { EditedMediaItem.Builder(MediaItem.fromUri(it.uri)).build() },
            ).build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            completion.complete(
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
                            completion.completeExceptionally(exception)
                        }
                    },
                )
                .build()

            transformer.start(Composition.Builder(sequence).build(), outputPath)

            // Transformer는 진행률을 밀어 주지 않는다 — 물어봐야 한다.
            val poller = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress)
                    }
                    delay(PROGRESS_INTERVAL_MS)
                }
            }

            try {
                completion.await()
            } finally {
                poller.cancel()
                // 취소로 빠져나갈 때는 내보내기가 아직 돌고 있다. 같이 접지 않으면 파일만 남는다.
                if (!completion.isCompleted) transformer.cancel()
            }
        }
    }

    private companion object {
        /** 진행률 갱신 간격. 더 촘촘히 물어도 화면에서 구분되지 않는다. */
        const val PROGRESS_INTERVAL_MS = 300L
    }
}
