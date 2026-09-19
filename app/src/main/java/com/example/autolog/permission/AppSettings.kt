package com.example.autolog.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 시스템이 더 이상 권한 다이얼로그를 띄워주지 않을 때 남는 유일한 복구 경로. */
fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )
}
