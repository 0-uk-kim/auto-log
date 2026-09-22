package com.example.autolog.camera

import androidx.camera.core.CameraSelector

/** 촬영에 쓰는 렌즈 (#49). 전면은 셀카 — 자기 얼굴을 보며 말하는 장면용이다. */
enum class CameraLens(val selector: CameraSelector) {
    Back(CameraSelector.DEFAULT_BACK_CAMERA),
    Front(CameraSelector.DEFAULT_FRONT_CAMERA),
    ;

    fun toggled(): CameraLens = if (this == Back) Front else Back
}
