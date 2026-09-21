package com.example.autolog.list

import com.example.autolog.data.clip.Clip
import com.example.autolog.permission.MediaAccess
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WithoutClipsTest {

    private val clips = (1L..3L).map { id ->
        Clip(id = id, displayName = "clip-$id.mp4", durationMs = 1_000, startedAt = Instant.ofEpochSecond(id))
    }
    private val state = ClipListUiState.Clips(clips, MediaAccess.Full)

    @Test
    fun `지운 클립만 빠지고 남은 순서는 그대로다`() {
        assertEquals(
            ClipListUiState.Clips(listOf(clips[0], clips[2]), MediaAccess.Full),
            state.withoutClips(setOf(2L)),
        )
    }

    @Test
    fun `마지막 클립까지 지우면 권한 상태를 들고 빈 화면이 된다`() {
        assertEquals(ClipListUiState.Empty(MediaAccess.Full), state.withoutClips(setOf(1L, 2L, 3L)))
    }

    @Test
    fun `목록이 아닌 상태는 그대로 둔다`() {
        assertEquals(ClipListUiState.Loading, ClipListUiState.Loading.withoutClips(setOf(1L)))
    }
}
