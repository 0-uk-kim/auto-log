package com.example.autolog.data.vlog

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.autolog.camera.CaptureOrientation
import com.example.autolog.data.clip.Clip
import com.example.autolog.data.subtitle.Subtitle
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
 * 클립 방향이 모두 같으면 회전 보정도 레터박스도 필요 없어 Transformer가 대부분 구간을
 * 다시 인코딩하지 않고 그대로 옮겨 담는다. 세로·가로가 섞이면 [mergeFrameFor]가 정한 크기로
 * 모두 다시 그린다 (#40). 자막이 있는 클립도 그 위에 글을 새기느라 다시 그린다 (2차).
 * 출력 코덱은 공유 대상 플랫폼이 공통으로 요구하는 H.264 + AAC로 못 박는다.
 */
@Singleton
class ClipMerger @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * [clips]를 받은 순서 그대로 [outputPath]에 이어붙이고, 진행률을 [onProgress]로 흘린다.
     * [subtitles]는 클립 id → 그 클립의 자막이다.
     *
     * Transformer는 Looper가 있는 스레드에서만 시작할 수 있어 메인에서 띄우고, 실제 작업은
     * 자기 내부 스레드에서 돈다. 호출이 취소되면 진행 중인 내보내기도 같이 접는다.
     */
    suspend fun merge(
        clips: List<Clip>,
        outputPath: String,
        subtitles: Map<Long, List<Subtitle>> = emptyMap(),
        onProgress: (percent: Int) -> Unit = {},
    ): MergeResult {
        require(clips.isNotEmpty()) { "이어붙일 클립이 없다" }

        val frame = withContext(Dispatchers.IO) { mergeFrameFor(clips.map { orientationOf(it) }) }

        return withContext(Dispatchers.Main) {
            val completion = CompletableDeferred<MergeResult>()

            // 효과는 클립마다 따로 건다 — 레터박스는 각 클립을 출력 크기에 맞춰 넣는 일이고,
            // 자막은 그 클립의 시간에만 걸린다. 자막은 레터박스 뒤에 얹어야 출력 프레임 기준으로 자리 잡는다.
            val presentation = frame?.let {
                Presentation.createForWidthAndHeight(it.width, it.height, Presentation.LAYOUT_SCALE_TO_FIT)
            }
            val sequence = EditedMediaItemSequence.Builder(
                clips.map { clip ->
                    val videoEffects = listOfNotNull(
                        presentation,
                        subtitles[clip.id]?.takeIf { it.isNotEmpty() }?.let {
                            OverlayEffect(listOf(SubtitleBitmapOverlay(it)))
                        },
                    )
                    EditedMediaItem.Builder(MediaItem.fromUri(clip.uri))
                        .apply { if (videoEffects.isNotEmpty()) setEffects(Effects(emptyList(), videoEffects)) }
                        .build()
                },
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

    /** 회전 메타데이터까지 반영한, 보이는 그대로의 방향. */
    private fun orientationOf(clip: Clip): CaptureOrientation = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, clip.uri)
        val width = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val rotation = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        val (shownWidth, shownHeight) = if (rotation % 180 == 0) width to height else height to width
        if (shownWidth > shownHeight) CaptureOrientation.Landscape else CaptureOrientation.Portrait
    }

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int =
        extractMetadata(key)?.toIntOrNull() ?: 0

    private companion object {
        /** 진행률 갱신 간격. 더 촘촘히 물어도 화면에서 구분되지 않는다. */
        const val PROGRESS_INTERVAL_MS = 300L
    }
}
