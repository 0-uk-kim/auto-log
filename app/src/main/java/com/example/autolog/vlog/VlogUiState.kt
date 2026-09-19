package com.example.autolog.vlog

import com.example.autolog.data.vlog.VlogFailure

/**
 * 브이로그 생성 화면의 상태. WorkManager가 들고 있는 작업 상태를 화면 말로 옮긴 것이다.
 */
sealed interface VlogUiState {

    /** 작업 기록과 저장분을 아직 못 읽었다. 한 프레임짜리 상태다. */
    data object Loading : VlogUiState

    /** 아직 만든 적이 없다. */
    data object Idle : VlogUiState

    /**
     * 병합 중. [percent]는 Transformer가 알려 줄 때만 올라가므로, 시작 직후에는 0에 머문다 —
     * 그 구간과 "진행률을 아직 모른다"를 화면에서 가르지 않는다.
     */
    data class Running(val percent: Int) : VlogUiState

    /** [uri]는 갤러리에 올라간 결과물이다 — 앱 캐시 파일이 아니라 공유·재생이 바로 되는 주소다. */
    data class Done(val uri: String, val durationMs: Long) : VlogUiState

    data class Failed(val reason: VlogFailure) : VlogUiState
}
