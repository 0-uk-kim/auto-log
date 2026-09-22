package com.example.autolog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/**
 * 플레이어의 현재 재생 위치(ms). Player는 위치 변화를 알려 주지 않아 물어봐야 한다.
 *
 * 자막이 켜지고 꺼지는 순간이 눈에 어긋나지 않을 만큼만 자주 묻는다.
 */
@Composable
fun rememberPlaybackPosition(player: Player): State<Long> {
    val position = remember(player) { mutableLongStateOf(player.currentPosition) }
    LaunchedEffect(player) {
        while (true) {
            position.longValue = player.currentPosition
            delay(POSITION_INTERVAL_MS)
        }
    }
    return position
}

private const val POSITION_INTERVAL_MS = 50L
