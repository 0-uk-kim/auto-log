package com.example.autolog.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.autolog.data.clip.ClipRepository
import com.example.autolog.data.subtitle.Subtitle
import com.example.autolog.data.subtitle.SubtitleRepository
import com.example.autolog.navigation.Preview
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val clipRepository: ClipRepository,
    subtitleRepository: SubtitleRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Preview>()
    val date: LocalDate = LocalDate.parse(route.date)

    private val _uiState = MutableStateFlow<PreviewUiState>(PreviewUiState.Loading)
    val uiState = _uiState.asStateFlow()

    /** 클립 id → 자막. 편집 화면에서 고치고 돌아오면 바로 바뀐다. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val subtitles: StateFlow<Map<Long, List<Subtitle>>> = _uiState
        .map { state -> (state as? PreviewUiState.Ready)?.clips?.map { it.id }.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { ids -> if (ids.isEmpty()) flowOf(emptyMap()) else subtitleRepository.observe(ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
                    startIndex = clips.startIndexFor(route.clipIndex),
                )
            }
        }
    }
}
