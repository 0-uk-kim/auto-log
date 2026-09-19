package com.example.autolog.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autolog.R
import com.example.autolog.calendar.CalendarSheet
import com.example.autolog.data.clip.Clip
import com.example.autolog.permission.MediaAccess
import com.example.autolog.permission.openAppSettings
import com.example.autolog.permission.rememberMediaAccessRequest
import com.example.autolog.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_CLIP_LIST_SCREEN = "clip-list-screen"

/**
 * 하루치 클립을 브이로그에 들어갈 순서대로 보여주는 화면 (planning 3-3).
 * 순서 변경(드래그)은 #16, 편집 여부 배지는 #18에서 이 목록 위에 얹힌다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipListScreen(
    date: String,
    onOpenClip: (clipIndex: Int) -> Unit,
    onCreateVlog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClipListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestAccess = rememberMediaAccessRequest(onResult = viewModel::refresh)
    var showCalendar by rememberSaveable { mutableStateOf(false) }

    // 앱 밖 삭제와 설정에서의 권한 변경은 콜백 없이 일어난다. 복귀할 때마다 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        modifier = modifier.testTag(TAG_CLIP_LIST_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(rememberDateTitle(date)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.clip_list_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCalendar = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar),
                            contentDescription = stringResource(R.string.clip_list_open_calendar),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // 이어붙일 것이 있을 때만 생성 버튼을 둔다 — 0건에서 누르면 할 수 있는 일이 없다.
            if (uiState is ClipListUiState.Clips) {
                ExtendedFloatingActionButton(onClick = onCreateVlog) {
                    Text(stringResource(R.string.clip_list_create_vlog))
                }
            }
        },
    ) { padding ->
        when (val state = uiState) {
            ClipListUiState.Loading -> ClipListLoading(Modifier.padding(padding))

            is ClipListUiState.Empty -> ClipListEmpty(
                access = state.access,
                onRequestAccess = requestAccess,
                onOpenSettings = { context.openAppSettings() },
                modifier = Modifier.padding(padding),
            )

            is ClipListUiState.Clips -> ClipList(
                clips = state.clips,
                access = state.access,
                onRequestAccess = requestAccess,
                onOpenClip = onOpenClip,
                onMoveClip = viewModel::moveClip,
                onOrderSettled = viewModel::persistOrder,
                contentPadding = padding,
            )
        }
    }

    if (showCalendar) {
        CalendarSheet(viewedDate = viewModel.date, onDismiss = { showCalendar = false })
    }
}

@Composable
private fun ClipList(
    clips: List<Clip>,
    access: MediaAccess,
    onRequestAccess: () -> Unit,
    onOpenClip: (Int) -> Unit,
    onMoveClip: (from: Int, to: Int) -> Unit,
    onOrderSettled: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropState(
        lazyListState = lazyListState,
        onMove = onMoveClip,
        onDrop = onOrderSettled,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        MediaAccessBanner(access = access, onRequestAccess = onRequestAccess)

        Text(
            text = stringResource(
                R.string.clip_list_summary,
                clips.size,
                formatClipDuration(clips.sumOf { it.durationMs }),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )

        LazyColumn(
            state = lazyListState,
            // FAB이 마지막 줄을 가리지 않게 아래를 비워 둔다.
            contentPadding = PaddingValues(bottom = Spacing.xl * 2),
        ) {
            itemsIndexed(clips, key = { _, clip -> clip.id }) { position, clip ->
                val isDragging = position == dragDropState.draggingItemIndex
                // 자리가 바뀌어도 제스처가 끊기지 않게 pointerInput은 clip.id로만 묶고,
                // 시작 위치는 항상 최신 값을 읽는다.
                val currentPosition by rememberUpdatedState(position)

                ClipRow(
                    clip = clip,
                    position = position,
                    isDragging = isDragging,
                    onClick = { onOpenClip(position) },
                    modifier = if (isDragging) {
                        // 끌고 있는 줄은 다른 줄 위로 떠야 하고, 자리 이동 애니메이션을 타면 안 된다.
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragDropState.draggingItemOffset }
                    } else {
                        Modifier.animateItem()
                    },
                    dragHandleModifier = Modifier.pointerInput(clip.id) {
                        detectDragGestures(
                            onDragStart = { dragDropState.onDragStart(currentPosition) },
                            onDragEnd = dragDropState::onDragInterrupted,
                            onDragCancel = dragDropState::onDragInterrupted,
                            onDrag = { change, offset ->
                                change.consume()
                                dragDropState.onDrag(offset)
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun rememberDateTitle(date: String): String {
    val pattern = stringResource(R.string.clip_list_date_pattern)
    return remember(date, pattern) {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
}
