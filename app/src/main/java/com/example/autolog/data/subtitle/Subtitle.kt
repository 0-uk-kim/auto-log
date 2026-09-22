package com.example.autolog.data.subtitle

import com.example.autolog.data.db.SubtitleEntity

/** 클립 [clipId]의 [startMs]부터 [endMs] 직전까지 보이는 자막 한 줄. 시각은 클립 안의 상대 위치다. */
data class Subtitle(
    val id: Long = 0,
    val clipId: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    /** 끝 시각은 포함하지 않는다 — 이어 붙인 두 자막이 경계에서 겹쳐 보이지 않게. */
    fun isShownAt(positionMs: Long): Boolean = positionMs in startMs until endMs
}

internal fun SubtitleEntity.toSubtitle() = Subtitle(id, clipId, startMs, endMs, text)

internal fun Subtitle.toEntity() = SubtitleEntity(id, clipId, startMs, endMs, text)
