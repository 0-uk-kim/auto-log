package com.example.autolog.camera

import kotlin.time.Duration

/**
 * 하이퍼랩스 배속 (#65, #100). 평소처럼 찍은 뒤 이 배수만큼 빠르게 다시 담는다. [Off]는 일반 동영상이다.
 * 갤럭시 기본 카메라의 하이퍼랩스 배속(4·8·16·32배)을 따른다.
 */
enum class TimelapseSpeed(val factor: Int) {
    Off(1),
    Four(4),
    Eight(8),
    Sixteen(16),
    ThirtyTwo(32),
    ;

    val isOn: Boolean
        get() = this != Off

    /** [recorded]만큼 찍으면 완성본이 얼마나 되는지. 촬영 중에 결과 길이를 미리 보여 준다. */
    fun outputOf(recorded: Duration): Duration = recorded / factor

    companion object {
        /** 하이퍼랩스 모드에서 고를 수 있는 배속. */
        val choices: List<TimelapseSpeed> = entries.filter { it.isOn }

        val Default = Eight
    }
}
