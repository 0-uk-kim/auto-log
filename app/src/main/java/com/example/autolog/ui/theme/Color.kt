package com.example.autolog.ui.theme

import androidx.compose.ui.graphics.Color

// 앱 전체가 카메라와 같은 검정 바탕 위에 선다. 영상이 주인공이라 틴트 없는 무채색만 쓰고,
// 강조는 카메라에서 쓰던 노랑 하나로 모은다.
val Ink = Color(0xFF000000)
val Surface1 = Color(0xFF141416)
val Surface2 = Color(0xFF1E1E21)
val Surface3 = Color(0xFF2A2A2E)
val Outline = Color(0xFF3A3A3F)
val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFF9B9BA1)

val Danger = Color(0xFFFF6B5E)
val DangerContainer = Color(0xFF3B1714)
val OnDangerContainer = Color(0xFFFFB4AB)

/** 달력의 토요일. 한국 달력 관례대로 파랑으로 가른다. */
val Saturday = Color(0xFF7AA7FF)

// 카메라 화면은 스킴과 무관하게 항상 검은 배경 위에 얹히므로 스킴 밖에 둔다.
val RecordRed = Color(0xFFE5342B)
val CameraBackground = Color(0xFF000000)
val CameraScrim = Color(0x99000000)
val CameraControlTint = Color(0xFFFFFFFF)
/** 고른 배율·옵션처럼 지금 켜진 것을 짚는 색. 갤럭시 카메라의 노란 강조를 따른다. */
val CameraHighlight = Color(0xFFFFD60A)
