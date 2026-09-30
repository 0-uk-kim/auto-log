package com.example.autolog.list

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.example.autolog.ui.UndoDeletionEffect
import com.example.autolog.ui.theme.ListDimens
import com.example.autolog.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_CLIP_LIST_SCREEN = "clip-list-screen"
const val TAG_START_SELECTION = "clip-list-start-selection"
const val TAG_DELETE_SELECTED = "clip-list-delete-selected"
const val TAG_SELECT_ALL = "clip-list-select-all"
const val TAG_OPEN_CALENDAR = "clip-list-open-calendar"

/**
 * 하루치 클립을 브이로그에 들어갈 순서대로 보여주는 화면 (planning 3-3).
 * 순서 변경(드래그)은 #16, 편집 여부 배지는 #18, 삭제(밀어서 삭제·삭제 모드)는 #38에서 이 목록 위에 얹힌다.
 * 삭제는 묻지 않고 바로 빼고 실행취소 스낵바로 되돌린다. 줄을 길게 누르면 선택 모드로 들어간다 (#102).
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
    val awaitingConfirmation by viewModel.awaitingConfirmation.collectAsStateWithLifecycle()
    val awaitingUndo by viewModel.awaitingUndo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestAccess = rememberMediaAccessRequest(onResult = viewModel::refresh)
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDeletionResult(approved = result.resultCode == Activity.RESULT_OK) }
    LaunchedEffect(viewModel) {
        viewModel.deleteRequests.collect { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    UndoDeletionEffect(
        pending = awaitingUndo,
        hostState = snackbarHostState,
        onUndo = viewModel::undoDeletion,
        onCommit = viewModel::commitDeletion,
    )

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
                    allSelected = selected.size == (uiState as? ClipListUiState.Clips)?.clips?.size,
                    onClose = viewModel::endSelection,
                    onToggleAll = viewModel::toggleSelectAll,
                    onDelete = viewModel::deleteSelected,
                )
            } else {
                TopAppBar(
                    title = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
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
                        DiagnosticsMenu()
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // 이어붙일 것이 있을 때만 생성 버튼을 둔다 — 0건에서 누르면 할 수 있는 일이 없다.
            // 삭제 모드에서는 고르는 중인 목록으로 브이로그를 만들 일이 없어 숨긴다.
            if (uiState is ClipListUiState.Clips && selection == null) {
                Button(
                    onClick = onCreateVlog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs)
                        .height(ListDimens.primaryButton),
                ) {
                    Text(stringResource(R.string.clip_list_create_vlog), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            DateHeader(
                title = rememberDateTitle(date),
                summary = (uiState as? ClipListUiState.Clips)?.clips?.let { clips ->
                    stringResource(
                        R.string.clip_list_summary,
                        clips.size,
                        formatClipDuration(clips.sumOf { it.playedDurationMs }),
                    )
                },
                // 삭제 모드에서는 날짜를 바꾸면 고르던 것이 사라진다.
                onOpenCalendar = { showCalendar = true }.takeIf { selection == null },
            )
            when (val state = uiState) {
                ClipListUiState.Loading -> ClipListLoading()

                is ClipListUiState.Empty -> ClipListEmpty(
                    access = state.access,
                    onRequestAccess = requestAccess,
                    onOpenSettings = { context.openAppSettings() },
                )

                is ClipListUiState.Clips -> ClipList(
                    clips = state.clips,
                    access = state.access,
                    onRequestAccess = requestAccess,
                    selection = selection,
                    awaitingConfirmationIds = awaitingConfirmation.mapTo(mutableSetOf()) { it.id },
                    awaitingUndoIds = awaitingUndo.mapTo(mutableSetOf()) { it.id },
                    onOpenClip = onOpenClip,
                    onToggleClip = viewModel::toggleSelection,
                    onLongPressClip = viewModel::startSelection,
                    onSwipeDelete = { clip -> viewModel.delete(listOf(clip)) },
                    onMoveClip = viewModel::moveClip,
                    onOrderSettled = viewModel::persistOrder,
                )
            }
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
    awaitingConfirmationIds: Set<Long>,
    awaitingUndoIds: Set<Long>,
    onOpenClip: (Int) -> Unit,
    onToggleClip: (clipId: Long) -> Unit,
    onLongPressClip: (clipId: Long) -> Unit,
    onSwipeDelete: (Clip) -> Unit,
    onMoveClip: (from: Int, to: Int) -> Unit,
    onOrderSettled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropState(
        lazyListState = lazyListState,
        onMove = onMoveClip,
        onDrop = onOrderSettled,
    )

    Column(modifier = modifier.fillMaxSize()) {
        MediaAccessBanner(access = access, onRequestAccess = onRequestAccess)

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(vertical = Spacing.xs),
        ) {
            itemsIndexed(clips, key = { _, clip -> clip.id }) { position, clip ->
                val isDragging = position == dragDropState.draggingItemIndex
                // 자리가 바뀌어도 제스처가 끊기지 않게 pointerInput은 clip.id로만 묶고,
                // 시작 위치는 항상 최신 값을 읽는다.
                val currentPosition by rememberUpdatedState(position)

                // rememberSwipeToDismissBoxState는 저장되는 상태라, 실행취소로 같은 key의 줄이 돌아오면 밀린 채로
                // 복원돼 곧바로 다시 지워진다. 저장하지 않는 상태를 쓴다 (#102).
                val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
                val swipeState = remember(clip.id) {
                    SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold)
                }
                val isHeld = clip.id in awaitingConfirmationIds || clip.id in awaitingUndoIds
                // 삭제 창에서 거절하거나, 사라지는 도중에 실행취소하면 밀어 둔 줄을 제자리로 돌린다.
                LaunchedEffect(isHeld) {
                    if (!isHeld) swipeState.reset()
                }
                // SwipeToDismissBox는 이 콜백이 바뀔 때마다 밀린 상태를 다시 보고 부른다 — 다시 그릴 때마다 새
                // 람다를 넘기면 실행취소한 줄이 곧바로 또 지워진다.
                val swipeDelete by rememberUpdatedState(onSwipeDelete)
                val onDismiss = remember(clip.id) { { _: SwipeToDismissBoxValue -> swipeDelete(clip) } }

                SwipeToDismissBox(
                    state = swipeState,
                    backgroundContent = { SwipeDeleteBackground(swipeState) },
                    enableDismissFromStartToEnd = false,
                    gesturesEnabled = selection == null,
                    onDismiss = onDismiss,
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
                        onLongClick = if (selection == null) ({ onLongPressClip(clip.id) }) else null,
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
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.clip_list_selected_count, selectedCount)) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.clip_list_end_selection),
                )
            }
        },
        actions = {
            TextButton(onClick = onToggleAll, modifier = Modifier.testTag(TAG_SELECT_ALL)) {
                Text(stringResource(if (allSelected) R.string.clip_list_deselect_all else R.string.clip_list_select_all))
            }
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
    // 줄이 카드라 뒤판도 같은 여백·모서리로 깔아야 밀었을 때 모서리가 튀어나오지 않는다.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clip(MaterialTheme.shapes.medium)
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

/**
 * 이 목록이 어느 날짜인지 크게 보여 준다. 날짜를 누르면 달력이 열린다 — 아래 화살표가 바꿀 수 있다는 표시다.
 */
@Composable
private fun DateHeader(title: String, summary: String?, onOpenCalendar: (() -> Unit)?) {
    Column(modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (onOpenCalendar != null) {
                        Modifier.clickable(
                            onClickLabel = stringResource(R.string.clip_list_open_calendar),
                            onClick = onOpenCalendar,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = Spacing.xs)
                .testTag(TAG_OPEN_CALENDAR),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            if (onOpenCalendar != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .size(20.dp)
                        .rotate(-90f),
                )
            }
        }
        Text(
            text = summary.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
