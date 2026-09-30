package com.example.autolog.edit

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.example.autolog.R
import com.example.autolog.ui.ActionButton
import com.example.autolog.ui.GlassIconButton
import com.example.autolog.ui.GlassPill
import com.example.autolog.ui.ScreenTopBar
import com.example.autolog.ui.VideoSurface
import com.example.autolog.ui.rememberPlaybackPosition
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraHighlight
import com.example.autolog.ui.theme.Danger
import com.example.autolog.ui.theme.Glass
import com.example.autolog.ui.theme.GlassBorder
import com.example.autolog.ui.theme.ListDimens
import com.example.autolog.ui.theme.Spacing
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

const val TAG_EDIT_SAVE = "edit-save"
const val TAG_EDIT_PLAY = "edit-play"
const val TAG_EDIT_SPLIT = "edit-split"
const val TAG_EDIT_REMOVE = "edit-remove"
const val TAG_EDIT_UNDO = "edit-undo"
const val TAG_EDIT_LENGTH = "edit-length"
const val TAG_EDIT_DELETE_CLIP = "edit-delete-clip"

/**
 * 클립 하나에서 남길 조각을 정하는 화면 (planning 5 "3차: 구간 자르기").
 *
 * 앞뒤는 손잡이로, 중간은 재생 위치에서 분할한 뒤 조각을 삭제해 잘라낸다 (#92).
 * 띠를 끌어 원하는 장면을 찾고, 아래 도구 줄로 자르고 지우고 되돌린다 (#94).
 * 원본은 그대로 두고 조각만 저장한다 — 미리보기와 브이로그 병합이 그 조각들만 이어 쓴다.
 * 저장 전에 뒤로 가면 아무것도 바뀌지 않는다.
 */
