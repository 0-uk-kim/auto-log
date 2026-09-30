package com.example.autolog.data.clip

import androidx.media3.common.MediaItem

/** 저장된 구간만 재생·병합하도록 잘라 둔 MediaItem. 미리보기와 병합이 같은 구간을 쓰게 한 곳에서 만든다. */
fun Clip.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder().setUri(uri)
    trim?.let {
        builder.setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(it.startMs)
                .setEndPositionMs(it.endMs)
                .build(),
        )
    }
    return builder.build()
}
