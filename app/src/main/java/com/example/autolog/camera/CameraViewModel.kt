package com.example.autolog.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.net.Uri
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
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
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val clipRepository: ClipRepository,
    private val settingsStore: CameraSettingsStore,
    private val timelapseEncoder: TimelapseEncoder,
) : ViewModel() {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest = _surfaceRequest.asStateFlow()

    // 바인딩 직후 첫 프레임까지는 프리뷰가 검다. 화면은 이 값을 보고 프리뷰를 서서히 띄운다.
    // 매 프레임 불리므로 uiState와 따로 둔다 — StateFlow는 같은 값이면 다시 내보내지 않는다.
    private val _isPreviewStreaming = MutableStateFlow(false)
    val isPreviewStreaming = _isPreviewStreaming.asStateFlow()

    // 프리뷰는 세로 화면에 9:16으로 그린다. 가로 촬영도 기기를 눕혀 찍으니 같은 프레임이 그대로 가로가 된다 (#40).
    @OptIn(ExperimentalCamera2Interop::class)
    private val previewUseCase = Preview.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build(),
        )
        .also { builder ->
            Camera2Interop.Extender(builder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    _isPreviewStreaming.value = true
                }
            })
        }
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
            isMuted = settingsStore.isMuted,
            lens = settingsStore.lens,
            timer = settingsStore.timer,
            timelapse = settingsStore.timelapse,
            hyperlapseSpeed = settingsStore.hyperlapseSpeed,
        ),
    )
    val uiState = _uiState.asStateFlow()

    // 드래그·핀치 중에는 매 프레임 바뀐다. uiState에 두면 카메라 화면 전체가 매번 다시 그려지므로 따로 둔다.
    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio = _zoomRatio.asStateFlow()

    private var recording: Recording? = null
    private var countdown: Job? = null
    private var latestClip: Uri? = null
    private var deviceRotation = Surface.ROTATION_0
    private var camera: Camera? = null
    private var zoomAnimation: Job? = null

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

    /** 녹화 중에는 바꾸지 않는다 — 다시 바인딩하면 진행 중인 녹화가 끊긴다. 바인딩은 화면이 lens를 보고 다시 건다. */
    fun toggleLens() {
        // 카운트다운 중에 다시 바인딩하면 끝나는 순간 녹화가 바인딩과 엇갈릴 수 있다.
        if (_uiState.value.isCapturing || !_uiState.value.canSwitchLens) return
        val next = _uiState.value.lens.toggled()
        settingsStore.lens = next
        _uiState.update { it.copy(lens = next) }
    }

    /** 녹화 중에는 바꾸지 않는다 — 한 클립을 찍는 도중에 배속이 달라질 수 없다. */
    fun selectMode(mode: CameraMode) {
        if (_uiState.value.isCapturing) return
        val speed = when (mode) {
            CameraMode.Hyperlapse -> _uiState.value.hyperlapseSpeed
            CameraMode.Video -> TimelapseSpeed.Off
        }
        settingsStore.timelapse = speed
        _uiState.update { it.copy(timelapse = speed) }
    }

    fun selectHyperlapseSpeed(speed: TimelapseSpeed) {
        if (_uiState.value.isCapturing || !speed.isOn) return
        settingsStore.hyperlapseSpeed = speed
        settingsStore.timelapse = speed
        _uiState.update { it.copy(hyperlapseSpeed = speed, timelapse = speed) }
    }

    fun selectTimer(timer: RecordTimer) {
        if (_uiState.value.isCapturing) return
        settingsStore.timer = timer
        _uiState.update { it.copy(timer = timer) }
    }

    /** 멈춘 동안에는 파일이 이어진 채 아무것도 담기지 않는다. 다시 누르면 같은 클립에 이어 찍는다. */
    fun togglePause() {
        val recording = recording ?: return
        if (_uiState.value.isPaused) recording.resume() else recording.pause()
    }

    /** 화면을 떠나면 카메라가 풀리므로, 돌던 타이머가 끝나도 찍을 수 없다. 그 전에 멈춘다. */
    fun cancelCountdown() {
        countdown?.cancel()
        countdown = null
        _uiState.update { it.copy(countdown = null) }
    }

    /** 녹화 중에도 바꿀 수 있다. 녹화는 이어지고 그 시점부터 소리만 꺼지거나 켜진다. */
    fun toggleMute() {
        if (!_uiState.value.canToggleMute) return
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
        zoomAnimation?.cancel()
        applyZoom(range.clamp(_zoomRatio.value * scale))
    }

    /** 배율 다이얼을 [dragPx]만큼 끈다. 왼쪽(음수)으로 끌면 큰 배율이 가운데로 와 확대된다. */
    fun onZoomDrag(dragPx: Float, pxPerLn: Float) {
        zoomAnimation?.cancel()
        applyZoom(dragZoom(_zoomRatio.value, dragPx, pxPerLn))
    }

    /** 배율 버튼용. 한 번에 바꾸면 프리뷰가 튀므로 몇 프레임에 걸쳐 옮긴다. */
    fun animateZoomTo(ratio: Float) {
        val range = _uiState.value.zoomRange ?: return
        val from = _zoomRatio.value
        val to = range.clamp(ratio)
        zoomAnimation?.cancel()
        zoomAnimation = viewModelScope.launch {
            val start = System.nanoTime()
            do {
                val t = ((System.nanoTime() - start) / ZOOM_ANIMATION_NANOS.toFloat()).coerceAtMost(1f)
                applyZoom(interpolateZoom(from, to, t))
                delay(ZOOM_FRAME_MS)
            } while (t < 1f)
        }
    }

    private fun applyZoom(ratio: Float) {
        val camera = camera ?: return
        val range = _uiState.value.zoomRange ?: return
        val clamped = range.clamp(ratio)
        // 적용은 비동기다. zoomState를 기다리면 핀치 도중 이전 값에 곱해져 튀므로 요청한 값을 바로 상태로 삼는다.
        camera.cameraControl.setZoomRatio(clamped)
        _zoomRatio.value = clamped
    }

    /**
     * 녹화 중이면 멈추고, 타이머가 돌고 있으면 취소한다. 아니면 타이머만큼 기다린 뒤 시작한다.
     * 정지 결과는 Finalize 이벤트로 돌아온다.
     */
    fun toggleRecording(context: Context) {
        recording?.let {
            it.stop()
            return
        }
        if (countdown != null) {
            cancelCountdown()
            return
        }
        if (_uiState.value.isEncodingTimelapse) return
        val seconds = _uiState.value.timer.seconds
        if (seconds == 0) {
            startRecording(context)
            return
        }
        // 기다리는 동안 화면이 다시 만들어질 수 있다. 액티비티를 붙잡지 않는다.
        val appContext = context.applicationContext
        countdown = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _uiState.update { it.copy(countdown = left) }
                delay(1.seconds)
            }
            countdown = null
            _uiState.update { it.copy(countdown = null) }
            startRecording(appContext)
        }
    }

    @SuppressLint("MissingPermission") // 권한이 있을 때만 그려지는 화면에서만 호출된다 (CameraPermissionGate)
    private fun startRecording(context: Context) {
        // 방향은 녹화를 시작할 때 든 방향으로 파일에 새겨진다. 녹화 중에 기기를 돌려도 바뀌지 않는다.
        videoCapture.targetRotation = recordingRotation(deviceRotation)
        val timelapse = _uiState.value.timelapse
        val startedAt = LocalDateTime.now()
        // 타임랩스는 원본을 캐시에 찍어 두고 끝난 뒤 배속을 올려 갤러리에 넣는다. 소리는 담지 않는다.
        val rawFile = if (timelapse.isOn) timelapseEncoder.newRawFile() else null
        val pending: PendingRecording = if (rawFile != null) {
            videoCapture.output.prepareRecording(context, FileOutputOptions.Builder(rawFile).build())
        } else {
            videoCapture.output
                .prepareRecording(context, ClipOutput.mediaStoreOptions(context.contentResolver, startedAt))
                // 음소거로 시작해도 오디오 트랙은 연다 — 열어 두지 않으면 녹화 도중 소리를 켤 수 없다.
                .withAudioEnabled()
        }
        recording = pending
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start ->
                        _uiState.update { it.copy(isRecording = true, isPaused = false) }

                    is VideoRecordEvent.Pause ->
                        _uiState.update { it.copy(isPaused = true) }

                    is VideoRecordEvent.Resume ->
                        _uiState.update { it.copy(isPaused = false) }

                    is VideoRecordEvent.Status ->
                        _uiState.update {
                            it.copy(elapsed = event.recordingStats.recordedDurationNanos.nanosToWholeSeconds())
                        }

                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        if (rawFile != null) {
                            _uiState.update { it.copy(isRecording = false, isPaused = false, elapsed = Duration.ZERO) }
                            val recorded = event.recordingStats.recordedDurationNanos.nanosToDuration()
                            if (event.hasError()) rawFile.delete() else encodeTimelapse(rawFile, timelapse, startedAt, recorded)
                            return@start
                        }
                        // 실패한 녹화는 재생할 것이 없으므로 직전 촬영본을 갱신하지 않는다.
                        val saved = if (event.hasError()) latestClip else event.outputResults.outputUri
                        latestClip = saved
                        _uiState.update {
                            it.copy(isRecording = false, isPaused = false, elapsed = Duration.ZERO, latestClip = saved)
                        }
                    }
                }
            }
            .apply { if (rawFile == null) mute(_uiState.value.isMuted) }
    }

    private fun encodeTimelapse(raw: File, speed: TimelapseSpeed, startedAt: LocalDateTime, recorded: Duration) {
        val endedAt = Instant.now()
        _uiState.update { it.copy(timelapseProgress = 0) }
        viewModelScope.launch {
            // 실패하면 원본도 사라진다. 직전 촬영본은 그대로 둔다 — 일반 촬영이 실패했을 때와 같다.
            try {
                latestClip = timelapseEncoder.encode(raw, speed, startedAt, endedAt, recorded) { percent ->
                    _uiState.update { it.copy(timelapseProgress = percent) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                _uiState.update { it.copy(timelapseProgress = null, latestClip = latestClip) }
            }
        }
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
        _isPreviewStreaming.value = false
        val bound = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            target.selector,
            previewUseCase,
            videoCapture,
        )
        zoomAnimation?.cancel()
        camera = bound
        // 다시 바인딩하면 배율이 1x로 돌아온다. 화면도 카메라가 알려주는 값에서 새로 시작한다.
        bound.cameraInfo.zoomState.value?.let { zoom ->
            _zoomRatio.value = zoom.zoomRatio
            _uiState.update { it.copy(zoomRange = ZoomRange(zoom.minZoomRatio, zoom.maxZoomRatio)) }
        }
        try {
            awaitCancellation()
        } finally {
            // 이미 새 렌즈로 다시 바인딩됐다면 그쪽을 풀면 안 된다.
            if (camera === bound) {
                camera = null
                _isPreviewStreaming.value = false
                cameraProvider.unbindAll()
            }
        }
    }
}