@Composable
fun EditScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onDone)

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDeletionResult(approved = result.resultCode == Activity.RESULT_OK) }
    LaunchedEffect(viewModel) {
        launch { viewModel.finished.collect { done() } }
        viewModel.deleteRequests.collect { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CameraBackground)
            .safeDrawingPadding(),
    ) {
        // 나가기는 왼쪽 위, 클립을 통째로 버리는 삭제는 오른쪽 위, 저장은 엄지가 닿는 오른쪽 아래다.
        // 저장과 삭제를 멀리 떼어 놓아 잘못 누를 일이 없다.
        ScreenTopBar(
            navigation = {
                GlassIconButton(
                    icon = R.drawable.ic_arrow_back,
                    contentDescription = stringResource(R.string.preview_back),
                    onClick = onDone,
                    tint = CameraControlTint,
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.edit_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = CameraControlTint,
                )
            },
            actions = {
                if (uiState is EditUiState.Ready) {
                    // 찍자마자 들어오는 화면이라 마음에 안 들면 여기서 바로 버린다 (#102).
                    GlassIconButton(
                        icon = R.drawable.ic_delete,
                        contentDescription = stringResource(R.string.edit_delete_clip),
                        onClick = viewModel::delete,
                        tint = Danger,
                        modifier = Modifier.testTag(TAG_EDIT_DELETE_CLIP),
                    )
                }
            },
        )

        when (val state = uiState) {
            EditUiState.Loading -> Unit

            EditUiState.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.edit_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CameraControlTint,
                    modifier = Modifier.padding(Spacing.xl),
                )
            }

            is EditUiState.Ready -> ClipTrimmer(
                state = state,
                actions = viewModel,
                onReset = viewModel::reset,
                onSave = viewModel::save,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ClipTrimmer(
    state: EditUiState.Ready,
    actions: EditViewModel,
    onReset: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = state.clip
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val firstStart = remember(clip.uri) { state.segments.items.first().startMs }
    // 자르기 전 원본 전체를 연다 — 잘려 나갈 부분도 띠를 끌며 볼 수 있어야 한다.
    val player = remember(clip.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clip.uri), firstStart)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LifecycleResumeEffect(player) {
        onPauseOrDispose { player.pause() }
    }

    val position = rememberPlaybackPosition(player)
    val segments by rememberUpdatedState(state.segments)
    val playPause = rememberPlayPauseButtonState(player)

    // 재생은 남긴 조각만 이어서 돈다 — 빈 곳에 닿으면 다음 조각으로, 끝나면 첫 조각으로 건너뛴다.
    // 끝까지 가서 멈춘 상태(isPlaying=false)에서도 되감아야 해서 playWhenReady로 본다.
    LaunchedEffect(player) {
        snapshotFlow { position.value }.collect { positionMs ->
            if (!player.playWhenReady) return@collect
            segments.nextPlayableFrom(positionMs)?.let(player::seekTo)
        }
    }
    // 자르기 버튼은 재생 위치가 나눌 수 있는 자리를 드나들 때만 다시 그린다.
    val canSplit by remember(state.segments, clip.durationMs) {
        derivedStateOf { state.segments.canSplitAt(position.value, clip.durationMs) }
    }
    // 손잡이가 한계(이웃 조각·최소 길이)에 닿는 순간 한 번 진동한다. 닿아 있는 동안 계속 울리지 않는다.
    var atLimit by remember { mutableStateOf(false) }
    val onHandleMoved = { requested: Long, actual: Long? ->
        actual?.let(player::seekTo)
        val limited = actual != null && abs(actual - requested) > LIMIT_SLOP_MS
        if (limited && !atLimit) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        atLimit = limited
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm)
                .clip(MaterialTheme.shapes.extraLarge)
                .border(1.dp, GlassBorder, MaterialTheme.shapes.extraLarge),
        ) {
            VideoSurface(player = player)
        }

        Column(Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.sm)) {
            TrimBar(
                uri = clip.uri,
                durationMs = clip.durationMs,
                segments = state.segments,
                selected = state.selected,
                position = { position.value },
                onTap = { ms ->
                    actions.select(ms)
                    player.seekTo(ms)
                },
                // 찾는 동안에는 멈춘다 — 손을 뗀 자리에서 바로 자를 수 있게.
                onScrub = { ms ->
                    player.pause()
                    player.seekTo(ms)
                },
                onHandleDragStart = {
                    player.pause()
                    atLimit = false
                    actions.beginHandleDrag()
                },
                onMoveStart = { ms -> onHandleMoved(ms, actions.moveStart(ms)) },
                // 손잡이를 놓아도 멈춘 채 경계 프레임을 보여 준다 — 저절로 재생되면 재생 버튼과 엇갈린다.
                onMoveEnd = { ms -> onHandleMoved(ms, actions.moveEnd(ms)) },
                contentDescription = stringResource(R.string.edit_trim_bar),
            )

            // 잘라냈으면 길이가 어떻게 바뀌는지만, 아직이면 사용법 한 줄. 두 줄 높이를 맞춰 도구 줄이 들썩이지 않게 한다.
            val cut = !state.segments.coversWhole(clip.durationMs)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (cut) {
                    // 되돌리는 버튼은 바뀐 결과 바로 옆에 둔다 — 무엇을 되돌리는지 눈으로 이어진다.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.edit_length_change,
                                formatTrimTime(clip.durationMs),
                                formatTrimTime(state.segments.lengthMs),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = CameraHighlight,
                            modifier = Modifier.testTag(TAG_EDIT_LENGTH),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        GlassPill(
                            text = stringResource(R.string.edit_reset),
                            icon = R.drawable.ic_refresh,
                            onClick = onReset,
                            contentColor = CameraControlTint,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = CameraControlTint.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 도구와 저장을 한 줄에 둬 영상에 자리를 더 준다. 저장은 오른쪽 끝, 엄지가 가장 쉽게 닿는 곳이다.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(
                    icon = if (playPause.showPlay) R.drawable.ic_play else R.drawable.ic_pause,
                    label = stringResource(if (playPause.showPlay) R.string.edit_play else R.string.edit_pause),
                    onClick = playPause::onClick,
                    modifier = Modifier.weight(1f).testTag(TAG_EDIT_PLAY),
                )
                ToolButton(
                    icon = R.drawable.ic_cut,
                    label = stringResource(R.string.edit_split),
                    enabled = canSplit,
                    onClick = {
                        player.pause()
                        actions.split(player.currentPosition)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.weight(1f).testTag(TAG_EDIT_SPLIT),
                )
                ToolButton(
                    icon = R.drawable.ic_delete,
                    label = stringResource(R.string.edit_remove),
                    enabled = state.segments.canRemove,
                    onClick = {
                        actions.removeSelected()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.weight(1f).testTag(TAG_EDIT_REMOVE),
                )
                ToolButton(
                    icon = R.drawable.ic_undo,
                    label = stringResource(R.string.edit_undo),
                    enabled = state.canUndo,
                    onClick = actions::undo,
                    modifier = Modifier.weight(1f).testTag(TAG_EDIT_UNDO),
                )
                ActionButton(
                    text = stringResource(R.string.edit_save),
                    onClick = onSave,
                    modifier = Modifier.padding(start = Spacing.sm).testTag(TAG_EDIT_SAVE),
                )
            }
        }
    }
}

/**
 * 유리 원 안의 아이콘과 그 아래 글자. 무엇을 하는지 글자로도 말한다 — 가위 아이콘만으로는 뜻이 갈린다.
 * 누르는 영역은 원이 아니라 칸 전체다 — 줄 하나에 넷이 서서 원만 누르게 하면 겨냥이 빠듯하다.
 */
@Composable
private fun ToolButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 72.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .padding(vertical = Spacing.sm),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ListDimens.glassButton)
                .clip(CircleShape)
                .background(Glass)
                .border(1.dp, GlassBorder, CircleShape),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = CameraControlTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(Spacing.xs + 2.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = CameraControlTint)
    }
}

/** 요청한 자리와 이만큼 넘게 벌어지면 손잡이가 한계에 걸린 것이다. */
private const val LIMIT_SLOP_MS = 100L

/**
 * 편집 화면의 시각 표기. 클립은 대개 1분이 안 돼서 `7.7초`처럼 초로 쓴다 — `0:07.7`은 소수점 때문에
 * 시각으로 읽히지 않는다. 1분을 넘으면 `1:05.3`이다. 손잡이는 1초보다 잘게 움직여 0.1초까지 보여 준다.
 */
internal fun formatTrimTime(ms: Long): String {
    val tenths = ms.coerceAtLeast(0) / 100
    val seconds = tenths / 10
    return if (seconds < 60) {
        String.format(Locale.US, "%d.%d초", seconds, tenths % 10)
    } else {
        String.format(Locale.US, "%d:%02d.%d", seconds / 60, seconds % 60, tenths % 10)
    }
}
