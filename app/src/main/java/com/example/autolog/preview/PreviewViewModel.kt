package com.example.autolog.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.autolog.data.clip.ClipRepository
import com.example.autolog.navigation.Preview
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val clipRepository: ClipRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Preview>()
    val date: LocalDate = LocalDate.parse(route.date)

    private val _uiState = MutableStateFlow<PreviewUiState>(PreviewUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    /** 목록과 같은 순서로 읽는다 — 재생 순서가 곧 사용자가 정한 순서다 (#16). */
    private fun load() {
        viewModelScope.launch {
            val clips = clipRepository.clipsOn(date)
            _uiState.value = if (clips.isEmpty()) {
                PreviewUiState.Empty
            } else {
                PreviewUiState.Ready(
                    clips = clips,
                    startIndex = route.clipIndex.resolveStartIndex(clips.size),
                )
            }
        }
    }
}
