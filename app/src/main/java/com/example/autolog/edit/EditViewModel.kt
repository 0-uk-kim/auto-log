package com.example.autolog.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.autolog.data.clip.ClipRepository
import com.example.autolog.data.clip.ClipTrim
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

    /** 저장이 끝나면 한 번 흘린다 — 화면은 그때 닫는다. 저장 전에 닫으면 미리보기가 옛 구간을 읽는다. */
    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved = _saved.receiveAsFlow()

    init {
        viewModelScope.launch {
            val clip = clipRepository.clipsOn(LocalDate.parse(route.date)).firstOrNull { it.id == route.clipId }
            _uiState.value = if (clip == null) {
                EditUiState.Missing
            } else {
                // 끌던 손잡이는 프로세스 종료를 넘겨 살려 둔다 (#33과 같은 기준).
                val restored = savedStateHandle.get<LongArray>(KEY_TRIM)
                    ?.let { ClipTrim.of(it[0], it[1], clip.durationMs) }
                EditUiState.Ready(clip, restored ?: clip.trim ?: ClipTrim.whole(clip.durationMs))
            }
        }
    }

    /** 옮긴 뒤의 구간을 돌려준다 — 화면이 다시 그려지기 전에 플레이어를 손잡이 자리로 보내야 해서다. */
    fun moveStart(positionMs: Long): ClipTrim? = updateTrim { trim, duration -> trim.withStart(positionMs, duration) }

    fun moveEnd(positionMs: Long): ClipTrim? = updateTrim { trim, duration -> trim.withEnd(positionMs, duration) }

    fun reset() {
        updateTrim { _, duration -> ClipTrim.whole(duration) }
    }

    fun save() {
        val state = uiState.value as? EditUiState.Ready ?: return
        viewModelScope.launch {
            clipRepository.saveTrim(state.clip, state.trim)
            _saved.send(Unit)
        }
    }

    private fun updateTrim(transform: (ClipTrim, Long) -> ClipTrim): ClipTrim? {
        val state = _uiState.value as? EditUiState.Ready ?: return null
        val trim = transform(state.trim, state.clip.durationMs)
        savedStateHandle[KEY_TRIM] = longArrayOf(trim.startMs, trim.endMs)
        _uiState.value = state.copy(trim = trim)
        return trim
    }

    private companion object {
        const val KEY_TRIM = "trim"
    }
}
