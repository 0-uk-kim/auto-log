package com.example.autolog.camera

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.container.Mp4TimestampData
import androidx.media3.effect.FrameDropEffect
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultMuxer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.autolog.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 타임랩스 촬영본을 만든다 (#65).
 *
 * CameraX는 촬영 간격을 벌려 찍는 기능이 없다. 평소처럼 앱 캐시에 찍어 두고, 끝나면 Transformer로
 * 배속을 올려 다시 담은 뒤 갤러리의 촬영본 폴더로 옮긴다. 다른 클립과 같은 폴더·이름 규칙이라
 * 목록과 브이로그 병합에 그대로 섞인다.
 */
@Singleton
class TimelapseEncoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val workDir = File(context.cacheDir, "timelapse")

    /** 원본을 찍어 둘 자리. 앞선 촬영이 중간에 끊겨 남긴 파일이 있으면 이때 치운다. */
    fun newRawFile(): File {
        workDir.deleteRecursively()
        workDir.mkdirs()
        return File(workDir, "raw.mp4")
    }

    /**
     * [raw]를 [speed]배로 줄여 갤러리에 넣고 그 URI를 돌려준다. 성공하든 실패하든 [raw]는 지운다.
     *
     * 목록은 촬영 시각 + 길이를 '촬영 완료' 시각으로 쓴다. 완성본은 실제로 찍은 시간보다 훨씬 짧으므로
     * 촬영 시각을 [endedAt]에서 완성본 길이만큼 당겨 파일에 새긴다 — 그래야 목록 순서와 완료 시각이 맞는다.
     * MediaStore는 이 값을 파일에서 읽어 오므로 행에 따로 적으면 공개하는 순간 덮어써진다.
     */
    suspend fun encode(
        raw: File,
        speed: TimelapseSpeed,
        startedAt: LocalDateTime,
        endedAt: Instant,
        recorded: Duration,
        onProgress: (percent: Int) -> Unit = {},
    ): Uri {
        val output = File(workDir, "timelapse.mp4")
        try {
            val takenAt = endedAt.minusMillis(speed.outputOf(recorded).inWholeMilliseconds)
            export(raw, output, speed, takenAt, onProgress)
            return withContext(ioDispatcher) { save(output, startedAt) }
        } finally {
            raw.delete()
            output.delete()
        }
    }

    private suspend fun export(
        raw: File,
        output: File,
        speed: TimelapseSpeed,
        takenAt: Instant,
        onProgress: (percent: Int) -> Unit,
    ): ExportResult = withContext(Dispatchers.Main) {
        val completion = CompletableDeferred<ExportResult>()

        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(raw)))
            .setSpeed(ConstantSpeed(speed.factor.toFloat()))
            // 배속만 올리면 초당 프레임이 배수만큼 늘어난다. 일반 촬영본과 같은 30fps로 솎아 낸다.
            .setEffects(Effects(emptyList(), listOf(FrameDropEffect.createDefaultFrameDropEffect(FRAME_RATE))))
            .build()
        val sequence = EditedMediaItemSequence.Builder(item)
            // 소리는 담지 않되 무음 트랙은 둔다 — 소리 있는 클립과 이어붙일 때 트랙 구성이 같아야 한다.
            .experimentalSetForceAudioTrack(true)
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setMuxerFactory(TakenAtMuxerFactory(DefaultMuxer.Factory(), takenAt))
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        completion.complete(result)
                    }

                    override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                        completion.completeExceptionally(exception)
                    }
                },
            )
            .build()

        transformer.start(Composition.Builder(sequence).build(), output.path)

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
            if (!completion.isCompleted) transformer.cancel()
        }
    }

    private fun save(file: File, startedAt: LocalDateTime): Uri {
        val values = ClipOutput.contentValues(startedAt).apply {
            // 다 옮기기 전에는 목록(#11)이 집어 가지 않게 막아 둔다.
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("갤러리에 타임랩스를 만들 수 없다")
        runCatching {
            contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("타임랩스를 쓸 수 없다")
        }.onFailure {
            contentResolver.delete(uri, null, null)
            throw it
        }
        contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    private companion object {
        const val FRAME_RATE = 30f
        const val PROGRESS_INTERVAL_MS = 300L
    }
}

/** 원본에서 넘어오는 촬영 시각을 [takenAt]으로 바꿔 새긴다. 원본에 없어도 새긴다. */
private class TakenAtMuxerFactory(
    private val delegate: Muxer.Factory,
    private val takenAt: Instant,
) : Muxer.Factory by delegate {

    override fun supportsWritingNegativeTimestampsInEditList(): Boolean =
        delegate.supportsWritingNegativeTimestampsInEditList()

    override fun create(path: String): Muxer {
        val muxer = delegate.create(path)
        val seconds = Mp4TimestampData.unixTimeToMp4TimeSeconds(takenAt.toEpochMilli())
        muxer.addMetadataEntry(Mp4TimestampData(seconds, seconds))
        return object : Muxer by muxer {
            override fun addMetadataEntry(entry: Metadata.Entry) {
                if (entry !is Mp4TimestampData) muxer.addMetadataEntry(entry)
            }
        }
    }
}

/** 처음부터 끝까지 같은 배속. */
private class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}
