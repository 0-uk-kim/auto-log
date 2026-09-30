package com.example.autolog.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.autolog.R
import com.example.autolog.data.clip.Clip
import com.example.autolog.data.clip.ClipTrim
import com.example.autolog.ui.VideoSurface
import com.example.autolog.ui.rememberPlaybackPosition
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.Coral80
import com.example.autolog.ui.theme.Spacing
import java.util.Locale

const val TAG_EDIT_SAVE = "edit-save"
const val TAG_EDIT_RANGE = "edit-range"

/**
 * 클립 하나에서 남길 구간을 정하는 화면 (planning 5 "3차: 구간 자르기").
 *
 * 원본은 그대로 두고 구간만 저장한다 — 미리보기와 브이로그 병합이 그 구간만 쓴다.
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

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { done() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CameraBackground)
            .safeDrawingPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone, modifier = Modifier.padding(Spacing.sm)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.preview_back),
                    tint = CameraControlTint,
                )
            }
            Text(
                text = stringResource(R.string.edit_title),
                style = MaterialTheme.typography.titleMedium,
                color = CameraControlTint,
                modifier = Modifier.weight(1f),
            )
            val ready = uiState as? EditUiState.Ready
            if (ready != null) {
                TextButton(onClick = viewModel::reset, enabled = ready.isTrimmed) {
                    Text(stringResource(R.string.edit_reset))
                }
                TextButton(
                    onClick = viewModel::save,
                    modifier = Modifier.padding(end = Spacing.sm).testTag(TAG_EDIT_SAVE),
                ) {
                    Text(stringResource(R.string.edit_save), color = Coral80)
                }
            }
        }

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
                clip = state.clip,
                trim = state.trim,
                onMoveStart = viewModel::moveStart,
                onMoveEnd = viewModel::moveEnd,
            )
        }
    }
}

@Composable
private fun ClipTrimmer(
    clip: Clip,
    trim: ClipTrim,
    onMoveStart: (Long) -> ClipTrim?,
    onMoveEnd: (Long) -> ClipTrim?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 자르기 전 원본 전체를 연다 — 잘려 나갈 부분도 손잡이를 끌며 볼 수 있어야 한다.
    val player = remember(clip.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clip.uri), trim.startMs)
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
    val currentTrim by rememberUpdatedState(trim)

    // 재생은 남길 구간 안에서만 돈다 — 끝에 닿으면 시작으로 되감는다.
    LaunchedEffect(player) {
        snapshotFlow { position.value }.collect { positionMs ->
            if (player.isPlaying && positionMs >= currentTrim.endMs) player.seekTo(currentTrim.startMs)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VideoSurface(player = player)
        }

        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)) {
            TrimBar(
                uri = clip.uri,
                durationMs = clip.durationMs,
                trim = trim,
                position = { position.value },
                // 끄는 동안에는 멈추고 손잡이 자리의 프레임을 보여 준다.
                onMoveStart = { ms ->
                    player.pause()
                    onMoveStart(ms)?.let { player.seekTo(it.startMs) }
                },
                onMoveEnd = { ms ->
                    player.pause()
                    onMoveEnd(ms)?.let { player.seekTo(it.endMs) }
                },
                // 손을 떼면 정한 구간을 처음부터 틀어 확인시킨다.
                onDragEnd = {
                    player.seekTo(currentTrim.startMs)
                    player.play()
                },
                contentDescription = stringResource(R.string.edit_trim_bar),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(
                    R.string.edit_range,
                    formatTrimTime(trim.startMs),
                    formatTrimTime(trim.endMs),
                    formatTrimTime(trim.lengthMs),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = CameraControlTint,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag(TAG_EDIT_RANGE),
            )
        }
    }
}

/** 손잡이는 1초보다 잘게 움직인다 — 목록 표기(`0:07`)와 달리 0.1초까지 보여 준다. */
internal fun formatTrimTime(ms: Long): String {
    val tenths = (ms.coerceAtLeast(0) / 100)
    val totalSeconds = tenths / 10
    return String.format(Locale.US, "%d:%02d.%d", totalSeconds / 60, totalSeconds % 60, tenths % 10)
}
