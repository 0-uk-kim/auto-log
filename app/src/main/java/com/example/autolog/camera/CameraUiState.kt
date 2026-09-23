package com.example.autolog.camera

import android.net.Uri
import android.view.Surface

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class CameraUiState(
    val isRecording: Boolean = false,
    val timer: RecordTimer = RecordTimer.Off,
    /** 타이머가 도는 동안 남은 초. 돌지 않으면 null이다 (#61). */
    val countdown: Int? = null,
    val elapsed: Duration = Duration.ZERO,
    val timelapse: TimelapseSpeed = TimelapseSpeed.Off,
    /** 타임랩스 완성본을 만드는 동안의 진행률. 만들고 있지 않으면 null이다 (#65). */
    val timelapseProgress: Int? = null,
    /** 직전 촬영본. 있을 때만 좌측 하단 썸네일을 노출한다 (planning 3-1). */
    val latestClip: Uri? = null,
    /** 기기를 든 방향 ([Surface.ROTATION_0] 등). 화면은 세로로 고정돼 있어 회전해도 레이아웃은 그대로다. */
    val deviceRotation: Int = Surface.ROTATION_0,
    val isMuted: Boolean = false,
    val lens: CameraLens = CameraLens.Back,
    /** 반대쪽 렌즈가 있을 때만 전환 버튼을 보인다. 바인딩 전에는 모르므로 숨겨 둔다. */
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

    /** 앞선 타임랩스를 아직 만드는 중이다. 인코더를 둘 돌리지 않도록 이때는 새로 찍지 않는다. */
    val isEncodingTimelapse: Boolean
        get() = timelapseProgress != null

    /** 타임랩스에는 소리를 담지 않으므로 음소거 전환이 뜻이 없다. */
    val canToggleMute: Boolean
        get() = !timelapse.isOn
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

/**
 * 화면에 보일 경과 시간. 녹화 상태 이벤트는 초당 수십 번 오는데 화면은 초까지만 보인다 —
 * 초 아래를 버려야 같은 값이 이어져 상태가 갱신되지 않고, 카메라 화면이 매번 다시 그려지지 않는다 (#83).
 */
fun Long.nanosToWholeSeconds(): Duration = (this / 1_000_000_000).seconds
