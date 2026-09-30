package com.example.autolog.data.clip

/**
 * 클립에서 남길 구간(ms, 클립 안의 상대 시각). 원본 파일은 그대로 두고 재생·병합할 때만 잘라 쓴다.
 *
 * 값은 [of]로만 만든다 — 손잡이를 어떻게 끌어도 클립 밖으로 나가거나 [MIN_LENGTH_MS]보다
 * 짧아지지 않게 여기서 한 번에 맞춘다.
 */
data class ClipTrim private constructor(val startMs: Long, val endMs: Long) {

    val lengthMs: Long get() = endMs - startMs

    /** 처음부터 끝까지 그대로면 자른 것이 아니다 — 저장하지 않고 지운다. */
    fun coversWhole(clipDurationMs: Long): Boolean = startMs == 0L && endMs >= clipDurationMs

    /** 시작 손잡이를 옮긴다. 끝은 그대로 두고, 너무 붙으면 시작을 되민다. */
    fun withStart(positionMs: Long, clipDurationMs: Long): ClipTrim =
        of(positionMs.coerceAtMost(endMs - minLength(clipDurationMs)), endMs, clipDurationMs)

    /** 끝 손잡이를 옮긴다. 시작은 그대로 두고, 너무 붙으면 끝을 되민다. */
    fun withEnd(positionMs: Long, clipDurationMs: Long): ClipTrim =
        of(startMs, positionMs.coerceAtLeast(startMs + minLength(clipDurationMs)), clipDurationMs)

    companion object {
        /** 이보다 짧으면 브이로그에서 한 컷으로 알아보기 어렵다. */
        const val MIN_LENGTH_MS = 1_000L

        fun whole(clipDurationMs: Long): ClipTrim = ClipTrim(0, clipDurationMs.coerceAtLeast(0))

        fun of(startMs: Long, endMs: Long, clipDurationMs: Long): ClipTrim {
            val duration = clipDurationMs.coerceAtLeast(0)
            val min = minLength(duration)
            val start = startMs.coerceIn(0, duration - min)
            val end = endMs.coerceIn(start + min, duration)
            return ClipTrim(start, end)
        }

        // 클립 자체가 최소 길이보다 짧으면 자를 여지가 없다 — 클립 전체가 최소 구간이다.
        private fun minLength(clipDurationMs: Long) = MIN_LENGTH_MS.coerceAtMost(clipDurationMs.coerceAtLeast(0))
    }
}
