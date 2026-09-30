package com.example.autolog.camera

import java.util.Locale
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
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

/** 선택되지 않은 버튼의 글자. 갤럭시처럼 x를 떼고, 1보다 작으면 앞의 0도 뗀다 (.6). */
fun formatZoomShort(ratio: Float): String = formatZoom(ratio).removeSuffix("x").removePrefix("0")

/**
 * 다이얼을 [dragPx]만큼 끈 뒤의 배율. 눈금이 로그 간격이라 손가락 아래 눈금이 손가락을 따라가려면
 * 배율도 로그 공간에서 움직여야 한다 — [pxPerLn]만큼 끌 때 배율이 e배가 된다. 왼쪽(음수)이 확대다.
 */
fun dragZoom(ratio: Float, dragPx: Float, pxPerLn: Float): Float = ratio * exp(-dragPx / pxPerLn)

/** 다이얼 눈금 하나. [label]이 있으면 굵은 눈금에 숫자를 단다. */
data class ZoomTick(val ratio: Float, val label: String?)

/**
 * 다이얼 눈금. 로그 간격에서 촘촘함이 비슷하도록 배율이 커질수록 간격을 넓힌다.
 * 숫자는 넓은 쪽 끝·1·2·3·5·10처럼 갤럭시 다이얼에 찍히는 값에만 단다.
 */
fun ZoomRange.ticks(): List<ZoomTick> {
    // 0.1 단위 정수로 세야 부동소수 누적 오차 없이 1.0·2.0 같은 자리에 정확히 떨어진다.
    val start = (min * 10).roundToInt()
    val end = (max * 10).roundToInt()
    val ticks = mutableListOf<ZoomTick>()
    var tenths = start
    while (tenths <= end) {
        val ratio = tenths / 10f
        val label = when {
            tenths == start && min < 1f -> formatZoomShort(ratio)
            tenths % 10 == 0 && tenths / 10 in LABELED_RATIOS -> (tenths / 10).toString()
            else -> null
        }
        ticks += ZoomTick(ratio, label)
        tenths += when {
            tenths < 20 -> 1
            tenths < 50 -> 2
            tenths < 100 -> 5
            tenths < 300 -> 10
            else -> 50
        }
    }
    return ticks
}

private val LABELED_RATIOS = setOf(1, 2, 3, 5, 10, 20, 30, 50, 100)

/**
 * 배율 애니메이션의 t(0..1) 지점 배율. 눈에는 배율이 곱셈으로 느껴지므로 로그 공간에서 잇는다 —
 * 선형으로 이으면 0.6x→2x에서 넓은 쪽을 순식간에 지나간다. 끝은 천천히 멈춘다.
 */
fun interpolateZoom(from: Float, to: Float, t: Float): Float {
    val eased = 1f - (1f - t.coerceIn(0f, 1f)).pow(3)
    return from * (to / from).pow(eased)
}

const val ZOOM_ANIMATION_NANOS = 250_000_000L
const val ZOOM_FRAME_MS = 16L

private const val ZOOM_EPSILON = 0.05f
