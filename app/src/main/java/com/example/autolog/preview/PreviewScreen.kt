package com.example.autolog.preview

import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.MediaItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import com.example.autolog.ui.GlassIconButton
import com.example.autolog.ui.ActionButton
import com.example.autolog.ui.GlowIcon
import com.example.autolog.ui.ScreenTopBar
import com.example.autolog.ui.VideoSurface
import com.example.autolog.ui.rememberClipThumbnail
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraHighlight
import com.example.autolog.ui.theme.Glass
import com.example.autolog.ui.theme.ListDimens
import com.example.autolog.ui.theme.GlassBorder
import androidx.compose.ui.text.style.TextAlign
import com.example.autolog.ui.theme.Spacing

const val TAG_POSITION = "preview-position"
const val TAG_PREVIEW_EDIT = "preview-edit"

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

            PreviewUiState.Empty -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlowIcon(icon = R.drawable.ic_movie)
                Text(
                    text = stringResource(R.string.preview_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CameraControlTint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(Spacing.xl),
                )
            }

            is PreviewUiState.Ready -> ClipPager(
                clips = state.clips,
                startIndex = state.startIndex,
                onBack = onBack,
                onEdit = onEdit,
            )
        }

        if (uiState !is PreviewUiState.Ready) {
            PreviewTopBar(onBack = onBack, modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding())
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
    onBack: () -> Unit,
    onEdit: (clipId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 클립 끝에서 멈추지 않고 재생목록이 스스로 다음 클립으로 넘어가게 둔다 — 플레이어가 다음 클립을 미리 읽어
    // 두므로 끊김 없이 이어진다. 페이지는 그 뒤를 따라간다.
    val player = remember { ExoPlayer.Builder(context).build() }

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

    // 화면을 벗어나면 소리부터 멈추고, 돌아오면 이어서 튼다 — 편집에서 돌아와도 바로 이어 본다.
    LifecycleResumeEffect(player) {
        player.play()
        onPauseOrDispose { player.pause() }
    }

    val pagerState = rememberPagerState(initialPage = startIndex) { clips.size }
    val scope = rememberCoroutineScope()

    // 인스타 스토리처럼 한 클립이 끝나면 다음 클립으로 넘어가 하루치를 처음부터 끝까지 이어 본다.
    // 페이지는 밀어 넘기는 애니메이션 없이 곧장 따라간다. 마지막 클립에서는 멈춘 채 남는다.
    DisposableEffect(player, pagerState) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                scope.launch { pagerState.scrollToPage(player.currentMediaItemIndex) }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 손가락으로 밀어 넘기는 중인지. 그동안만 영상 층을 숨기고 아래 페이저의 썸네일이 밀려 보이게 한다.
    var swiping by remember { mutableStateOf(false) }
    var swipeStartPage by remember { mutableIntStateOf(startIndex) }
    val flingThreshold = with(LocalDensity.current) { FLING_VELOCITY.toPx() }

    // 손을 뗀 뒤 자리가 확정된 페이지만 따라간다 — 끄는 도중마다 옮기면 지나치는 클립이 잠깐씩 재생된다.
    // 끌기는 페이저에 직접 거리를 밀어 넣어서 끄는 중에도 settledPage가 바뀐다. 그래서 끄는 동안은 따라가지 않는다.
    LaunchedEffect(pagerState, player) {
        snapshotFlow { pagerState.settledPage.takeUnless { swiping } }.filterNotNull().collect { page ->
            if (player.currentMediaItemIndex != page) {
                player.seekTo(page, C.TIME_UNSET)
                player.play()
            }
        }
    }

    // 위아래 바를 영상 위에 겹치지 않고 따로 둔다 — 밝은 장면에서 흰 글자가 묻힌다.
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        StoryProgress(
            page = pagerState.currentPage,
            total = clips.size,
            player = player,
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm),
        )
        // 넘기다 보면 하루치 중 어디쯤인지 잃는다. 목록에서 본 번호와 같은 값을 가운데에 둔다.
        PreviewTopBar(
            onBack = onBack,
            position = { PagePosition(page = pagerState.currentPage, total = clips.size) },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            // 페이저는 넘기는 동안 보이는 썸네일 띠일 뿐이다. 끌기는 위 영상 층이 받아 페이저로 넘긴다.
            HorizontalPager(
                state = pagerState,
                pageSpacing = Spacing.sm,
                contentPadding = PaddingValues(horizontal = Spacing.sm),
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                // 영상을 둥근 카드에 담아 넘길 때 이웃 클립과의 경계가 보이게 한다.
                Box(Modifier.fillMaxSize().videoCard()) {
                    NeighbourClip(clip = clips[page])
                }
            }

            // 영상 표면은 페이지마다 새로 만들지 않고 이 층 하나만 쓴다 — 새로 만들면 첫 프레임이 뜰 때까지
            // 검은 화면이 끼어 클립이 바뀔 때마다 끊겨 보인다. 넘기는 동안만 투명해진다.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.sm)
                    .graphicsLayer { alpha = if (swiping) 0f else 1f }
                    .videoCard()
                    .background(CameraBackground)
                    .draggable(
                        state = rememberDraggableState { delta -> pagerState.dispatchRawDelta(-delta) },
                        orientation = Orientation.Horizontal,
                        onDragStarted = {
                            swipeStartPage = pagerState.currentPage
                            swiping = true
                        },
                        // 튕긴 방향은 끌기를 시작한 페이지에서 센다 — 끄는 도중 이미 넘어간 페이지에서 세면 두 칸을 간다.
                        onDragStopped = { velocity ->
                            val target = when {
                                velocity < -flingThreshold -> swipeStartPage + 1
                                velocity > flingThreshold -> swipeStartPage - 1
                                else -> pagerState.currentPage
                            }.coerceIn(clips.indices)
                            pagerState.animateScrollToPage(target)
                            swiping = false
                        },
                    ),
            ) {
                VideoSurface(player = player)
                // 양옆을 누르면 이전·다음 클립, 가운데를 누르면 재생·일시정지(VideoSurface가 받는다).
                val page = pagerState.settledPage
                TapZones(
                    onPrevious = {
                        scope.launch {
                            if (page > 0) pagerState.scrollToPage(page - 1) else player.seekTo(0)
                            player.play()
                        }
                    },
                    onNext = {
                        if (page < clips.lastIndex) scope.launch { pagerState.scrollToPage(page + 1) }
                    },
                )
            }
        }

        ClipInfoBar(
            clip = clips[pagerState.currentPage],
            onEdit = { onEdit(clips[pagerState.currentPage].id) },
            modifier = Modifier.padding(bottom = Spacing.md),
        )
    }
}

