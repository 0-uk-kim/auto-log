package com.example.autolog.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ThumbnailSize = Size(256, 256)

/**
 * 한 번 읽은 썸네일을 들고 있는다. 목록은 스크롤하며 같은 클립을 몇 번이고 다시 그리는데,
 * 그때마다 디코딩하면 스크롤이 끊긴다. 앱 수명 동안만 살아 있으면 되는 캐시라 메모리에만 둔다.
 */
private val ThumbnailCache = object : LruCache<Uri, Bitmap>(cacheSizeKb()) {
    override fun sizeOf(key: Uri, value: Bitmap) = value.byteCount / 1024
}

private fun cacheSizeKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()

/**
 * 클립의 대표 프레임. 아직 못 읽었으면 null이다.
 *
 * Coil 대신 [ContentResolver.loadThumbnail]을 쓴다 — MediaStore 썸네일은 회전 메타데이터가
 * 반영된 채로 나오지만, 프레임을 직접 디코딩하면 세로로 찍은 클립이 눕는다.
 */
@Composable
fun rememberClipThumbnail(uri: Uri): Bitmap? {
    val contentResolver = LocalContext.current.contentResolver
    var frame by remember(uri) { mutableStateOf(ThumbnailCache[uri]) }

    LaunchedEffect(uri) {
        if (frame != null) return@LaunchedEffect
        frame = withContext(Dispatchers.IO) {
            runCatching { contentResolver.loadThumbnail(uri, ThumbnailSize, null) }.getOrNull()
        }?.also { ThumbnailCache.put(uri, it) }
    }

    return frame
}
