package com.example.autolog.edit

import com.example.autolog.data.subtitle.Subtitle

/**
 * 편집 중인 자막 한 줄. 저장하기 전까지는 화면에만 있다.
 *
 * 시작·끝은 "지금 재생 위치로 찍기"로 정한다. 찍은 값이 반대편을 넘어가면 반대편을 밀어서
 * 구간이 뒤집히지 않게 한다 — 사용자가 순서를 신경 쓰지 않고 찍어도 된다.
 */
data class SubtitleDraft(
    /** 새 자막이면 0. */
    val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val text: String = "",
) {
    val canSave: Boolean get() = text.isNotBlank() && endMs - startMs >= MIN_LENGTH_MS

    fun withStart(positionMs: Long, durationMs: Long): SubtitleDraft {
        val start = positionMs.coerceIn(0, (durationMs - MIN_LENGTH_MS).coerceAtLeast(0))
        val end = if (endMs - start < MIN_LENGTH_MS) (start + DEFAULT_LENGTH_MS).coerceAtMost(durationMs) else endMs
        return copy(startMs = start, endMs = end)
    }

    fun withEnd(positionMs: Long, durationMs: Long): SubtitleDraft {
        val end = positionMs.coerceIn(MIN_LENGTH_MS.coerceAtMost(durationMs), durationMs)
        val start = if (end - startMs < MIN_LENGTH_MS) (end - DEFAULT_LENGTH_MS).coerceAtLeast(0) else startMs
        return copy(startMs = start, endMs = end)
    }

    fun toSubtitle(clipId: Long) = Subtitle(id, clipId, startMs, endMs, text.trim())

    companion object {
        /** 이보다 짧으면 읽기 전에 사라진다. */
        const val MIN_LENGTH_MS = 500L

        /** 새로 찍을 때 기본 길이. 한 문장을 읽을 만한 시간이다. */
        const val DEFAULT_LENGTH_MS = 2_000L

        fun startingAt(positionMs: Long, durationMs: Long) =
            SubtitleDraft(startMs = 0, endMs = durationMs).withStart(positionMs, durationMs)
                .let { it.copy(endMs = (it.startMs + DEFAULT_LENGTH_MS).coerceAtMost(durationMs)) }

        fun of(subtitle: Subtitle) =
            SubtitleDraft(subtitle.id, subtitle.startMs, subtitle.endMs, subtitle.text)
    }
}

/**
 * 편집 화면에 보일 자막들. 고치는 중인 줄은 저장된 값 대신 초안으로 보여 줘서, 구간을 찍고 글을
 * 바꾸는 대로 영상 위에서 바로 확인할 수 있다.
 */
fun List<Subtitle>.withDraft(draft: SubtitleDraft?, clipId: Long): List<Subtitle> {
    if (draft == null) return this
    val others = if (draft.id == 0L) this else filterNot { it.id == draft.id }
    return if (draft.text.isBlank()) others else others + draft.toSubtitle(clipId)
}
