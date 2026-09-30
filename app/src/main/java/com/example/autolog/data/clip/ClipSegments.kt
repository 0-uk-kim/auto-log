package com.example.autolog.data.clip

/** 클립에서 남길 조각 하나(ms, 클립 안의 상대 시각). 끝은 포함하지 않는다. */
data class ClipSegment(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs

    operator fun contains(positionMs: Long): Boolean = positionMs in startMs until endMs
}

/**
 * 클립에서 남길 조각들 (#92). 원본 파일은 그대로 두고 재생·병합할 때만 이 조각들을 이어 쓴다.
 *
 * 조각은 시각 순이고 겹치지 않는다. 맞닿는 것은 괜찮다 — 방금 분할한 자리가 그렇다.
 * 어떤 조작을 해도 조각이 클립 밖으로 나가거나 [MIN_LENGTH_MS]보다 짧아지지 않게 여기서 맞춘다.
 */
data class ClipSegments private constructor(val items: List<ClipSegment>) {

    val lengthMs: Long get() = items.sumOf { it.lengthMs }

    /** 빈 곳 없이 처음부터 끝까지 덮으면 잘라낸 것이 없다 — 분할만 했어도 그렇다. 저장하지 않고 지운다. */
    fun coversWhole(clipDurationMs: Long): Boolean = lengthMs >= clipDurationMs

    /** [positionMs]를 담은 조각의 자리. 조각 사이 빈 곳이면 -1. */
    fun indexAt(positionMs: Long): Int = items.indexOfFirst { positionMs in it }

    /** 양쪽 조각이 모두 최소 길이를 넘을 때만 나눌 수 있다. */
    fun canSplitAt(positionMs: Long, clipDurationMs: Long): Boolean {
        val segment = items.getOrNull(indexAt(positionMs)) ?: return false
        val min = minLength(clipDurationMs)
        return positionMs - segment.startMs >= min && segment.endMs - positionMs >= min
    }

    fun splitAt(positionMs: Long, clipDurationMs: Long): ClipSegments {
        if (!canSplitAt(positionMs, clipDurationMs)) return this
        val index = indexAt(positionMs)
        val segment = items[index]
        return ClipSegments(
            items.toMutableList().apply {
                set(index, ClipSegment(segment.startMs, positionMs))
                add(index + 1, ClipSegment(positionMs, segment.endMs))
            },
        )
    }

    /** 마지막 한 조각은 지울 수 없다 — 클립을 통째로 빼는 것은 삭제의 몫이다. */
    val canRemove: Boolean get() = items.size > 1

    fun remove(index: Int): ClipSegments =
        if (!canRemove || index !in items.indices) this else ClipSegments(items.filterIndexed { i, _ -> i != index })

    /** 시작 손잡이를 옮긴다. 앞 조각 끝을 넘지 못하고, 자기 끝과 최소 길이만큼 떨어진다. */
    fun withStart(index: Int, positionMs: Long, clipDurationMs: Long): ClipSegments {
        val segment = items.getOrNull(index) ?: return this
        val lower = items.getOrNull(index - 1)?.endMs ?: 0
        val upper = segment.endMs - minLength(clipDurationMs)
        return replace(index, segment.copy(startMs = positionMs.coerceIn(lower, upper.coerceAtLeast(lower))))
    }

    /** 끝 손잡이를 옮긴다. 다음 조각 시작을 넘지 못하고, 자기 시작과 최소 길이만큼 떨어진다. */
    fun withEnd(index: Int, positionMs: Long, clipDurationMs: Long): ClipSegments {
        val segment = items.getOrNull(index) ?: return this
        val upper = items.getOrNull(index + 1)?.startMs ?: clipDurationMs
        val lower = segment.startMs + minLength(clipDurationMs)
        return replace(index, segment.copy(endMs = positionMs.coerceIn(lower.coerceAtMost(upper), upper)))
    }

    /**
     * 남긴 조각만 이어 재생할 때 [positionMs]에서 건너뛸 곳. 조각 안이면 null,
     * 빈 곳이면 다음 조각 시작, 마지막 조각을 지났으면 첫 조각 시작으로 돌아간다.
     */
    fun nextPlayableFrom(positionMs: Long): Long? {
        if (indexAt(positionMs) >= 0) return null
        return (items.firstOrNull { it.startMs > positionMs } ?: items.first()).startMs
    }

    private fun replace(index: Int, segment: ClipSegment) =
        ClipSegments(items.toMutableList().apply { set(index, segment) })

    companion object {
        /**
         * 이보다 짧으면 브이로그에서 한 컷으로 알아보기 어렵다. 1초였을 때는 조각 양 끝 1초씩 자르기가 막혀
         * 2~7초짜리 클립에서 자를 곳이 거의 남지 않았다.
         */
        const val MIN_LENGTH_MS = 500L

        fun whole(clipDurationMs: Long): ClipSegments =
            ClipSegments(listOf(ClipSegment(0, clipDurationMs.coerceAtLeast(0))))

        /**
         * 저장된 조각을 지금 클립 길이에 맞춘다. 원본이 저장 뒤에 짧아졌을 수 있다.
         * 클립 밖은 잘라 내고, 겹치면 합치고, 최소 길이에 못 미치면 버린다. 남는 것이 없으면 전체다.
         */
        fun of(segments: List<ClipSegment>, clipDurationMs: Long): ClipSegments {
            val duration = clipDurationMs.coerceAtLeast(0)
            val min = minLength(duration)
            val merged = mutableListOf<ClipSegment>()
            segments
                .map { ClipSegment(it.startMs.coerceIn(0, duration), it.endMs.coerceIn(0, duration)) }
                .sortedBy { it.startMs }
                .forEach { segment ->
                    val last = merged.lastOrNull()
                    if (last != null && segment.startMs < last.endMs) {
                        merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, segment.endMs))
                    } else {
                        merged += segment
                    }
                }
            val kept = merged.filter { it.lengthMs >= min && it.lengthMs > 0 }
            return if (kept.isEmpty()) whole(duration) else ClipSegments(kept)
        }

        // 클립 자체가 최소 길이보다 짧으면 자를 여지가 없다 — 클립 전체가 최소 조각이다.
        private fun minLength(clipDurationMs: Long) = MIN_LENGTH_MS.coerceAtMost(clipDurationMs.coerceAtLeast(0))
    }
}
