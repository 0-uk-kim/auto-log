package com.example.autolog.preview

import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.media3.common.Player
import com.example.autolog.list.formatClipDuration
import com.example.autolog.list.formatClipTime
import com.example.autolog.ui.rememberPlaybackPosition
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.autolog.R
import com.example.autolog.data.clip.Clip
import com.example.autolog.ui.VideoSurface
import com.example.autolog.ui.rememberClipThumbnail
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.Spacing

const val TAG_POSITION = "preview-position"
const val TAG_PREVIEW_EDIT = "preview-edit"

/** 뒤로 버튼(48dp + 여백)과 같은 높이 — 위치 표시가 뒤로 버튼과 한 줄에 선다. */
private val TOP_BAR_HEIGHT = 64.dp

/**
 * 목록 순서를 따라 클립을 재생한다 (planning 6 "미리보기 재생 범위").
 *
 * 단일 클립 플레이어가 아니라 **페이저**다 — 좌우로 넘기면 이전·다음 클립으로 간다.
 * 아래 편집 버튼은 지금 보고 있는 클립의 편집 화면을 연다 (planning 5 "3차: 구간 자르기").
 */
@Composable
fun PreviewScreen(
    date: String,
    clipIndex: Int,
    onBack: () -> Unit,
    onEdit: (clipId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 편집 화면에서 돌아오면 바뀐 구간으로 다시 읽는다. 바뀐 것이 없으면 상태가 같아 그대로다.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

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

            is PreviewUiState.Ready -> ClipPager(
                clips = state.clips,
                startIndex = state.startIndex,
                onEdit = onEdit,
            )
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
 * 목록 순서대로 넘겨 보는 페이저.
 *
 * 플레이어는 **하나만 만들어 재사용한다** — 페이지마다 만들면 코덱 인스턴스가 그만큼 잡히고,
 * 빠르게 넘길 때 소리가 겹친다. 클립 전체를 재생목록으로 넣어 두고 페이지가 멈춘 자리로 옮긴다.
 */
@OptIn(UnstableApi::class)
@Composable
private fun ClipPager(
    clips: List<Clip>,
    startIndex: Int,
    onEdit: (clipId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context)
            // 재생목록이라 끝나면 자동으로 다음 클립이 시작된다. 페이지는 그대로인데 화면만
            // 바뀌면 어긋나므로, 넘기는 것은 사용자 손에만 맡긴다.
            .setPauseAtEndOfMediaItems(true)
            .build()
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(player, clips) {
        // 편집에서 돌아와 다시 읽은 것이면 보던 클립에 머문다.
        val index = if (player.mediaItemCount > 0) player.currentMediaItemIndex.coerceIn(clips.indices) else startIndex
        val factory = DefaultMediaSourceFactory(context)
        player.setMediaSources(clips.map { it.previewMediaSource(context, factory) }, index, C.TIME_UNSET)
        player.prepare()
        player.playWhenReady = true
    }

    // 화면을 벗어나면 소리부터 멈춰야 한다. 돌아올 때 이어서 트는 것은 사용자가 정한다.
    LifecycleResumeEffect(player) {
        onPauseOrDispose { player.pause() }
    }

    val pagerState = rememberPagerState(initialPage = startIndex) { clips.size }

    // 손을 뗀 뒤 자리가 확정된 페이지만 따라간다 — 끄는 도중마다 옮기면 지나치는 클립이 잠깐씩 재생된다.
    LaunchedEffect(pagerState, player) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (player.currentMediaItemIndex != page) {
                player.seekTo(page, C.TIME_UNSET)
                player.play()
            }
        }
    }

    // 위아래 바를 영상 위에 겹치지 않고 따로 둔다 — 밝은 장면에서 흰 글자가 묻힌다.
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            // 넘기다 보면 하루치 중 어디쯤인지 잃는다. 목록에서 본 번호와 같은 값을 보여 준다.
            PagePosition(page = pagerState.currentPage, total = clips.size)
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page == pagerState.settledPage) {
                VideoSurface(player = player)
            } else {
                // 넘기는 중 옆 페이지는 썸네일로 채운다 — 표면은 하나뿐이라 여기에 붙일 것이 없다.
                NeighbourClip(clip = clips[page])
            }
        }

        ClipInfoBar(
            clip = clips[pagerState.currentPage],
            player = player,
            onEdit = { onEdit(clips[pagerState.currentPage].id) },
        )
    }
}

/**
 * 지금 보는 클립이 언제 찍은 것인지, 어디쯤 재생 중인지, 그리고 할 수 있는 일(편집)을 아래에 모은다.
 * 편집은 이 화면의 주된 다음 동작이라 모서리 아이콘이 아니라 글자가 붙은 버튼으로 둔다.
 */
@Composable
private fun ClipInfoBar(
    clip: Clip,
    player: Player,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = rememberPlaybackPosition(player)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatClipTime(clip.endedAt),
                    style = MaterialTheme.typography.titleMedium,
                    color = CameraControlTint,
                )
                Text(
                    text = formatClipDuration(clip.playedDurationMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = CameraControlTint.copy(alpha = 0.7f),
                )
            }
            Button(
                onClick = onEdit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CameraControlTint.copy(alpha = 0.18f),
                    contentColor = CameraControlTint,
                ),
                contentPadding = PaddingValues(horizontal = Spacing.md),
                modifier = Modifier.testTag(TAG_PREVIEW_EDIT),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cut),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.preview_edit))
            }
        }
        LinearProgressIndicator(
            progress = {
                val duration = player.duration
                if (duration > 0) (position.value.toFloat() / duration).coerceIn(0f, 1f) else 0f
            },
            color = CameraControlTint,
            trackColor = CameraControlTint.copy(alpha = 0.25f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
        )
    }
}

@Composable
private fun PagePosition(page: Int, total: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.preview_position, page + 1, total),
        style = MaterialTheme.typography.labelLarge,
        color = CameraControlTint,
        modifier = modifier
            .clip(CircleShape)
            .background(CameraControlTint.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .testTag(TAG_POSITION),
    )
}

@Composable
private fun NeighbourClip(clip: Clip, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraBackground),
        contentAlignment = Alignment.Center,
    ) {
        rememberClipThumbnail(clip.uri)?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // 썸네일은 클립 비율을 그대로 담고 있다 — 세로·가로가 섞인 목록이라 거기에 맞춘다.
                modifier = Modifier.aspectRatio(
                    bitmap.width.toFloat() / bitmap.height,
                    matchHeightConstraintsFirst = bitmap.height > bitmap.width,
                ),
            )
        }
    }
}
