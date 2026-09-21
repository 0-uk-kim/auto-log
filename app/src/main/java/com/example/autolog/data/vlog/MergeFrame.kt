package com.example.autolog.data.vlog

import com.example.autolog.camera.CaptureOrientation

/** 브이로그 한 편의 화면 크기. 세로·가로가 섞였을 때만 정해진다. */
data class MergeFrame(val width: Int, val height: Int)

/**
 * 클립 방향이 모두 같으면 null — 원래 규격 그대로 이어붙여 재인코딩을 피한다.
 * 섞여 있으면 첫 클립 방향의 FHD로 맞추고, 방향이 다른 클립은 그 안에 레터박스로 넣는다 (#40).
 * 첫 클립은 사용자가 목록에서 맨 앞에 둔 것이라 영상의 얼굴로 본다.
 */
fun mergeFrameFor(orientations: List<CaptureOrientation>): MergeFrame? {
    if (orientations.distinct().size <= 1) return null
    return when (orientations.first()) {
        CaptureOrientation.Portrait -> MergeFrame(width = FHD_SHORT, height = FHD_LONG)
        CaptureOrientation.Landscape -> MergeFrame(width = FHD_LONG, height = FHD_SHORT)
    }
}

private const val FHD_SHORT = 1080
private const val FHD_LONG = 1920
