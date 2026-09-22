package com.example.autolog.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.MirrorMode
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
import kotlin.time.Duration
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val clipRepository: ClipRepository,
    private val settingsStore: CameraSettingsStore,
) : ViewModel() {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest = _surfaceRequest.asStateFlow()

    // 프리뷰는 세로 화면에 9:16으로 그린다. 가로 촬영도 기기를 눕혀 찍으니 같은 프레임이 그대로 가로가 된다 (#40).
    private val previewUseCase = Preview.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build(),
        )
        .build()
        .apply { setSurfaceProvider { request -> _surfaceRequest.value = request } }

    // FHD·30fps 고정 — 방향에 따라 1080×1920 또는 1920×1080. 기기가 FHD를 못 하면 한 단계 낮은 화질로 대체한다 (planning 6-1).
    private val recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD),
            ),
        )
        .build()

    // 셀카는 프리뷰가 거울처럼 보이므로 저장본도 같은 좌우로 남긴다 (#49). 후면은 그대로다.
    private val videoCapture = VideoCapture.Builder(recorder)
        .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
        .build()

    private val _uiState = MutableStateFlow(CameraUiState(
            orientation = settingsStore.orientation,
            isMuted = settingsStore.isMuted,
            lens = settingsStore.lens,
        ),
    )
    val uiState = _uiState.asStateFlow()

    private var recording: Recording? = null
    private var latestClip: Uri? = null
    private var deviceRotation = Surface.ROTATION_0
    private var camera: Camera? = null

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

    /** 녹화 중에는 바꾸지 않는다 — 한 클립 안에서 방향이 바뀔 수 없다. */
    fun toggleOrientation() {
        if (recording != null) return
        val next = _uiState.value.orientation.toggled()
        settingsStore.orientation = next
        _uiState.update { it.copy(orientation = next) }
    }

    /** 녹화 중에는 바꾸지 않는다 — 다시 바인딩하면 진행 중인 녹화가 끊긴다. 바인딩은 화면이 lens를 보고 다시 건다. */
    fun toggleLens() {
        if (recording != null || !_uiState.value.canSwitchLens) return
        val next = _uiState.value.lens.toggled()
        settingsStore.lens = next
        _uiState.update { it.copy(lens = next) }
    }

    /** 녹화 중에도 바꿀 수 있다. 녹화는 이어지고 그 시점부터 소리만 꺼지거나 켜진다. */
    fun toggleMute() {
        val next = !_uiState.value.isMuted
        settingsStore.isMuted = next
        recording?.mute(next)
        _uiState.update { it.copy(isMuted = next) }
    }

    fun onDeviceRotationChanged(rotation: Int) {
        deviceRotation = rotation
        _uiState.update { it.copy(deviceRotation = rotation) }
    }

    /** 핀치 한 번의 배율 변화량을 현재 배율에 곱한다. 녹화 중에도 막지 않는다 — 배율은 파일 규격과 무관하다. */
    fun onPinch(scale: Float) {
        val range = _uiState.value.zoomRange ?: return
        setZoom(range.clamp(_uiState.value.zoomRatio * scale))
    }

    fun setZoom(ratio: Float) {
        val camera = camera ?: return
        val range = _uiState.value.zoomRange ?: return
        val clamped = range.clamp(ratio)
        // 적용은 비동기다. zoomState를 기다리면 핀치 도중 이전 값에 곱해져 튀므로 요청한 값을 바로 상태로 삼는다.
        camera.cameraControl.setZoomRatio(clamped)
        _uiState.update { it.copy(zoomRatio = clamped) }
    }

    /** 녹화 중이면 멈추고, 아니면 시작한다. 정지 결과는 Finalize 이벤트로 돌아온다. */
    @SuppressLint("MissingPermission") // 권한이 있을 때만 그려지는 화면에서만 호출된다 (CameraPermissionGate)
    fun toggleRecording(context: Context) {
        recording?.let {
            it.stop()
            return
        }
        // 방향은 녹화를 시작할 때 파일에 새겨진다. 녹화 중에 기기를 돌려도 바뀌지 않는다.
        videoCapture.targetRotation = _uiState.value.orientation.targetRotation(deviceRotation)
        recording = videoCapture.output
            .prepareRecording(context, ClipOutput.mediaStoreOptions(context.contentResolver))
            // 음소거로 시작해도 오디오 트랙은 연다 — 열어 두지 않으면 녹화 도중 소리를 켤 수 없다.
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
                        _uiState.update {
                            it.copy(isRecording = false, elapsed = Duration.ZERO, latestClip = saved)
                        }
                    }
                }
            }
            .apply { mute(_uiState.value.isMuted) }
    }

    override fun onCleared() {
        recording?.stop()
        recording = null
    }

    /** 호출한 코루틴이 취소될 때까지 바인딩을 유지한다. 화면을 떠나면 자동으로 해제된다. */
    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner, lens: CameraLens) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(appContext)
        val available = CameraLens.entries.filter { cameraProvider.hasCamera(it.selector) }
        // 저장된 렌즈가 이 기기에 없으면(전면 없는 기기 등) 있는 쪽으로 찍는다.
        val target = lens.takeIf { it in available } ?: available.firstOrNull() ?: return
        _uiState.update { it.copy(lens = target, canSwitchLens = available.size > 1) }
        // 렌즈를 바꾸면 이전 호출의 해제와 이번 바인딩 중 무엇이 먼저 돌지 보장되지 않는다. 먼저 풀고 건다.
        cameraProvider.unbindAll()
        val bound = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            target.selector,
            previewUseCase,
            videoCapture,
        )
        camera = bound
        // 다시 바인딩하면 배율이 1x로 돌아온다. 화면도 카메라가 알려주는 값에서 새로 시작한다.
        bound.cameraInfo.zoomState.value?.let { zoom ->
            _uiState.update {
                it.copy(zoomRange = ZoomRange(zoom.minZoomRatio, zoom.maxZoomRatio), zoomRatio = zoom.zoomRatio)
            }
        }
        try {
            awaitCancellation()
        } finally {
            // 이미 새 렌즈로 다시 바인딩됐다면 그쪽을 풀면 안 된다.
            if (camera === bound) {
                camera = null
                cameraProvider.unbindAll()
            }
        }
    }
}
