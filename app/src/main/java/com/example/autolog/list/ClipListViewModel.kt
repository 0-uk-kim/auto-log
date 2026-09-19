package com.example.autolog.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autolog.data.clip.Clip
import com.example.autolog.data.clip.ClipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ClipListViewModel @Inject constructor(
    private val clipRepository: ClipRepository,
) : ViewModel() {

    private val _clipsByDate = MutableStateFlow<Map<LocalDate, List<Clip>>?>(null)
    val clipsByDate = _clipsByDate.asStateFlow()

    init {
        refresh()
    }

    /** 앱 밖에서 클립이 지워질 수 있으므로 화면에 들어올 때마다 다시 읽는다. */
    fun refresh() {
        viewModelScope.launch {
            _clipsByDate.value = clipRepository.clipsByDate()
        }
    }

    /** #16의 드래그가 들어오기 전까지, 순서 저장·복원을 눈으로 확인하는 임시 조작. */
    fun reverseOrder(date: LocalDate) {
        viewModelScope.launch {
            val clips = _clipsByDate.value?.get(date) ?: return@launch
            clipRepository.saveOrder(date, clips.reversed())
            refresh()
        }
    }
}
