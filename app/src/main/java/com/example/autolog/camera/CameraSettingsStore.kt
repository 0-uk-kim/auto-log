package com.example.autolog.camera

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 마지막으로 고른 촬영 설정. 값 몇 개라 DataStore까지 들이지 않는다. */
@Singleton
class CameraSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var orientation: CaptureOrientation
        get() = prefs.getString(KEY_ORIENTATION, null)
            ?.let { saved -> CaptureOrientation.entries.firstOrNull { it.name == saved } }
            ?: CaptureOrientation.Portrait
        set(value) = prefs.edit().putString(KEY_ORIENTATION, value.name).apply()

    var isMuted: Boolean
        get() = prefs.getBoolean(KEY_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_MUTED, value).apply()

    var lens: CameraLens
        get() = prefs.getString(KEY_LENS, null)
            ?.let { saved -> CameraLens.entries.firstOrNull { it.name == saved } }
            ?: CameraLens.Back
        set(value) = prefs.edit().putString(KEY_LENS, value.name).apply()

    var timer: RecordTimer
        get() = prefs.getString(KEY_TIMER, null)
            ?.let { saved -> RecordTimer.entries.firstOrNull { it.name == saved } }
            ?: RecordTimer.Off
        set(value) = prefs.edit().putString(KEY_TIMER, value.name).apply()

    private companion object {
        const val PREFS_NAME = "camera"
        const val KEY_ORIENTATION = "capture_orientation"
        const val KEY_MUTED = "muted"
        const val KEY_LENS = "lens"
        const val KEY_TIMER = "record_timer"
    }
}
