package com.example.autolog.camera

import android.view.OrientationEventListener
import androidx.activity.compose.LocalActivity
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autolog.camera.permission.CameraPermissionGate
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraDimens
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
        LightSystemBarIcons()

        val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        // 렌즈가 바뀌면 이전 바인딩을 풀고 새 렌즈로 다시 건다.
        LaunchedEffect(lifecycleOwner, uiState.lens) {
            viewModel.bindToCamera(context.applicationContext, lifecycleOwner, uiState.lens)
        }

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
            onPauseOrDispose { viewModel.cancelCountdown() }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(CameraBackground),
            contentAlignment = Alignment.Center,
        ) {
            // 프리뷰 틀의 폭. 9:16보다 넓은 화면에서는 양옆이 검은띠라, 상단 칩은 띠가 아니라 틀 끝에 붙인다.
            val frameWidth = min(maxWidth, maxHeight * 9f / 16f)

            // 촬영 규격이 9:16이므로 프리뷰도 같은 비율로 가둔다 — 보이는 것과 찍히는 것을 맞춘다.
            // 가로 촬영은 기기를 눕혀 찍으므로 이 프레임이 그대로 16:9 가로 영상이 된다.
            surfaceRequest?.let { request ->
                CameraXViewfinder(
                    surfaceRequest = request,
                    modifier = Modifier
                        // 화면이 9:16보다 납작하면 높이가, 길쭉하면 너비가 기준이 된다 — 프레임 전체가 항상 보인다.
                        .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ -> viewModel.onPinch(zoom) }
                        }
                        // 화면을 보며 말하다가 버튼을 찾지 않고 바로 뒤집을 수 있게 한다.
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { viewModel.toggleLens() })
                        }
                        .lensSwipe(
                            // 가로 모드는 눕혀 드므로, 든 사람 기준 위아래가 화면의 좌우다.
                            alongScreenWidth = uiState.orientation == CaptureOrientation.Landscape,
                            onSwipe = viewModel::toggleLens,
                        )
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
                    .align(Alignment.TopCenter)
                    .width(frameWidth)
                    .safeDrawingPadding()
                    .padding(Spacing.md),
            ) {
                AnimatedVisibility(
                    visible = !uiState.isCapturing,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    RecordTimerToggle(timer = uiState.timer, onClick = viewModel::cycleTimer)
                }

                AnimatedVisibility(
                    visible = !uiState.isCapturing,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    OrientationToggle(
                        orientation = uiState.orientation,
                        onClick = viewModel::toggleOrientation,
                    )
                }
            }

            uiState.turnHint?.let { TurnDeviceHint(target = it, deviceRotation = uiState.deviceRotation) }
            uiState.countdown?.let { CountdownNumber(secondsLeft = it, deviceRotation = uiState.deviceRotation) }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 줌과 한 줄에 두어 하단 영역 높이를 늘리지 않는다. 음소거는 썸네일 폭 안 가운데에 맞춘다.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(CameraDimens.cornerAction),
                        contentAlignment = Alignment.Center,
                    ) {
                        MuteToggle(isMuted = uiState.isMuted, onClick = viewModel::toggleMute)
                    }

                    uiState.zoomRange?.takeIf { it.isZoomable }?.let { range ->
                        ZoomControl(range = range, ratio = uiState.zoomRatio, onSelect = viewModel::setZoom)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        uiState.latestClip?.let { clip ->
                            LatestClipThumbnail(uri = clip, onClick = onOpenLatestClip)
                        }
                    }

                    RecordButton(
                        isRecording = uiState.isRecording,
                        isCountingDown = uiState.isCountingDown,
                        onClick = { viewModel.toggleRecording(context) },
                    )

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        ClipListButton(onClick = onOpenClipList)
                    }
                }
            }
        }
    }
}

/** 카메라 화면은 배경이 검다. 머무는 동안만 시스템 바 아이콘을 밝게 바꾸고 나가면 되돌린다. */
@Composable
private fun LightSystemBarIcons() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val wasLightStatusBars = controller.isAppearanceLightStatusBars
        val wasLightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = wasLightStatusBars
            controller.isAppearanceLightNavigationBars = wasLightNavigationBars
        }
    }
}
