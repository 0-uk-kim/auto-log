package com.example.autolog.camera

import android.view.OrientationEventListener
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autolog.camera.permission.CameraPermissionGate
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.Spacing

const val TAG_VIEWFINDER = "camera-viewfinder"

@Composable
fun CameraScreen(
    onOpenClipList: () -> Unit,
    onOpenLatestClip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    CameraPermissionGate(modifier = modifier) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            viewModel.bindToCamera(context.applicationContext, lifecycleOwner)
        }

        val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        // 화면은 세로로 고정이라 눕혀 든 것을 화면 회전으로는 알 수 없다. 센서로 직접 읽는다.
        DisposableEffect(context) {
            val listener = object : OrientationEventListener(context) {
                override fun onOrientationChanged(degrees: Int) {
                    degreesToRotation(degrees)?.let(viewModel::onDeviceRotationChanged)
                }
            }
            listener.enable()
            onDispose { listener.disable() }
        }

        // 목록에서 지우고 돌아오거나 앱 밖에서 지워질 수 있다. 돌아올 때마다 다시 읽는다.
        LifecycleResumeEffect(Unit) {
            viewModel.refreshLatestClip()
            onPauseOrDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CameraBackground),
            contentAlignment = Alignment.Center,
        ) {
            // 촬영 규격이 9:16이므로 프리뷰도 같은 비율로 가둔다 — 보이는 것과 찍히는 것을 맞춘다.
            // 가로 촬영은 기기를 눕혀 찍으므로 이 프레임이 그대로 16:9 가로 영상이 된다.
            surfaceRequest?.let { request ->
                CameraXViewfinder(
                    surfaceRequest = request,
                    modifier = Modifier
                        // 화면이 9:16보다 납작하면 높이가, 길쭉하면 너비가 기준이 된다 — 프레임 전체가 항상 보인다.
                        .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        .testTag(TAG_VIEWFINDER),
                )
            }

            AnimatedVisibility(
                visible = uiState.isRecording,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = Spacing.md),
            ) {
                ElapsedIndicator(elapsed = uiState.elapsed.formatElapsed())
            }

            AnimatedVisibility(
                visible = !uiState.isRecording,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(Spacing.md),
            ) {
                OrientationToggle(
                    orientation = uiState.orientation,
                    onClick = viewModel::toggleOrientation,
                )
            }

            if (uiState.shouldTurnSideways) {
                TurnSidewaysHint()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .fillMaxWidth()
                    .padding(Spacing.md),
            ) {
                uiState.latestClip?.let { clip ->
                    LatestClipThumbnail(
                        uri = clip,
                        onClick = onOpenLatestClip,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }

                RecordButton(
                    isRecording = uiState.isRecording,
                    onClick = { viewModel.toggleRecording(context) },
                    modifier = Modifier.align(Alignment.Center),
                )

                ClipListButton(
                    onClick = onOpenClipList,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}
