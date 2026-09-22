package com.example.autolog.camera

import java.util.Locale
import kotlin.math.round

/** 후면 카메라가 낼 수 있는 배율 범위 (#42). 광각 렌즈가 있으면 min이 1보다 작다. */
data class ZoomRange(val min: Float, val max: Float) {
    val isZoomable: Boolean get() = max > min

    fun clamp(ratio: Float): Float = ratio.coerceIn(min, max)
}

/**
 * 빠른 전환 버튼에 둘 배율. 광각이 있으면 가장 넓은 배율부터, 1x·2x는 범위 안일 때만 둔다.
 * 광각 배율은 기기마다 0.5·0.6처럼 달라 고정값 대신 min을 그대로 쓴다.
 */
fun ZoomRange.presets(): List<Float> {
    val wide = min.takeIf { it < 1f }
    return listOfNotNull(wide, 1f, 2f).filter { it in min..max }
}

/** 현재 배율이 어느 버튼 구간에 있는지 — 버튼 사이 배율이면 아래쪽 버튼이 그 값을 대신 보여준다. */
fun List<Float>.activePreset(ratio: Float): Float? =
    lastOrNull { it <= ratio + ZOOM_EPSILON } ?: firstOrNull()

/** 1x, 2x, 1.4x, 0.6x — 정수면 소수점을 떼고, 아니면 한 자리까지만. */
fun formatZoom(ratio: Float): String {
    val rounded = round(ratio * 10) / 10
    return if (rounded % 1f == 0f) "${rounded.toInt()}x" else String.format(Locale.US, "%.1fx", rounded)
}

private const val ZOOM_EPSILON = 0.05f
