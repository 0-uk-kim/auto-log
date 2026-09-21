package com.example.autolog.camera

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 촬영본의 방향 (#40). 화면은 세로로 고정돼 있어서 가로는 휴대폰을 옆으로 눕혀 찍는다. */
enum class CaptureOrientation {
    Portrait,
    Landscape,
    ;

    fun toggled(): CaptureOrientation = if (this == Portrait) Landscape else Portrait
}

/**
 * 녹화에 줄 target rotation. 세로는 기기를 어떻게 들든 세로로 남기고, 가로는 기기가 눕혀진 쪽을
 * 따른다 — 반대쪽으로 눕히면 위아래가 뒤집혀 찍힌다. 아직 세워 들고 있으면 왼쪽으로 눕힌 것으로 본다.
 */
fun CaptureOrientation.targetRotation(deviceRotation: Int): Int = when (this) {
    CaptureOrientation.Portrait -> Surface.ROTATION_0
    CaptureOrientation.Landscape ->
        if (deviceRotation == Surface.ROTATION_270) Surface.ROTATION_270 else Surface.ROTATION_90
}

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

/** 마지막으로 고른 방향. 설정 하나라 DataStore까지 들이지 않는다. */
@Singleton
class CaptureOrientationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var orientation: CaptureOrientation
        get() = prefs.getString(KEY_ORIENTATION, null)
            ?.let { saved -> CaptureOrientation.entries.firstOrNull { it.name == saved } }
            ?: CaptureOrientation.Portrait
        set(value) = prefs.edit().putString(KEY_ORIENTATION, value.name).apply()

    private companion object {
        const val PREFS_NAME = "camera"
        const val KEY_ORIENTATION = "capture_orientation"
    }
}
