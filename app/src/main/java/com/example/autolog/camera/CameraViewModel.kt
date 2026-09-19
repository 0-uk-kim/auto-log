package com.example.autolog.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
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
import androidx.lifecycle.viewModelScope
import com.example.autolog.data.clip.ClipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val clipRepository: ClipRepository,
) : ViewModel() {

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
    private var latestClip: Uri? = null

    init {
        refreshLatestClip()
    }

    /**
     * 좌측 하단 썸네일을 저장소에서 다시 세운다 (#33).
     *
     * 메모리에만 두면 프로세스가 죽었다 살아났을 때 방금 찍은 영상이 있는데도 썸네일이 사라진다.
     * 앱 밖에서 지워졌을 때 없어져야 하는 것도 같은 이유로 여기서 갈린다.
     *
     * 오늘 것만 본다 — 눌렀을 때 열리는 것이 오늘 목록이므로 둘이 어긋나면 안 된다.
     */
    fun refreshLatestClip() {
        if (recording != null) return
        viewModelScope.launch {
            val latest = clipRepository.clipsOn(LocalDate.now()).maxByOrNull { it.endedAt }?.uri
            latestClip = latest
            _uiState.update { it.copy(latestClip = latest) }
        }
    }

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
                        // 실패한 녹화는 재생할 것이 없으므로 직전 촬영본을 갱신하지 않는다.
                        val saved = if (event.hasError()) latestClip else event.outputResults.outputUri
                        latestClip = saved
                        _uiState.value = CameraUiState(latestClip = saved)
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
