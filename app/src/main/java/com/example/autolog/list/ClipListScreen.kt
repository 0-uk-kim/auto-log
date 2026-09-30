package com.example.autolog.list

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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
import com.example.autolog.ui.ActionButton
import com.example.autolog.ui.ActionStyle
import com.example.autolog.ui.BottomDock
import com.example.autolog.ui.GlassIconButton
import com.example.autolog.ui.GlassPill
import com.example.autolog.ui.ScreenTopBar
import com.example.autolog.ui.UndoDeletionEffect
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
 * 하루치 클립을 브이로그에 들어갈 순서대로 보여주는 스토리보드 (planning 3-3).
 *
 * 위 띠는 완성될 브이로그의 축소판이고, 아래 격자는 같은 순서의 칸들이다. 칸을 길게 눌러 끌면 순서가
 * 바뀐다(#16). 지우는 것은 「선택」으로 고른 뒤 아래 버튼으로 한다(#38) — 묻지 않고 바로 빼고 실행취소
 * 스낵바로 되돌린다(#102).
 */
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
                )
            } else {
                ScreenTopBar(
                    modifier = Modifier.statusBarsPadding(),
                    // 날짜가 곧 이 화면의 제목이다. 누르면 달력이 열린다 — 따로 달력 버튼을 두지 않는다.
                    navigation = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton(
                                icon = R.drawable.ic_arrow_back,
                                contentDescription = stringResource(R.string.clip_list_back),
                                onClick = onBack,
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            DateTitle(
                                title = rememberDateTitle(date),
                                isToday = viewModel.date == LocalDate.now(),
                                onOpenCalendar = { showCalendar = true },
                            )
                        }
                    },
                    actions = {
                        if (uiState is ClipListUiState.Clips) {
                            GlassPill(
                                text = stringResource(R.string.clip_list_start_selection),
                                onClick = viewModel::startSelection,
                                modifier = Modifier.testTag(TAG_START_SELECTION),
                            )
                        }
                        DiagnosticsMenu()
                    },
                )
            }
        },
        // 아래 버튼이 격자 위에 떠 있어 스낵바가 그 위로 올라서야 한다 — 기본 자리는 버튼에 가린다.
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = if (uiState is ClipListUiState.Clips) DOCK_CLEARANCE else 0.dp),
            )
        },
    ) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            when (val state = uiState) {
                ClipListUiState.Loading -> ClipListLoading()

                is ClipListUiState.Empty -> ClipListEmpty(
                    access = state.access,
                    onRequestAccess = requestAccess,
                    onOpenSettings = { context.openAppSettings() },
                    // 카메라는 늘 오늘로 찍는다. 지난 날짜의 빈 목록에서 권하면 엉뚱한 날에 쌓인다.
                    onShoot = onBack.takeIf { viewModel.date == LocalDate.now() },
                )

                is ClipListUiState.Clips -> Storyboard(
                    clips = state.clips,
                    access = state.access,
                    onRequestAccess = requestAccess,
                    selection = selection,
                    onOpenClip = onOpenClip,
                    onToggleClip = viewModel::toggleSelection,
                    onMoveClip = viewModel::moveClip,
                    onOrderSettled = viewModel::persistOrder,
                )
            }

            // 버튼은 엄지가 닿는 아래 한 자리만 쓴다 — 평소엔 브이로그, 고르는 중엔 삭제.
            // 이어붙일 것이 없으면 둘 다 할 일이 없어 비운다.
            if (uiState is ClipListUiState.Clips) {
                BottomDock(Modifier.align(Alignment.BottomCenter)) {
                    val selected = selection
                    if (selected != null) {
                        ActionButton(
                            text = stringResource(R.string.clip_list_delete_selected, selected.size),
                            icon = R.drawable.ic_delete,
                            onClick = viewModel::deleteSelected,
                            enabled = selected.isNotEmpty(),
                            style = ActionStyle.Destructive,
                            modifier = Modifier.weight(1f).testTag(TAG_DELETE_SELECTED),
                        )
                    } else {
                        val marks by viewModel.calendarMarks.collectAsStateWithLifecycle()
                        // 이미 만든 날은 새로 만드는 게 아니라 보러 가는 것이다 — 들어가면 기존 결과물이 뜬다.
                        val hasVlog = viewModel.date in marks.datesWithVlog
                        ActionButton(
                            text = stringResource(
                                if (hasVlog) R.string.clip_list_open_vlog else R.string.clip_list_create_vlog,
                            ),
                            icon = if (hasVlog) R.drawable.ic_movie else R.drawable.ic_sparkle,
                            onClick = onCreateVlog,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
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
private fun Storyboard(
    clips: List<Clip>,
    access: MediaAccess,
    onRequestAccess: () -> Unit,
    selection: Set<Long>?,
    onOpenClip: (Int) -> Unit,
    onToggleClip: (clipId: Long) -> Unit,
    onMoveClip: (from: Int, to: Int) -> Unit,
    onOrderSettled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val dragDropState = rememberDragDropState(
        gridState = gridState,
        onMove = onMoveClip,
        onDrop = onOrderSettled,
    )
    val haptics = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxSize()) {
        MediaAccessBanner(access = access, onRequestAccess = onRequestAccess)

        VlogTimeline(
            clips = clips,
            onOpenClip = onOpenClip,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.sm),
        ) {
            Text(
                text = stringResource(
                    R.string.clip_list_summary,
                    clips.size,
                    formatClipDuration(clips.sumOf { it.playedDurationMs }),
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    if (selection == null) R.string.clip_list_reorder_hint else R.string.clip_list_select_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            // 마지막 줄이 떠 있는 아래 버튼에 가리지 않도록 그만큼 더 내려 쓴다.
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = DOCK_CLEARANCE),
        ) {
            itemsIndexed(clips, key = { _, clip -> clip.id }) { position, clip ->
                val isDragging = position == dragDropState.draggingItemIndex
                // 자리가 바뀌어도 제스처가 끊기지 않게 pointerInput은 clip.id로만 묶고,
                // 시작 위치는 항상 최신 값을 읽는다.
                val currentPosition by rememberUpdatedState(position)

                ClipTile(
                    clip = clip,
                    position = position,
                    isDragging = isDragging,
                    selected = selection?.let { clip.id in it },
                    modifier = Modifier
                        .then(
                            if (isDragging) {
                                // 끌고 있는 칸은 다른 칸 위로 떠야 하고, 자리 이동 애니메이션을 타면 안 된다.
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationX = dragDropState.draggingItemOffset.x
                                        translationY = dragDropState.draggingItemOffset.y
                                    }
                            } else {
                                Modifier.animateItem()
                            },
                        )
                        .clickable {
                            if (selection != null) onToggleClip(clip.id) else onOpenClip(currentPosition)
                        }
                        // 끌기 감지를 누름보다 안쪽에 둬 이벤트를 먼저 받게 한다 — 끌기가 시작되면 이동을 소비해 누름은 취소된다.
                        .then(
                            if (selection == null) {
                                Modifier.pointerInput(clip.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragDropState.onDragStart(currentPosition)
                                        },
                                        onDragEnd = dragDropState::onDragInterrupted,
                                        onDragCancel = dragDropState::onDragInterrupted,
                                        onDrag = { change, offset ->
                                            change.consume()
                                            dragDropState.onDrag(offset)
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

/** 위 바의 날짜 제목. 오늘이면 위에 작게 「오늘」을 얹는다 — 지난 날을 보고 있을 때 헷갈리지 않는다. */
@Composable
private fun DateTitle(title: String, isToday: Boolean, onOpenCalendar: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(
                onClickLabel = stringResource(R.string.clip_list_open_calendar),
                onClick = onOpenCalendar,
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .testTag(TAG_OPEN_CALENDAR),
    ) {
        Column {
            if (isToday) {
                Text(
                    text = stringResource(R.string.clip_list_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = Spacing.xs)
                .size(18.dp)
                .rotate(-90f),
        )
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
) {
    ScreenTopBar(
        modifier = Modifier.statusBarsPadding(),
        navigation = {
            GlassIconButton(
                icon = R.drawable.ic_close,
                contentDescription = stringResource(R.string.clip_list_end_selection),
                onClick = onClose,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.clip_list_selected_count, selectedCount),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        actions = {
            GlassPill(
                text = stringResource(if (allSelected) R.string.clip_list_deselect_all else R.string.clip_list_select_all),
                onClick = onToggleAll,
                modifier = Modifier.testTag(TAG_SELECT_ALL),
            )
        },
    )
}

private const val GRID_COLUMNS = 3

/** 떠 있는 아래 버튼(56dp)과 그 위아래 여백만큼. 격자 끝과 스낵바가 이만큼 비켜 선다. */
private val DOCK_CLEARANCE = 104.dp

@Composable
private fun rememberDateTitle(date: String): String {
    val pattern = stringResource(R.string.clip_list_date_pattern)
    return remember(date, pattern) {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
}
