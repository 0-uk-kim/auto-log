package com.example.autolog.list

import com.example.autolog.data.clip.Clip
import com.example.autolog.permission.MediaAccess

/**
 * 목록 화면의 상태.
 *
 * 비어 있는 이유를 [MediaAccess]로 갈라 들고 다닌다 — "안 찍었다"와 "못 읽는다"는 사용자가 할 일이
 * 전혀 다른데 화면은 똑같이 0건으로 보이기 때문이다 (#15, #35).
 */
sealed interface ClipListUiState {

    data object Loading : ClipListUiState

    data class Empty(val access: MediaAccess) : ClipListUiState

    data class Clips(val clips: List<Clip>, val access: MediaAccess) : ClipListUiState
}

/** 지운 클립을 뺀 상태. 마지막 클립까지 지우면 빈 화면으로 넘어간다 (#38). */
fun ClipListUiState.withoutClips(clipIds: Set<Long>): ClipListUiState {
    if (this !is ClipListUiState.Clips) return this
    val remaining = clips.filterNot { it.id in clipIds }
    return if (remaining.isEmpty()) ClipListUiState.Empty(access) else copy(clips = remaining)
}
