package com.example.autolog.list

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
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
import com.example.autolog.diagnostics.DiagnosticsMenu
import com.example.autolog.permission.MediaAccess
import com.example.autolog.permission.openAppSettings
import com.example.autolog.permission.rememberMediaAccessRequest
import com.example.autolog.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_CLIP_LIST_SCREEN = "clip-list-screen"
const val TAG_START_SELECTION = "clip-list-start-selection"
const val TAG_DELETE_SELECTED = "clip-list-delete-selected"

/**
 * 하루치 클립을 브이로그에 들어갈 순서대로 보여주는 화면 (planning 3-3).
 * 순서 변경(드래그)은 #16, 편집 여부 배지는 #18, 삭제(밀어서 삭제·삭제 모드)는 #38에서 이 목록 위에 얹힌다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipListScreen(
    date: String,
    onOpenClip: (clipIndex: Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onCreateVlog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClipListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val pendingDeletion by viewModel.pendingDeletion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestAccess = rememberMediaAccessRequest(onResult = viewModel::refresh)
    var showCalendar by rememberSaveable { mutableStateOf(false) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDeletionResult(approved = result.resultCode == Activity.RESULT_OK) }
    val launchDeletion: (IntentSender?) -> Unit = { sender ->
        sender?.let { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    BackHandler(enabled = selection != null, onBack = viewModel::endSelection)

    // 앱 밖 삭제와 설정에서의 권한 변경은 콜백 없이 일어난다. 복귀할 때마다 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        modifier = modifier.testTag(TAG_CLIP_LIST_SCREEN),
        topBar = {
            val selected = selection
            if (selected != null) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    onClose = viewModel::endSelection,
                    onDelete = { launchDeletion(viewModel.deleteSelected()) },
                )
            } else {
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
                        if (uiState is ClipListUiState.Clips) {
                            IconButton(
                                onClick = viewModel::startSelection,
                                modifier = Modifier.testTag(TAG_START_SELECTION),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = stringResource(R.string.clip_list_start_selection),
                                )
                            }
                        }
                        IconButton(onClick = { showCalendar = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = stringResource(R.string.clip_list_open_calendar),
                            )
                        }
                        DiagnosticsMenu()
                    },
                )
            }
        },
        floatingActionButton = {
            // 이어붙일 것이 있을 때만 생성 버튼을 둔다 — 0건에서 누르면 할 수 있는 일이 없다.
            // 삭제 모드에서는 고르는 중인 목록으로 브이로그를 만들 일이 없어 숨긴다.
            if (uiState is ClipListUiState.Clips && selection == null) {
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
                selection = selection,
                pendingDeletionIds = pendingDeletion.mapTo(mutableSetOf()) { it.id },
                onOpenClip = onOpenClip,
                onToggleClip = viewModel::toggleSelection,
                onSwipeDelete = { clip -> launchDeletion(viewModel.requestDeletion(listOf(clip))) },
                onMoveClip = viewModel::moveClip,
                onOrderSettled = viewModel::persistOrder,
                contentPadding = padding,
            )
        }
    }

    if (showCalendar) {
        val marks by viewModel.calendarMarks.collectAsStateWithLifecycle()
        CalendarSheet(
            viewedDate = viewModel.date,
            marks = marks,
            onSelectDate = { selected ->
                showCalendar = false
                if (selected != viewModel.date) onSelectDate(selected)
            },
            onDismiss = { showCalendar = false },
        )
    }
}

@Composable
private fun ClipList(
    clips: List<Clip>,
    access: MediaAccess,
    onRequestAccess: () -> Unit,
    selection: Set<Long>?,
    pendingDeletionIds: Set<Long>,
    onOpenClip: (Int) -> Unit,
    onToggleClip: (clipId: Long) -> Unit,
    onSwipeDelete: (Clip) -> Unit,
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

                val swipeState = rememberSwipeToDismissBoxState()
                val isPendingDeletion = clip.id in pendingDeletionIds
                // 삭제 창에서 거절하면 밀어 둔 줄을 제자리로 돌린다. 승인되면 줄 자체가 목록에서 빠진다.
                LaunchedEffect(isPendingDeletion) {
                    if (!isPendingDeletion) swipeState.reset()
                }

                SwipeToDismissBox(
                    state = swipeState,
                    backgroundContent = { SwipeDeleteBackground(swipeState) },
                    enableDismissFromStartToEnd = false,
                    gesturesEnabled = selection == null,
                    onDismiss = { onSwipeDelete(clip) },
                    modifier = if (isDragging) {
                        // 끌고 있는 줄은 다른 줄 위로 떠야 하고, 자리 이동 애니메이션을 타면 안 된다.
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragDropState.draggingItemOffset }
                    } else {
                        Modifier.animateItem()
                    },
                ) {
                    ClipRow(
                        clip = clip,
                        position = position,
                        isDragging = isDragging,
                        selected = selection?.let { clip.id in it },
                        onClick = {
                            if (selection != null) onToggleClip(clip.id) else onOpenClip(position)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.clip_list_selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.clip_list_end_selection),
                )
            }
        },
        actions = {
            IconButton(
                onClick = onDelete,
                enabled = selectedCount > 0,
                modifier = Modifier.testTag(TAG_DELETE_SELECTED),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.clip_list_delete_selected),
                )
            }
        },
    )
}

/** 줄을 미는 동안 뒤에 드러나는 삭제 표시. 방향이 정해지기 전(제자리)에는 비워 둔다. */
@Composable
private fun SwipeDeleteBackground(state: SwipeToDismissBoxState) {
    if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.clip_list_swipe_delete),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun rememberDateTitle(date: String): String {
    val pattern = stringResource(R.string.clip_list_date_pattern)
    return remember(date, pattern) {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
}
