package com.example.autolog.edit

import com.example.autolog.data.clip.Clip
import com.example.autolog.data.clip.ClipTrim

sealed interface EditUiState {

    data object Loading : EditUiState

    /** 미리보기에서 넘어오는 사이에 앱 밖에서 지워졌을 수 있다. */
    data object Missing : EditUiState

    /** [trim]은 아직 저장하지 않은, 손잡이가 가리키는 구간이다. */
    data class Ready(val clip: Clip, val trim: ClipTrim) : EditUiState {
        val isTrimmed: Boolean get() = !trim.coversWhole(clip.durationMs)
    }
}
