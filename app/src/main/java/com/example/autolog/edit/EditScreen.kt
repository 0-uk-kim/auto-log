package com.example.autolog.edit

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.autolog.R
import com.example.autolog.data.clip.Clip
import com.example.autolog.list.formatClipDuration
import com.example.autolog.ui.VideoSurface
import com.example.autolog.ui.rememberPlaybackPosition
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.Spacing

const val TAG_EDIT_SEEK = "edit-seek"

/**
 * 클립 하나를 손보는 화면 (planning 5 "2차: 미리보기 → 편집").
 *
 * 미리보기와 달리 넘겨 보는 페이저가 아니다 — 한 클립 안에서 위치를 오가며 자막 구간을
 * 정하는 곳이라 시크바가 주인공이다.
 */
@Composable
fun EditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CameraBackground)
            .safeDrawingPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.sm)) {
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
            )
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

            is EditUiState.Ready -> ClipEditor(clip = state.clip)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ClipEditor(clip: Clip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(clip.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clip.uri))
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

    val position by rememberPlaybackPosition(player)
    // 플레이어가 길이를 읽기 전에는 MediaStore 값으로 버틴다 — 둘은 수 ms 차이뿐이다.
    val duration = player.duration.takeIf { it > 0 } ?: clip.durationMs

    Column(modifier = modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VideoSurface(player = player)
        }

        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Slider(
                value = position.coerceIn(0, duration).toFloat(),
                onValueChange = { player.seekTo(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = CameraControlTint,
                    activeTrackColor = CameraControlTint,
                    inactiveTrackColor = CameraControlTint.copy(alpha = 0.3f),
                    inactiveTickColor = CameraControlTint.copy(alpha = 0.3f),
                ),
                modifier = Modifier.testTag(TAG_EDIT_SEEK),
            )
            Text(
                text = stringResource(
                    R.string.edit_position,
                    formatClipDuration(position),
                    formatClipDuration(duration),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = CameraControlTint,
            )
        }
    }
}
