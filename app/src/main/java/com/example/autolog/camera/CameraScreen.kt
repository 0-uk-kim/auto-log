package com.example.autolog.camera

import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CameraBackground),
            contentAlignment = Alignment.Center,
        ) {
            // 촬영 규격이 9:16이므로 프리뷰도 같은 비율로 가둔다 — 보이는 것과 찍히는 것을 맞춘다.
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

                // P1 #10에서 실제 목록 진입 버튼으로 교체된다.
                Button(
                    onClick = onOpenClipList,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) { Text("영상 목록") }
            }
        }
    }
}
