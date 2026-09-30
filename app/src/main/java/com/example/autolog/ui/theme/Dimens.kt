package com.example.autolog.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object CameraDimens {
    val recordButton = 76.dp
    /** 셔터 양옆 썸네일·렌즈 전환 원. */
    val sideAction = 52.dp
}

object ListDimens {
    /** 9:16 세로 촬영 고정이라 썸네일도 세로로 세운다 (planning 6-1). */
    val clipThumbnail = 96.dp
    val clipThumbnailWidth = 54.dp
    val orderNumber = 22.dp
    val primaryButton = 56.dp
    /** 위 바의 원형 유리 버튼. 48dp 터치 영역은 [androidx.compose.material3.minimumInteractiveComponentSize]가 채운다. */
    val glassButton = 44.dp
    val glassPill = 40.dp
    val topBar = 64.dp
}
