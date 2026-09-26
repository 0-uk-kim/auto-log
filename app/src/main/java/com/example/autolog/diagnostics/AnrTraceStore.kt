package com.example.autolog.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.example.autolog.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 테스터 기기에서 난 ANR의 트레이스를 모아 두었다가 한 파일로 내보낸다 (#88).
 *
 * Play로 배포하지 않아 Android vitals가 없다. 시스템도 트레이스를 남기지만 개수·기간 제한으로
 * 지워지므로, 앱이 켜질 때마다 새로 생긴 것을 [dir]로 옮겨 둔다.
 */
@Singleton
class AnrTraceStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val dir = File(context.filesDir, "anr")
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 앱 시작 때의 수집과 내보내기 직전의 수집이 겹치면 같은 기록을 두 번 옮긴다.
    private val mutex = Mutex()

    /** 시스템에 남은 ANR 중 아직 옮기지 않은 것을 옮기고 오래된 것을 지운다. */
    suspend fun collect() = withContext(ioDispatcher) {
        mutex.withLock {
            val lastSaved = prefs.getLong(KEY_LAST_SAVED, 0L)
            // 시스템은 매번 같은 기록을 돌려준다. 마지막으로 옮긴 시각 뒤의 것만 새것이다.
            val fresh = context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(null, 0, 0)
                .filter { it.reason == ApplicationExitInfo.REASON_ANR && it.timestamp > lastSaved }
            if (fresh.isEmpty()) return@withLock

            dir.mkdirs()
            fresh.forEach(::save)
            prefs.edit().putLong(KEY_LAST_SAVED, fresh.maxOf { it.timestamp }).apply()
            oldTraces(dir.list().orEmpty().toList(), KEEP).forEach { File(dir, it).delete() }
        }
    }

    /** 모아 둔 트레이스와 기기 정보를 zip 하나로 묶는다. 모인 것이 없으면 null이다. */
    suspend fun export(): File? {
        collect()
        return withContext(ioDispatcher) {
            val traces = dir.listFiles().orEmpty().sortedByDescending { it.name }
            if (traces.isEmpty()) return@withContext null

            val out = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }.resolve(EXPORT_NAME)
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("device.txt"))
                zip.write(deviceInfo().toByteArray())
                zip.closeEntry()
                traces.forEach { trace ->
                    zip.putNextEntry(ZipEntry("anr/${trace.name}"))
                    trace.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            out
        }
    }

    private fun save(info: ApplicationExitInfo) {
        File(dir, "${info.timestamp}.txt").outputStream().bufferedWriter().use { out ->
            out.appendLine("발생 시각: ${Instant.ofEpochMilli(info.timestamp)}")
            out.appendLine("사유: ${info.description}")
            out.appendLine("중요도: ${info.importance}")
            out.appendLine()
            // 트레이스 없이 기록만 남는 경우도 있다. 그래도 언제 났는지는 남긴다.
            info.traceInputStream?.bufferedReader()?.use { it.copyTo(out) }
                ?: out.appendLine("(트레이스 없음)")
        }
    }

    private fun deviceInfo(): String {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        return """
            기기: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            앱: ${pkg.versionName} (${pkg.longVersionCode})
        """.trimIndent() + "\n"
    }

    companion object {
        const val EXPORT_DIR = "diagnostics"
        private const val EXPORT_NAME = "autolog-anr.zip"
        private const val PREFS_NAME = "diagnostics"
        private const val KEY_LAST_SAVED = "anr_last_saved"

        /** 트레이스 한 건이 수백 KB~수 MB다. 원인을 좁히는 데는 최근 몇 건이면 된다. */
        private const val KEEP = 5
    }
}

/** 파일 이름이 발생 시각(ms)이다. 최근 [keep]건을 뺀 나머지를 돌려준다. */
internal fun oldTraces(fileNames: List<String>, keep: Int): List<String> =
    fileNames.sortedByDescending { it.substringBefore('.').toLongOrNull() ?: 0L }.drop(keep)
