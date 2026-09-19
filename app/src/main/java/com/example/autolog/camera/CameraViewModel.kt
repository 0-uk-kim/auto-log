package com.example.autolog.camera

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class CameraViewModel @Inject constructor() : ViewModel() {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest = _surfaceRequest.asStateFlow()

    // 1차는 9:16 세로 고정이라 프리뷰도 16:9 선택 전략으로 못 박는다 (planning 6-1).
    private val previewUseCase = Preview.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build(),
        )
        .build()
        .apply { setSurfaceProvider { request -> _surfaceRequest.value = request } }

    // 1080×1920·30fps 고정. 기기가 FHD를 못 하면 한 단계 낮은 화질로 대체한다 (planning 6-1).
    private val recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD),
            ),
        )
        .build()

    val videoCapture = VideoCapture.withOutput(recorder)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()

    private var recording: Recording? = null

    /** 녹화 중이면 멈추고, 아니면 시작한다. 정지 결과는 Finalize 이벤트로 돌아온다. */
    @SuppressLint("MissingPermission") // 권한이 있을 때만 그려지는 화면에서만 호출된다 (CameraPermissionGate)
    fun toggleRecording(context: Context) {
        recording?.let {
            it.stop()
            return
        }
        recording = videoCapture.output
            .prepareRecording(context, ClipOutput.mediaStoreOptions(context.contentResolver))
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start ->
                        _uiState.update { it.copy(isRecording = true) }

                    is VideoRecordEvent.Status ->
                        _uiState.update {
                            it.copy(elapsed = event.recordingStats.recordedDurationNanos.nanosToDuration())
                        }

                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        _uiState.value = CameraUiState()
                    }
                }
            }
    }

    override fun onCleared() {
        recording?.stop()
        recording = null
    }

    /** 호출한 코루틴이 취소될 때까지 바인딩을 유지한다. 화면을 떠나면 자동으로 해제된다. */
    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(appContext)
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            previewUseCase,
            videoCapture,
        )
        try {
            awaitCancellation()
        } finally {
            cameraProvider.unbindAll()
        }
    }
}
