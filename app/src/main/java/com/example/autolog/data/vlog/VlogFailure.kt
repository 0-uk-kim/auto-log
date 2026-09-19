package com.example.autolog.data.vlog

import com.example.autolog.data.clip.Clip

/**
 * 병합이 실패하는 이유. 사용자가 할 수 있는 일이 저마다 달라서 하나로 뭉뚱그리지 않는다 (#32).
 */
enum class VlogFailure {

    /** 그날 클립이 하나도 없다 — 앱 밖에서 다 지워졌거나 날짜를 잘못 짚었다. */
    NoClips,

    /** 결과물을 담을 자리가 모자란다. 지우고 다시 시도하면 된다. */
    NotEnoughStorage,

    /** 그 밖의 실패. 코덱·손상된 원본 등 사용자가 손댈 수 없는 것들이다. */
    MergeFailed,
    ;

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: MergeFailed
    }
}

/**
 * 이어붙인 결과를 담는 데 드는 자리. 원본을 합친 만큼에 컨테이너 헤더와 인코딩 여유를 더한다.
 *
 * 다 쓰고 나서야 모자란 걸 알면 그때까지의 시간과 배터리를 버리게 되므로 시작 전에 잰다 (#32).
 */
fun requiredBytesFor(clips: List<Clip>): Long =
    clips.sumOf { it.sizeBytes } * (100 + STORAGE_MARGIN_PERCENT) / 100

private const val STORAGE_MARGIN_PERCENT = 15
