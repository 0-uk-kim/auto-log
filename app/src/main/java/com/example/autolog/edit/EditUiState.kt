package com.example.autolog.edit

import com.example.autolog.data.clip.Clip

sealed interface EditUiState {

    data object Loading : EditUiState

    /** 미리보기에서 넘어오는 사이에 앱 밖에서 지워졌을 수 있다. */
    data object Missing : EditUiState

    data class Ready(val clip: Clip) : EditUiState
}
