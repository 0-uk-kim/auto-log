package com.example.autolog.camera

import android.view.OrientationEventListener
import android.view.Surface

/** 촬영본의 방향 (#40). 화면은 세로로 고정돼 있어서 가로는 휴대폰을 옆으로 눕혀 찍는다. */
enum class CaptureOrientation {
    Portrait,
    Landscape,
}

/**
 * 녹화에 줄 target rotation (#79). 눕혀 들었으면 눕혀진 쪽을 따라 가로로, 아니면 세로로 찍는다.
 * 거꾸로 든 것은 세로로 본다 — 책상에 놓인 기기가 뒤집힌 것으로 읽히면 영상이 거꾸로 남는다.
 */
fun recordingRotation(deviceRotation: Int): Int =
    if (isSideways(deviceRotation)) deviceRotation else Surface.ROTATION_0

/**
 * [OrientationEventListener]의 기울기(도)를 화면 회전값으로 바꾼다. 기기를 시계 방향으로 돌리면
 * 화면은 반대 방향으로 돌아가야 바로 보이므로 90°가 ROTATION_270이다.
 */
fun degreesToRotation(degrees: Int): Int? = when (degrees) {
    OrientationEventListener.ORIENTATION_UNKNOWN -> null
    in 45 until 135 -> Surface.ROTATION_270
    in 135 until 225 -> Surface.ROTATION_180
    in 225 until 315 -> Surface.ROTATION_90
    else -> Surface.ROTATION_0
}

fun isSideways(deviceRotation: Int): Boolean =
    deviceRotation == Surface.ROTATION_90 || deviceRotation == Surface.ROTATION_270
