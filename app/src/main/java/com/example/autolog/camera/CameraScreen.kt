package com.example.autolog.camera

import android.view.OrientationEventListener
import androidx.activity.compose.LocalActivity
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autolog.R
import com.example.autolog.camera.permission.CameraPermissionGate
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraDimens
import com.example.autolog.ui.theme.Spacing

const val TAG_VIEWFINDER = "camera-viewfinder"

/** 눌러도 반응하지 않는 조작부. 자리는 지키되 지금은 쓸 수 없다는 것만 알린다. */
private const val DISABLED_ALPHA = 0.38f

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
                // 타임랩스는 찍은 시간과 완성본 길이가 다르다. 얼마나 더 찍어야 할지 가늠하도록 둘 다 보인다.
                val elapsed = uiState.elapsed.formatElapsed()
                ElapsedIndicator(
                    elapsed = if (uiState.timelapse.isOn) {
                        stringResource(
                            R.string.camera_timelapse_elapsed,
                            elapsed,
                            uiState.timelapse.outputOf(uiState.elapsed).formatElapsed(),
                        )
                    } else {
                        elapsed
                    },
                )
            }

            uiState.timelapseProgress?.let { percent ->
                ElapsedIndicator(
                    elapsed = stringResource(R.string.camera_timelapse_encoding, percent),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(top = Spacing.md),
                )
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
                // 음소거와 같은 세로줄에 쌓는다. 줌 줄에 넣으면 줌 버튼이 위아래로 밀린다.
                // 촬영 중에는 자리를 비우지 않고 흐리게만 숨긴다 — 빠지면 아래 줄이 통째로 내려앉는다.
                val timerAlpha by animateFloatAsState(if (uiState.isCapturing) 0f else 1f, label = "timerAlpha")
                Box(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .width(CameraDimens.cornerAction)
                        .alpha(timerAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    TimelapseToggle(speed = uiState.timelapse, onClick = viewModel::cycleTimelapse)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .width(CameraDimens.cornerAction)
                        .alpha(timerAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    RecordTimerToggle(timer = uiState.timer, onClick = viewModel::cycleTimer)
                }

                // 줌과 한 줄에 두어 하단 영역 높이를 늘리지 않는다. 음소거는 썸네일 폭 안 가운데, 렌즈 전환은 목록 버튼 바로 위에 맞춘다.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(CameraDimens.cornerAction),
                        contentAlignment = Alignment.Center,
                    ) {
                        MuteToggle(
                            isMuted = uiState.isMuted,
                            onClick = viewModel::toggleMute,
                            modifier = Modifier.alpha(if (uiState.canToggleMute) 1f else DISABLED_ALPHA),
                        )
                    }

                    uiState.zoomRange?.takeIf { it.isZoomable }?.let { range ->
                        ZoomControl(range = range, ratio = uiState.zoomRatio, onSelect = viewModel::setZoom)
                    }

                    // 녹화 중에는 바꿀 수 없으니 숨긴다.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(CameraDimens.cornerAction),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.animation.AnimatedVisibility(visible = uiState.canSwitchLens && !uiState.isCapturing) {
                            LensToggle(lens = uiState.lens, onClick = viewModel::toggleLens)
                        }
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
                        modifier = Modifier.alpha(if (uiState.isEncodingTimelapse) DISABLED_ALPHA else 1f),
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
