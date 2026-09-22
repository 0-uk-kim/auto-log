package com.example.autolog.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim

const val TAG_PLAYER = "video-surface"
const val TAG_PLAY_PAUSE = "video-play-pause"

/**
 * 영상 한 편을 그리는 표면. 클립 미리보기(#22)와 브이로그 미리보기(#29)가 같이 쓴다.
 *
 * 화면 아무 데나 누르면 재생과 일시정지를 오간다 — 영상이 화면 대부분을 덮어서
 * 작은 컨트롤을 겨냥하게 만들 이유가 없다. 그래서 컨트롤 바를 두지 않고,
 * 멈춰 있을 때만 가운데에 재생 아이콘을 띄운다.
 *
 * [overlay]는 영상 프레임과 같은 크기의 칸에 그려진다 — 레터박스 검은 띠가 아니라 영상 위에 얹힌다.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
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
        // 세로·가로 클립이 섞여 있다(#40). 영상 크기를 알기 전에는 기본인 세로로 잡아 둔다.
        val aspectRatio = presentation.videoSizeDp
            ?.takeIf { it.width > 0f && it.height > 0f }
            ?.let { it.width / it.height }
            ?: (9f / 16f)
        // 프레임 전체가 항상 보이게 영상 비율대로 가둔다.
        Box(Modifier.aspectRatio(aspectRatio, matchHeightConstraintsFirst = aspectRatio < 1f)) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TAG_PLAYER),
            )
            overlay()
        }

        // 첫 프레임이 올라오기 전에는 표면이 회색으로 비어 보인다 — 검은 천을 덮어 둔다.
        if (presentation.coverSurface) {
            Box(Modifier.fillMaxSize().background(CameraBackground))
        }

        AnimatedVisibility(visible = playPause.showPlay, enter = fadeIn(), exit = fadeOut()) {
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
                    contentDescription = stringResource(R.string.player_play),
                    tint = CameraControlTint,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}
