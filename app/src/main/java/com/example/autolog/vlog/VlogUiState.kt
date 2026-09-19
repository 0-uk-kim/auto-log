package com.example.autolog.vlog

/**
 * 브이로그 생성 화면의 상태. WorkManager가 들고 있는 작업 상태를 화면 말로 옮긴 것이다.
 */
sealed interface VlogUiState {

    /** 아직 만든 적이 없다. */
    data object Idle : VlogUiState

    /**
     * 병합 중. [percent]는 Transformer가 알려 줄 때만 올라가므로, 시작 직후에는 0에 머문다 —
     * 그 구간과 "진행률을 아직 모른다"를 화면에서 가르지 않는다.
     */
    data class Running(val percent: Int) : VlogUiState

    data class Done(val outputPath: String, val durationMs: Long) : VlogUiState

    data object Failed : VlogUiState
}
