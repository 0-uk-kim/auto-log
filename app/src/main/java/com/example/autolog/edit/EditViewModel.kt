package com.example.autolog.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.autolog.data.clip.ClipRepository
import com.example.autolog.data.clip.ClipSegment
import com.example.autolog.data.clip.ClipSegments
import com.example.autolog.navigation.Edit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class EditViewModel @Inject constructor(
    private val clipRepository: ClipRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Edit>()

    private val _uiState = MutableStateFlow<EditUiState>(EditUiState.Loading)
    val uiState = _uiState.asStateFlow()

    /**
     * 되돌리기용 이전 상태들. 손잡이 끌기는 한 번 끄는 것 전체가 한 단계다 — 움직일 때마다 쌓으면
     * 되돌리기를 수십 번 눌러야 한다. 화면 회전을 넘길 만큼 중요하지 않아 저장하지 않는다.
     */
    private val history = ArrayDeque<Pair<ClipSegments, Int>>()

    /** 저장이 끝나면 한 번 흘린다 — 화면은 그때 닫는다. 저장 전에 닫으면 미리보기가 옛 조각을 읽는다. */
    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved = _saved.receiveAsFlow()

    init {
        viewModelScope.launch {
            val clip = clipRepository.clipsOn(LocalDate.parse(route.date)).firstOrNull { it.id == route.clipId }
            _uiState.value = if (clip == null) {
                EditUiState.Missing
            } else {
                // 편집 중이던 조각은 프로세스 종료를 넘겨 살려 둔다 (#33과 같은 기준).
                val restored = savedStateHandle.get<LongArray>(KEY_SEGMENTS)?.let { flat ->
                    ClipSegments.of(flat.toList().chunked(2) { ClipSegment(it[0], it[1]) }, clip.durationMs)
                }
                val segments = restored ?: clip.segments ?: ClipSegments.whole(clip.durationMs)
                val selected = savedStateHandle.get<Int>(KEY_SELECTED)?.coerceIn(segments.items.indices) ?: 0
                EditUiState.Ready(clip, segments, selected)
            }
        }
    }

    /** 손잡이를 잡을 때 한 번 부른다 — 끄는 동안의 움직임을 되돌리기 한 단계로 묶는다. */
    fun beginHandleDrag() {
        val state = _uiState.value as? EditUiState.Ready ?: return
        remember(state)
        _uiState.value = state.copy(canUndo = true)
    }

    fun undo() {
        val (segments, selected) = history.removeLastOrNull() ?: return
        update(record = false) { it.copy(segments = segments, selected = selected) }
    }

    /** 옮긴 뒤의 시작 시각을 돌려준다 — 화면이 다시 그려지기 전에 플레이어를 손잡이 자리로 보내야 해서다. */
    fun moveStart(positionMs: Long): Long? = update(record = false) { state ->
        state.copy(segments = state.segments.withStart(state.selected, positionMs, state.clip.durationMs))
    }?.let { it.segments.items[it.selected].startMs }

    fun moveEnd(positionMs: Long): Long? = update(record = false) { state ->
        state.copy(segments = state.segments.withEnd(state.selected, positionMs, state.clip.durationMs))
    }?.let { it.segments.items[it.selected].endMs }

    /** 조각 안을 탭하면 그 조각을 고른다. 빈 곳이면 선택은 그대로다. */
    fun select(positionMs: Long) {
        update(record = false) { state ->
            val index = state.segments.indexAt(positionMs)
            if (index < 0) state else state.copy(selected = index)
        }
    }

    /** 재생 위치에서 조각을 둘로 나누고, 뒤쪽 조각을 고른다 — 이어서 그 뒤를 잘라내기 쉽다. */
    fun split(positionMs: Long) {
        val current = _uiState.value as? EditUiState.Ready ?: return
        if (!current.segments.canSplitAt(positionMs, current.clip.durationMs)) return
        update { state ->
            val segments = state.segments.splitAt(positionMs, state.clip.durationMs)
            state.copy(segments = segments, selected = segments.indexAt(positionMs))
        }
    }

    fun removeSelected() {
        if ((_uiState.value as? EditUiState.Ready)?.segments?.canRemove != true) return
        update { state ->
            val segments = state.segments.remove(state.selected)
            state.copy(segments = segments, selected = state.selected.coerceAtMost(segments.items.lastIndex))
        }
    }

    fun reset() {
        update { state -> state.copy(segments = ClipSegments.whole(state.clip.durationMs), selected = 0) }
    }

    fun save() {
        val state = uiState.value as? EditUiState.Ready ?: return
        viewModelScope.launch {
            clipRepository.saveSegments(state.clip, state.segments)
            _saved.send(Unit)
        }
    }

    /** [record]면 바꾸기 전 상태를 되돌리기에 쌓는다. 선택·손잡이 이동처럼 잘게 이어지는 것은 쌓지 않는다. */
    private fun update(
        record: Boolean = true,
        transform: (EditUiState.Ready) -> EditUiState.Ready,
    ): EditUiState.Ready? {
        val state = _uiState.value as? EditUiState.Ready ?: return null
        if (record) remember(state)
        val next = transform(state).copy(canUndo = history.isNotEmpty())
        savedStateHandle[KEY_SEGMENTS] = next.segments.items.flatMap { listOf(it.startMs, it.endMs) }.toLongArray()
        savedStateHandle[KEY_SELECTED] = next.selected
        _uiState.value = next
        return next
    }

    private fun remember(state: EditUiState.Ready) {
        history.addLast(state.segments to state.selected)
        if (history.size > MAX_UNDO) history.removeFirst()
    }

    private companion object {
        const val MAX_UNDO = 50
        const val KEY_SEGMENTS = "segments"
        const val KEY_SELECTED = "selectedSegment"
    }
}
