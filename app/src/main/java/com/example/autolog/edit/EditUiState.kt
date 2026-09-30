package com.example.autolog.edit

import com.example.autolog.data.clip.Clip
import com.example.autolog.data.clip.ClipSegments

sealed interface EditUiState {

    data object Loading : EditUiState

    /** 미리보기에서 넘어오는 사이에 앱 밖에서 지워졌을 수 있다. */
    data object Missing : EditUiState

    /** [segments]는 아직 저장하지 않은 편집 중인 조각들, [selected]는 손잡이가 붙은 조각이다. */
    data class Ready(
        val clip: Clip,
        val segments: ClipSegments,
        val selected: Int,
        val canUndo: Boolean = false,
    ) : EditUiState {
        val isTrimmed: Boolean get() = !segments.coversWhole(clip.durationMs) || segments.items.size > 1
    }
}
