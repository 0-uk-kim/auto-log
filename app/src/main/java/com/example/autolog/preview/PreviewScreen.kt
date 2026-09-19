package com.example.autolog.preview

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import com.example.autolog.R
import com.example.autolog.data.clip.Clip
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.Spacing

const val TAG_PLAYER = "preview-player"
const val TAG_PLAY_PAUSE = "preview-play-pause"

/**
 * 선택한 클립을 재생한다 (planning 3-3).
 *
 * 좌우 스와이프로 이전·다음 클립까지 넘기는 것은 #23에서 이 화면을 페이저로 감싸며 들어온다.
 */
@Composable
fun PreviewScreen(
    date: String,
    clipIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraBackground),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = uiState) {
            // 읽는 사이 한 프레임은 검은 화면이다 — 촬영 화면에서 바로 넘어오므로 눈에 띄지 않는다.
            PreviewUiState.Loading -> Unit

            PreviewUiState.Empty -> Text(
                text = stringResource(R.string.preview_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = CameraControlTint,
                modifier = Modifier.padding(Spacing.xl),
            )

            is PreviewUiState.Ready -> ClipPlayer(clip = state.clips[state.startIndex])
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(Spacing.sm),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.preview_back),
                tint = CameraControlTint,
            )
        }
    }
}

/**
 * 클립 하나를 재생한다. 화면 아무 데나 누르면 재생과 일시정지를 오간다 —
 * 세로 영상이 화면을 거의 다 덮어서 작은 버튼을 겨냥하게 만들 이유가 없다.
 */
@OptIn(UnstableApi::class)
@Composable
private fun ClipPlayer(clip: Clip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(player, clip.uri) {
        player.setMediaItem(MediaItem.fromUri(clip.uri))
        player.prepare()
        player.playWhenReady = true
    }

    // 화면을 벗어나면 소리부터 멈춰야 한다. 돌아올 때 이어서 트는 것은 사용자가 정한다.
    LifecycleResumeEffect(player) {
        onPauseOrDispose { player.pause() }
    }

    val playPause = rememberPlayPauseButtonState(player)
    val presentation = rememberPresentationState(player)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = playPause::onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                // 9:16 고정 촬영이라(planning 6-1) 프레임 전체가 항상 보이게 가둔다.
                .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                .testTag(TAG_PLAYER),
        )

        // 첫 프레임이 올라오기 전에는 표면이 회색으로 비어 보인다 — 검은 천을 덮어 둔다.
        if (presentation.coverSurface) {
            Box(Modifier.fillMaxSize().background(CameraBackground))
        }

        AnimatedVisibility(
            visible = playPause.showPlay,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CameraScrim)
                    .testTag(TAG_PLAY_PAUSE),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = stringResource(R.string.preview_play),
                    tint = CameraControlTint,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}
