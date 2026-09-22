package com.example.autolog.data.subtitle

/**
 * 자막 모양. 화면 오버레이(T3)와 병합 때 새기는 효과(T4)가 같은 값을 써서, 만들기 전에 본 모양이
 * 결과물에도 그대로 나온다. 모두 **영상 높이** 대비 비율이라 해상도·가로세로와 상관없이 같은 크기로 보인다.
 */
object SubtitleStyle {
    const val TEXT_SIZE_RATIO = 0.032f
    const val BOTTOM_MARGIN_RATIO = 0.08f
    const val PADDING_RATIO = 0.008f

    /** 줄 폭은 영상 폭 대비. 넘치면 줄을 바꾼다. */
    const val MAX_WIDTH_RATIO = 0.86f

    const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    const val BACKGROUND_COLOR = 0x99000000.toInt()
}
