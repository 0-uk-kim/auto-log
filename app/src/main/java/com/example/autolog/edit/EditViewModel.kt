package com.example.autolog.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.autolog.data.clip.ClipRepository
import com.example.autolog.data.subtitle.Subtitle
import com.example.autolog.data.subtitle.SubtitleRepository
import com.example.autolog.navigation.Edit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EditViewModel @Inject constructor(
    private val clipRepository: ClipRepository,
    private val subtitleRepository: SubtitleRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Edit>()
    val date: LocalDate = LocalDate.parse(route.date)

    private val _uiState = MutableStateFlow<EditUiState>(EditUiState.Loading)
    val uiState = _uiState.asStateFlow()

    val subtitles: StateFlow<List<Subtitle>> = subtitleRepository.observe(route.clipId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 입력하던 자막은 프로세스 종료를 넘겨 살려 둔다 (#33과 같은 기준).
    private val _draft = MutableStateFlow(restoreDraft())
    val draft: StateFlow<SubtitleDraft?> = _draft.asStateFlow()

    init {
        viewModelScope.launch {
            val clip = clipRepository.clipsOn(date).firstOrNull { it.id == route.clipId }
            _uiState.value = clip?.let(EditUiState::Ready) ?: EditUiState.Missing
        }
    }

    fun startNew(positionMs: Long, durationMs: Long) = setDraft(SubtitleDraft.startingAt(positionMs, durationMs))

    fun edit(subtitle: Subtitle) = setDraft(SubtitleDraft.of(subtitle))

    fun markStart(positionMs: Long, durationMs: Long) = updateDraft { it.withStart(positionMs, durationMs) }

    fun markEnd(positionMs: Long, durationMs: Long) = updateDraft { it.withEnd(positionMs, durationMs) }

    fun setText(text: String) = updateDraft { it.copy(text = text) }

    fun cancel() = setDraft(null)

    fun save() {
        val current = draft.value?.takeIf { it.canSave } ?: return
        setDraft(null)
        viewModelScope.launch { subtitleRepository.save(current.toSubtitle(route.clipId)) }
    }

    fun delete() {
        val current = draft.value ?: return
        setDraft(null)
        if (current.id == 0L) return
        viewModelScope.launch { subtitleRepository.delete(current.toSubtitle(route.clipId)) }
    }

    private fun updateDraft(transform: (SubtitleDraft) -> SubtitleDraft) {
        draft.value?.let { setDraft(transform(it)) }
    }

    private fun setDraft(value: SubtitleDraft?) {
        _draft.value = value
        savedStateHandle[KEY_DRAFT_TIMES] = value?.let { longArrayOf(it.id, it.startMs, it.endMs) }
        savedStateHandle[KEY_DRAFT_TEXT] = value?.text
    }

    private fun restoreDraft(): SubtitleDraft? {
        val times = savedStateHandle.get<LongArray>(KEY_DRAFT_TIMES) ?: return null
        return SubtitleDraft(times[0], times[1], times[2], savedStateHandle.get<String>(KEY_DRAFT_TEXT).orEmpty())
    }

    private companion object {
        const val KEY_DRAFT_TIMES = "subtitleDraftTimes"
        const val KEY_DRAFT_TEXT = "subtitleDraftText"
    }
}
