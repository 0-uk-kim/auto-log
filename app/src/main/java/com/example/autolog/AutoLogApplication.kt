package com.example.autolog

import android.app.Application
import androidx.camera.lifecycle.ProcessCameraProvider
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
        // 카메라 서비스 연결과 기기 특성 조회는 수백 ms 걸린다. 앱이 뜨는 동안 미리 해 두면
        // 카메라 화면이 처음 바인딩할 때 곧바로 프리뷰가 뜬다 (#100). 결과는 싱글턴이라 화면이 그대로 받아 쓴다.
        ProcessCameraProvider.getInstance(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