@Composable
private fun PreviewTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    position: @Composable () -> Unit = {},
) {
    ScreenTopBar(
        modifier = modifier,
        navigation = {
            GlassIconButton(
                icon = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.preview_back),
                onClick = onBack,
                tint = CameraControlTint,
            )
        },
        title = position,
    )
}

/**
 * 지금 보는 클립이 언제 찍은 것인지와 할 수 있는 일(편집)을 아래에 모은다.
 * 편집은 이 화면에서 사실상 유일한 다음 동작이라 엄지가 닿는 자리에 글자가 붙은 버튼으로 둔다.
 */
@Composable
private fun ClipInfoBar(clip: Clip, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.md, top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatClipTime(clip.endedAt),
                    style = MaterialTheme.typography.titleLarge,
                    color = CameraControlTint,
                )
                Text(
                    text = formatClipDuration(clip.playedDurationMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = CameraControlTint.copy(alpha = 0.7f),
                )
            }
            ActionButton(
                text = stringResource(R.string.preview_edit),
                icon = R.drawable.ic_cut,
                onClick = onEdit,
                modifier = Modifier.height(ListDimens.glassButton + Spacing.xs).testTag(TAG_PREVIEW_EDIT),
            )
        }
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
            .background(Glass)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs + 2.dp)
            .testTag(TAG_POSITION),
    )
}

/**
 * 인스타 스토리식 진행 막대. 클립마다 한 칸이고, 지나간 칸은 차 있고 지금 칸은 재생 위치만큼 찬다.
 * 몇 개 중 몇 번째인지와 이 클립이 얼마나 남았는지를 한 줄로 읽는다.
 */
@Composable
private fun StoryProgress(page: Int, total: Int, player: Player, modifier: Modifier = Modifier) {
    val position = rememberPlaybackPosition(player)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp),
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(CameraControlTint.copy(alpha = 0.25f))
                    .drawWithContent {
                        val fraction = when {
                            index < page -> 1f
                            index > page -> 0f
                            else -> {
                                val duration = player.duration
                                if (duration > 0) (position.value.toFloat() / duration).coerceIn(0f, 1f) else 0f
                            }
                        }
                        drawRect(CameraHighlight, size = size.copy(width = size.width * fraction))
                    },
            )
        }
    }
}

/** 화면 양쪽 가장자리의 누름 영역. 가운데는 비워 두어 아래 영상 표면이 재생·일시정지를 받는다. */
@Composable
private fun TapZones(onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        TapZone(onClick = onPrevious, label = stringResource(R.string.preview_previous), modifier = Modifier.weight(EDGE_WEIGHT))
        Spacer(Modifier.weight(1f - 2 * EDGE_WEIGHT))
        TapZone(onClick = onNext, label = stringResource(R.string.preview_next), modifier = Modifier.weight(EDGE_WEIGHT))
    }
}

@Composable
private fun TapZone(onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = label,
                onClick = onClick,
            ),
    )
}

/** 둥근 영상 카드. 페이저의 썸네일 칸과 위 영상 층이 같은 모양이라 넘길 때 경계가 맞물린다. */
@Composable
private fun Modifier.videoCard(): Modifier {
    val shape = MaterialTheme.shapes.extraLarge
    return clip(shape).border(1.dp, GlassBorder, shape)
}

/** 이보다 빠르게 튕기면 절반을 못 넘겨도 옆 클립으로 넘어간다. */
private val FLING_VELOCITY = 400.dp

/** 양옆 누름 영역의 폭. 인스타 스토리처럼 가장자리 3분의 1씩이다. */
private const val EDGE_WEIGHT = 0.3f

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
