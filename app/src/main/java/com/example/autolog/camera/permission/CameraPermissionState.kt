package com.example.autolog.camera.permission

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** 촬영에 필요한 권한. 오디오는 원본 소리를 그대로 이어붙이는 정책(planning 6) 때문에 선택이 아니라 필수다. */
val CameraPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

enum class CameraPermissionStatus {
    Granted,

    /** 아직 한 번도 묻지 않았다. 카메라 화면 최초 진입 시 바로 요청한다. */
    NotRequested,

    /** 거부했지만 다시 물어볼 수 있다. */
    Denied,

    /** 시스템이 더 이상 다이얼로그를 띄워주지 않는다. 설정 화면으로 보내는 수밖에 없다. */
    PermanentlyDenied,
}

@Immutable
class CameraPermissionState(
    val status: CameraPermissionStatus,
    val request: () -> Unit,
    val openAppSettings: () -> Unit,
)

@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val activity = requireNotNull(LocalActivity.current) { "권한 요청에는 Activity가 필요하다" }

    // 영구 거부는 "물어본 적이 있는데 rationale도 못 띄운다"로만 판별할 수 있어서 요청 이력이 필요하다.
    var everRequested by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf(activity.cameraPermissionStatus(everRequested)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        everRequested = true
        status = activity.cameraPermissionStatus(everRequested = true)
    }

    // 설정에서 권한을 바꾸고 돌아오는 경로에는 결과 콜백이 없다. 복귀할 때마다 다시 읽는다.
    LifecycleResumeEffect(everRequested) {
        status = activity.cameraPermissionStatus(everRequested)
        onPauseOrDispose {}
    }

    return CameraPermissionState(
        status = status,
        request = { launcher.launch(CameraPermissions) },
        openAppSettings = { activity.openAppSettings() },
    )
}

private fun Activity.cameraPermissionStatus(everRequested: Boolean): CameraPermissionStatus = when {
    CameraPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    } -> CameraPermissionStatus.Granted

    !everRequested -> CameraPermissionStatus.NotRequested

    CameraPermissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) } ->
        CameraPermissionStatus.Denied

    else -> CameraPermissionStatus.PermanentlyDenied
}

private fun Activity.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )
}
