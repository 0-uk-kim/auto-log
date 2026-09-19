package com.example.autolog.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 촬영본을 **다시 읽는 데** 필요한 권한. 저장은 앱 소유 항목이라 권한이 없어도 되지만,
 * 재설치하면 소유권이 풀려서 이 권한 없이는 자기가 찍은 클립도 안 보인다 (#35).
 *
 * Android 14부터는 "일부만 허용"이 따로 있어서 둘을 같이 요청한다 — 그래야 시스템 다이얼로그에
 * 전체 허용과 일부 선택이 함께 뜬다.
 */
val MediaReadPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)

    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * 목록이 MediaStore를 얼마나 읽을 수 있는 상태인지.
 *
 * 빈 목록의 원인을 가르는 값이다 — 같은 "0건"이라도 [Full]이면 그날 안 찍은 것이고,
 * [Partial]이면 사용자가 고른 것만 보이는 것이며, [Denied]면 아무것도 못 읽은 것이다 (#15).
 */
enum class MediaAccess {
    Full,

    /** Android 14+ '제한된 액세스'. 사용자가 고른 항목만 보이므로 0건이 곧 "없음"이 아니다. */
    Partial,

    Denied,
}

@Singleton
class MediaAccessProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 설정에서 바꾸고 돌아오는 경로가 있어서 캐시하지 않고 물을 때마다 읽는다. */
    fun current(): MediaAccess = when {
        context.isGranted(MediaReadPermissions.first()) -> MediaAccess.Full

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ->
            MediaAccess.Partial

        else -> MediaAccess.Denied
    }
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * 조회 권한을 (다시) 요청한다. '제한된 액세스' 상태에서 다시 부르면 시스템이 항목을 더 고르는
 * 화면을 띄워주므로, 거부 복구와 "영상 더 선택"이 같은 호출로 처리된다.
 */
@Composable
fun rememberMediaAccessRequest(onResult: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onResult() }
    return { launcher.launch(MediaReadPermissions) }
}
