package com.example.autolog.list

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.autolog.data.clip.CalendarMarks
import com.example.autolog.data.clip.Clip
import com.example.autolog.data.clip.ClipRepository
import com.example.autolog.navigation.ClipList
import com.example.autolog.permission.MediaAccess
import com.example.autolog.permission.MediaAccessProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ClipListViewModel @Inject constructor(
    private val clipRepository: ClipRepository,
    private val mediaAccessProvider: MediaAccessProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val date: LocalDate = LocalDate.parse(savedStateHandle.toRoute<ClipList>().date)

    private val _uiState = MutableStateFlow<ClipListUiState>(ClipListUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _calendarMarks = MutableStateFlow(CalendarMarks())
    val calendarMarks = _calendarMarks.asStateFlow()

    /** 삭제 모드에서 체크한 클립. null이면 삭제 모드가 아니다 (#38). */
    private val _selection = MutableStateFlow<Set<Long>?>(null)
    val selection = _selection.asStateFlow()

    /**
     * 시스템 삭제 창에 올라가 있는 클립. 창이 닫히기 전까지 밀어 둔 줄을 제자리로 돌리지 않는다 —
     * 비워지는 순간 거절된 줄은 원위치하고, 승인된 줄은 목록에서 빠진다.
     */
    private val _pendingDeletion = MutableStateFlow<List<Clip>>(emptyList())
    val pendingDeletion = _pendingDeletion.asStateFlow()

    init {
        refresh()
    }

    /**
     * 앱 밖에서 클립이 지워지거나 설정에서 권한이 바뀔 수 있으므로 화면에 들어올 때마다 다시 읽는다.
     *
     * 권한이 없어도 조회는 그대로 한다 — 이번 설치에서 직접 찍은 클립은 앱 소유 항목이라
     * 권한 없이도 읽히기 때문에, [MediaAccess.Denied]라고 건너뛰면 보이는 것까지 숨기게 된다.
     */
    fun refresh() {
        viewModelScope.launch {
            val access = mediaAccessProvider.current()
            val clips = clipRepository.clipsOn(date)
            _uiState.value = if (clips.isEmpty()) {
                ClipListUiState.Empty(access)
            } else {
                ClipListUiState.Clips(clips, access)
            }
            _calendarMarks.value = clipRepository.calendarMarks()
        }
    }

    /**
     * 끄는 동안의 자리 바꿈. 화면에만 반영하고 저장하지 않는다 —
     * 한 칸 지날 때마다 쓰면 드래그 한 번에 Room 쓰기가 수십 번 일어난다 (#16).
     */
    fun moveClip(from: Int, to: Int) {
        val current = _uiState.value as? ClipListUiState.Clips ?: return
        _uiState.value = current.copy(clips = current.clips.moved(from, to))
    }

    /** 손을 뗀 순간의 순서를 그날 것으로 확정한다. 이 순서가 곧 브이로그의 재생 순서다 (planning 3-3). */
    fun persistOrder() {
        val current = _uiState.value as? ClipListUiState.Clips ?: return
        viewModelScope.launch { clipRepository.saveOrder(date, current.clips) }
    }

    fun startSelection() {
        _selection.value = emptySet()
    }

    fun endSelection() {
        _selection.value = null
    }

    fun toggleSelection(clipId: Long) {
        _selection.update { selected ->
            selected?.let { if (clipId in it) it - clipId else it + clipId }
        }
    }

    /** 체크한 클립을 목록 순서대로 삭제 창에 올린다. */
    fun deleteSelected(): IntentSender? {
        val selected = _selection.value ?: return null
        val clips = (_uiState.value as? ClipListUiState.Clips)?.clips.orEmpty()
        return requestDeletion(clips.filter { it.id in selected })
    }

    fun requestDeletion(clips: List<Clip>): IntentSender? {
        if (clips.isEmpty()) return null
        _pendingDeletion.value = clips
        return clipRepository.deleteRequest(clips)
    }

    /** 시스템 삭제 창의 결과. 거절하면 아무것도 지워지지 않았으므로 체크 상태도 그대로 둔다. */
    fun onDeletionResult(approved: Boolean) {
        val deleted = _pendingDeletion.value
        if (approved && deleted.isNotEmpty()) {
            val deletedIds = deleted.mapTo(mutableSetOf()) { it.id }
            _uiState.update { it.withoutClips(deletedIds) }
            _selection.value = null
            viewModelScope.launch {
                clipRepository.forgetDeleted(deleted)
                // 날짜의 마지막 클립이면 달력 표시도 사라져야 한다.
                _calendarMarks.value = clipRepository.calendarMarks()
            }
        }
        _pendingDeletion.value = emptyList()
    }
}
