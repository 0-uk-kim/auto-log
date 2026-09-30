package com.example.autolog.data.clip

import androidx.media3.common.MediaItem

/**
 * 남긴 조각마다 잘라 둔 MediaItem. 자르지 않은 클립은 원본 하나다.
 * 미리보기와 병합이 같은 조각을 쓰게 한 곳에서 만든다.
 */
fun Clip.segmentMediaItems(): List<MediaItem> {
    val kept = segments?.items ?: return listOf(MediaItem.fromUri(uri))
    return kept.map { segment ->
        MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(segment.startMs)
                    .setEndPositionMs(segment.endMs)
                    .build(),
            )
            .build()
    }
}
