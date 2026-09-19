package com.example.autolog

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 병합 작업이 Repository를 주입받아야 해서 WorkManager 초기화를 앱이 직접 맡는다 —
 * 기본 초기화는 Hilt가 만든 Worker를 만들 줄 모른다 (#26).
 */
@HiltAndroidApp
class AutoLogApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
