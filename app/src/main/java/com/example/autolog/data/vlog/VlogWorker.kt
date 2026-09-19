package com.example.autolog.data.vlog

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.autolog.data.clip.ClipRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.time.LocalDate

/**
 * 하루치 클립을 한 편으로 이어붙이는 백그라운드 작업 (planning 6 "클립 길이 제한").
 *
 * 하루치가 길어지면 병합도 길어지므로 화면에 묶어 두지 않는다 — 목록을 벗어나거나 앱을 내려도
 * 계속 돌고, 화면은 진행률만 구독한다.
 *
 * 결과물을 MediaStore에 올리고 Room에 기록하는 것은 #27이 한다. 지금은 캐시 파일까지다.
 */
@HiltWorker
class VlogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val clipRepository: ClipRepository,
    private val clipMerger: ClipMerger,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val date = inputData.getString(KEY_DATE)?.let(LocalDate::parse)
            ?: return Result.failure()

        val clips = clipRepository.clipsOn(date)
        if (clips.isEmpty()) return Result.failure()

        val output = File(applicationContext.cacheDir, "vlog-$date.mp4")

        return runCatching {
            clipMerger.merge(clips, output.absolutePath) { percent ->
                setProgressAsync(workDataOf(KEY_PROGRESS to percent))
            }
        }.fold(
            onSuccess = { merged ->
                Result.success(
                    workDataOf(
                        KEY_OUTPUT_PATH to output.absolutePath,
                        KEY_DURATION_MS to merged.durationMs,
                    ),
                )
            },
            onFailure = {
                // 반쯤 쓰인 파일이 남으면 다음 시도가 이어쓰기로 깨진다.
                output.delete()
                Result.failure()
            },
        )
    }

    companion object {
        const val KEY_DATE = "date"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_PATH = "outputPath"
        const val KEY_DURATION_MS = "durationMs"

        /** 날짜당 브이로그는 항상 1개라(planning 6) 같은 날짜 작업은 하나만 돈다. */
        fun workName(date: LocalDate) = "vlog-$date"

        fun enqueue(workManager: WorkManager, date: LocalDate) {
            workManager.enqueueUniqueWork(
                workName(date),
                // 이미 돌고 있으면 그대로 둔다 — 두 번 눌렀다고 처음부터 다시 할 이유가 없다.
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<VlogWorker>()
                    .setInputData(Data.Builder().putString(KEY_DATE, date.toString()).build())
                    .build(),
            )
        }
    }
}
