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

    /** 옮긴 뒤의 시작 시각을 돌려준다 — 화면이 다시 그려지기 전에 플레이어를 손잡이 자리로 보내야 해서다. */
    fun moveStart(positionMs: Long): Long? = update { state ->
        state.copy(segments = state.segments.withStart(state.selected, positionMs, state.clip.durationMs))
    }?.let { it.segments.items[it.selected].startMs }

    fun moveEnd(positionMs: Long): Long? = update { state ->
        state.copy(segments = state.segments.withEnd(state.selected, positionMs, state.clip.durationMs))
    }?.let { it.segments.items[it.selected].endMs }

    /** 조각 안을 탭하면 그 조각을 고른다. 빈 곳이면 선택은 그대로다. */
    fun select(positionMs: Long) {
        update { state ->
            val index = state.segments.indexAt(positionMs)
            if (index < 0) state else state.copy(selected = index)
        }
    }

    /** 재생 위치에서 조각을 둘로 나누고, 뒤쪽 조각을 고른다 — 이어서 그 뒤를 잘라내기 쉽다. */
    fun split(positionMs: Long) {
        update { state ->
            if (!state.segments.canSplitAt(positionMs, state.clip.durationMs)) return@update state
            val segments = state.segments.splitAt(positionMs, state.clip.durationMs)
            state.copy(segments = segments, selected = segments.indexAt(positionMs))
        }
    }

    fun removeSelected() {
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

    private fun update(transform: (EditUiState.Ready) -> EditUiState.Ready): EditUiState.Ready? {
        val state = _uiState.value as? EditUiState.Ready ?: return null
        val next = transform(state)
        savedStateHandle[KEY_SEGMENTS] = next.segments.items.flatMap { listOf(it.startMs, it.endMs) }.toLongArray()
        savedStateHandle[KEY_SELECTED] = next.selected
        _uiState.value = next
        return next
    }

    private companion object {
        const val KEY_SEGMENTS = "segments"
        const val KEY_SELECTED = "selectedSegment"
    }
}
