package com.example.autolog.navigation

import kotlinx.serialization.Serializable

/**
 * 날짜가 곧 프로젝트라서 카메라를 뺀 모든 목적지가 날짜(ISO-8601)를 인자로 받는다.
 * 날짜는 촬영 종료 시각 기준이다 (planning 6).
 */
sealed interface AutoLogRoute

@Serializable
data object Camera : AutoLogRoute

@Serializable
data class ClipList(val date: String) : AutoLogRoute

/** [clipIndex]가 [LATEST_CLIP]이면 그날의 마지막 클립부터 연다 — 카메라 좌측 하단 진입 (planning 6). */
@Serializable
data class Preview(val date: String, val clipIndex: Int) : AutoLogRoute {
    companion object {
        const val LATEST_CLIP = -1
    }
}

@Serializable
data class Vlog(val date: String) : AutoLogRoute

/** 클립 하나를 손보는 화면. 자리(index)가 아니라 [clipId]로 가리킨다 — 목록 순서가 바뀌어도 같은 클립을 연다. */
@Serializable
data class Edit(val date: String, val clipId: Long) : AutoLogRoute
