package com.example.autolog.camera.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.Spacing

const val TAG_DENIED = "permission-denied"
const val TAG_PERMANENTLY_DENIED = "permission-permanently-denied"

/**
 * 권한이 있을 때만 [content]를 띄우고, 없으면 복구 경로를 보여준다.
 * 카메라는 메인 화면이라 권한 거부가 곧 앱을 못 쓰는 상태이므로 화면 전체를 이 게이트가 덮는다.
 */
@Composable
fun CameraPermissionGate(
    modifier: Modifier = Modifier,
    state: CameraPermissionState = rememberCameraPermissionState(),
    content: @Composable () -> Unit,
) {
    LaunchedEffect(state.status) {
        if (state.status == CameraPermissionStatus.NotRequested) state.request()
    }

    when (state.status) {
        CameraPermissionStatus.Granted -> content()

        // 최초 진입 요청 다이얼로그가 뜨기 직전의 한 프레임. 프리뷰 대신 검은 화면을 깔아 깜빡임을 막는다.
        CameraPermissionStatus.NotRequested -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(CameraBackground),
        )

        CameraPermissionStatus.Denied -> PermissionRecovery(
            message = stringResource(R.string.camera_permission_denied),
            actionLabel = stringResource(R.string.camera_permission_action_request),
            onAction = state.request,
            testTag = TAG_DENIED,
            modifier = modifier,
        )

        CameraPermissionStatus.PermanentlyDenied -> PermissionRecovery(
            message = stringResource(R.string.camera_permission_permanently_denied),
            actionLabel = stringResource(R.string.camera_permission_action_settings),
            onAction = state.openAppSettings,
            testTag = TAG_PERMANENTLY_DENIED,
            modifier = modifier,
        )
    }
}

@Composable
private fun PermissionRecovery(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraBackground)
            .padding(Spacing.lg)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CameraControlTint,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
