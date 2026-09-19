package com.example.autolog.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.autolog.data.clip.CalendarMarks
import com.example.autolog.data.clip.ClipRepository
import com.example.autolog.navigation.ClipList
import com.example.autolog.permission.MediaAccess
import com.example.autolog.permission.MediaAccessProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}
