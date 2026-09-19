package com.example.autolog.camera

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class CameraUiState(
    val isRecording: Boolean = false,
    val elapsed: Duration = Duration.ZERO,
)

/** 녹화 경과 시간. 한 시간을 넘기면 자리를 하나 더 쓴다 — 클립 길이에 제한이 없다 (planning 6). */
fun Duration.formatElapsed(): String = toComponents { hours, minutes, seconds, _ ->
    if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun Long.nanosToDuration(): Duration = (this / 1_000_000).milliseconds
