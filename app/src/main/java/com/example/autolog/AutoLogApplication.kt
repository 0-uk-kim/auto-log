package com.example.autolog

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.autolog.diagnostics.AnrTraceStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 병합 작업이 Repository를 주입받아야 해서 WorkManager 초기화를 앱이 직접 맡는다 —
 * 기본 초기화는 Hilt가 만든 Worker를 만들 줄 모른다 (#26).
 */
@HiltAndroidApp
class AutoLogApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var anrTraceStore: AnrTraceStore

    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // 시스템이 지우기 전에 옮겨 둔다. 제보는 한참 뒤에 올 수 있다 (#88).
        appScope.launch { anrTraceStore.collect() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
