package com.example.autolog.preview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.example.autolog.data.clip.Clip
import com.example.autolog.data.clip.segmentMediaItems

/**
 * 클립 하나를 재생목록 항목 **하나**로 만든다 (#92).
 *
 * 조각이 여럿이어도 항목을 나누지 않고 이어 붙인다 — 미리보기는 "페이지 = 재생목록 항목"으로
 * 맞물려 있어서, 조각마다 항목을 만들면 페이지와 재생 위치가 어긋난다.
 */
@OptIn(UnstableApi::class)
internal fun Clip.previewMediaSource(context: Context, factory: DefaultMediaSourceFactory): MediaSource {
    val items = segmentMediaItems()
    val kept = segments?.items
    if (kept == null || kept.size == 1) return factory.createMediaSource(items.single())
    return ConcatenatingMediaSource2.Builder()
        .useDefaultMediaSourceFactory(context)
        .setMediaItem(MediaItem.fromUri(uri))
        .apply {
            kept.zip(items).forEach { (segment, item) -> add(item, segment.lengthMs) }
        }
        .build()
}
