package com.example.autolog.preview

import com.example.autolog.data.clip.Clip
import com.example.autolog.navigation.Preview

sealed interface PreviewUiState {

    data object Loading : PreviewUiState

    /** 들어오는 사이에 앱 밖에서 다 지워졌을 수 있다 — 빈 플레이어를 띄우는 대신 말해 준다. */
    data object Empty : PreviewUiState

    data class Ready(val clips: List<Clip>, val startIndex: Int) : PreviewUiState
}

/**
 * 진입점이 준 위치를 실제 목록 안의 자리로 옮긴다 (planning 6 "미리보기 재생 범위").
 *
 * 카메라 좌측 하단으로 들어오면 [Preview.LATEST_CLIP]이고, 그때 시작 위치는 **가장 나중에 찍은**
 * 클립이다 — 목록의 마지막 자리가 아니다. 순서를 바꿔 두면 둘이 갈리는데, 좌측 하단 썸네일은
 * 직전 촬영본을 보여주므로 그 썸네일과 열리는 클립이 어긋나면 안 된다 (#24).
 *
 * 목록에서 들어오면 탭한 자리 그대로다.
 */
fun List<Clip>.startIndexFor(clipIndex: Int): Int = when {
    isEmpty() -> 0
    clipIndex == Preview.LATEST_CLIP -> indexOf(maxBy { it.endedAt })
    else -> clipIndex.coerceIn(0, lastIndex)
}
