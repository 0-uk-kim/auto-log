package com.example.autolog.camera

import android.view.OrientationEventListener
import androidx.activity.compose.LocalActivity
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autolog.R
import com.example.autolog.camera.permission.CameraPermissionGate
import com.example.autolog.ui.theme.CameraBackground
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
        val isPreviewStreaming by viewModel.isPreviewStreaming.collectAsStateWithLifecycle()
        // 위임하지 않고 State째 넘긴다 — 배율을 읽는 줌 줄만 다시 그려지게 한다.
        val zoomRatio = viewModel.zoomRatio.collectAsStateWithLifecycle()
        val zoomDial = rememberZoomDialState()
        // 화면 복귀·렌즈 전환 때 검은 프리뷰가 갑자기 켜지지 않게 첫 프레임부터 서서히 띄운다.
        val previewAlpha by animateFloatAsState(
            targetValue = if (isPreviewStreaming) 1f else 0f,
            animationSpec = tween(PREVIEW_FADE_MS),
            label = "previewAlpha",
        )

        KeepScreenOn(enabled = uiState.isCapturing)

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
                        // 뷰파인더는 SurfaceView라 투명도가 먹지 않는다. 위에 검은 막을 덮었다 걷는다.
                        .drawWithContent {
                            drawContent()
                            drawRect(CameraBackground, alpha = 1f - previewAlpha)
                        }
                        .viewfinderGestures(
                            onPinch = { scale ->
                                zoomDial.touch()
                                viewModel.onPinch(scale)
                            },
                            // 갤럭시처럼 프리뷰를 좌우로 쓸면 옆 모드로 넘어간다.
                            onSwipe = { towardEnd -> viewModel.selectMode(uiState.mode.neighbor(towardEnd)) },
                        )
                        // 화면을 보며 말하다가 버튼을 찾지 않고 바로 뒤집을 수 있게 한다.
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { viewModel.toggleLens() })
                        }
                        .testTag(TAG_VIEWFINDER),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding(),
                contentAlignment = Alignment.Center,
            ) {
                CameraTopBar(
                    state = uiState,
                    onOpenClipList = onOpenClipList,
                    onSelectTimer = viewModel::selectTimer,
                    onSelectSpeed = viewModel::selectHyperlapseSpeed,
                    onToggleMute = viewModel::toggleMute,
                )

                // 촬영 중에는 비워 둔 상단 가운데에 경과 시간이 온다.
                AnimatedVisibility(visible = uiState.isRecording, enter = fadeIn(), exit = fadeOut()) {
                    // 하이퍼랩스는 찍은 시간과 완성본 길이가 다르다. 얼마나 더 찍어야 할지 가늠하도록 둘 다 보인다.
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
                        isPaused = uiState.isPaused,
                    )
                }

                uiState.timelapseProgress?.let { percent ->
                    ElapsedIndicator(
                        elapsed = stringResource(R.string.camera_timelapse_encoding, percent),
                        isRecording = false,
                    )
                }
            }

            uiState.countdown?.let { CountdownNumber(secondsLeft = it, deviceRotation = uiState.deviceRotation) }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .fillMaxWidth()
                    .padding(bottom = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                uiState.zoomRange?.takeIf { it.isZoomable }?.let { range ->
                    ZoomControl(
                        range = range,
                        ratio = zoomRatio,
                        dialState = zoomDial,
                        onSelect = viewModel::animateZoomTo,
                        onDrag = viewModel::onZoomDrag,
                    )
                }

                // 촬영 중에는 모드를 바꿀 수 없다. 자리는 남겨 셔터 줄이 움직이지 않게 한다.
                val modeAlpha by animateFloatAsState(if (uiState.isCapturing) 0f else 1f, label = "modeAlpha")
                CameraModeBar(
                    selected = uiState.mode,
                    onSelect = viewModel::selectMode,
                    modifier = Modifier
                        .padding(vertical = Spacing.sm)
                        .alpha(modeAlpha),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (uiState.isRecording) {
                            PauseButton(isPaused = uiState.isPaused, onClick = viewModel::togglePause)
                        } else if (!uiState.isCountingDown) {
                            uiState.latestClip?.let { clip ->
                                LatestClipThumbnail(uri = clip, onClick = onOpenLatestClip)
                            }
                        }
                    }

                    RecordButton(
                        isRecording = uiState.isRecording,
                        isCountingDown = uiState.isCountingDown,
                        onClick = { viewModel.toggleRecording(context) },
                        modifier = Modifier.alpha(if (uiState.isEncodingTimelapse) DISABLED_ALPHA else 1f),
                    )

                    // 녹화 중에는 바꿀 수 없으니 숨긴다.
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.canSwitchLens && !uiState.isCapturing,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            LensToggle(lens = uiState.lens, onClick = viewModel::toggleLens)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 두 손가락은 핀치 배율, 한 손가락 가로 쓸기는 모드 전환. 한 손가락으로 시작해 두 번째 손가락이
 * 내려오면 그 제스처는 끝까지 핀치로만 본다 — 핀치를 풀며 손가락이 옆으로 흘러도 모드가 바뀌지 않는다.
 */
private fun Modifier.viewfinderGestures(
    onPinch: (Float) -> Unit,
    onSwipe: (towardEnd: Boolean) -> Unit,
): Modifier = pointerInput(Unit) {
    val swipeThreshold = SWIPE_THRESHOLD_DP.dp.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var pinched = false
        var swiped = false
        var pan = Offset.Zero
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } > 1) {
                pinched = true
                val zoom = event.calculateZoom()
                if (zoom != 1f) onPinch(zoom)
                event.changes.forEach { it.consume() }
            } else if (!pinched && !swiped) {
                pan += event.calculatePan()
                if (abs(pan.x) > swipeThreshold && abs(pan.x) > abs(pan.y) * 2) {
                    swiped = true
                    onSwipe(pan.x < 0)
                    // 소비해 두어야 두 번 쓸었을 때 더블 탭으로 렌즈가 뒤집히지 않는다.
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

private const val SWIPE_THRESHOLD_DP = 48

private const val PREVIEW_FADE_MS = 200

/** 찍는 동안에는 손대지 않아도 화면이 꺼지지 않게 한다 (#81). 꺼지면 액티비티가 멈춰 녹화도 끊긴다. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
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
