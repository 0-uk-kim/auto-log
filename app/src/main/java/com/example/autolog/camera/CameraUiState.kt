package com.example.autolog.camera

import android.net.Uri
import android.view.Surface

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class CameraUiState(
    val isRecording: Boolean = false,
    val timer: RecordTimer = RecordTimer.Off,
    /** 타이머가 도는 동안 남은 초. 돌지 않으면 null이다 (#61). */
    val countdown: Int? = null,
    val elapsed: Duration = Duration.ZERO,
    /** 직전 촬영본. 있을 때만 좌측 하단 썸네일을 노출한다 (planning 3-1). */
    val latestClip: Uri? = null,
    val orientation: CaptureOrientation = CaptureOrientation.Portrait,
    /** 기기를 든 방향 ([Surface.ROTATION_0] 등). 화면은 세로로 고정돼 있어 회전해도 레이아웃은 그대로다. */
    val deviceRotation: Int = Surface.ROTATION_0,
    val isMuted: Boolean = false,
    val lens: CameraLens = CameraLens.Back,
    /** 반대쪽 렌즈가 있을 때만 스와이프 전환을 받는다. 바인딩 전에는 모르므로 막아 둔다. */
    val canSwitchLens: Boolean = false,
    /** 카메라가 바인딩되기 전에는 모른다. 그동안은 배율 조작을 받지 않는다. */
    val zoomRange: ZoomRange? = null,
    val zoomRatio: Float = 1f,
) {
    val isCountingDown: Boolean
        get() = countdown != null

    /** 녹화 중이거나 곧 시작될 참이다. 이때는 촬영 설정을 바꾸지 않는다. */
    val isCapturing: Boolean
        get() = isRecording || isCountingDown

    val isDeviceSideways: Boolean
        get() = isSideways(deviceRotation)

    /**
     * 고른 방향과 기기를 든 방향이 어긋나면 그쪽으로 돌리라고 알린다 — 그대로 찍으면 옆으로 누운
     * 영상이 된다. 맞게 들고 있거나 녹화 중이면 null. 카운트다운 중에는 숫자가 그 자리를 쓴다.
     */
    val turnHint: CaptureOrientation?
        get() = when {
            isCapturing -> null
            orientation == CaptureOrientation.Landscape && !isDeviceSideways -> CaptureOrientation.Landscape
            orientation == CaptureOrientation.Portrait && isDeviceSideways -> CaptureOrientation.Portrait
            else -> null
        }
}

/** 녹화 경과 시간. 한 시간을 넘기면 자리를 하나 더 쓴다 — 클립 길이에 제한이 없다 (planning 6). */
fun Duration.formatElapsed(): String = toComponents { hours, minutes, seconds, _ ->
    if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun Long.nanosToDuration(): Duration = (this / 1_000_000).milliseconds
