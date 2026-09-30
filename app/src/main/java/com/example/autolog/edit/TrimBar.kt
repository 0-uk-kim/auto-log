package com.example.autolog.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.autolog.data.clip.ClipSegments
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraScrim
import com.example.autolog.ui.theme.Coral80
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val TAG_TRIM_BAR = "edit-trim-bar"

private val BarHeight = 56.dp
private val HandleWidth = 14.dp
private const val FRAME_COUNT = 8

private enum class Handle { Start, End }

/**
 * 클립 전체를 프레임 띠로 깔고 남길 조각들을 테두리로 감싼다. 조각 사이 잘려 나갈 곳은 어둡게 덮는다.
 *
 * 띠 위의 가로 위치가 곧 클립 안의 시각이다. 조각을 탭하면 선택되고, 끌면 **선택한 조각**의
 * 가까운 손잡이가 움직인다 — 맞닿은 조각의 경계가 겹쳐도 어느 쪽을 잡을지 헷갈리지 않는다.
 * [position]은 재생 위치(ms)로, 그리는 단계에서만 읽는다 — 재생 중 이 컴포저블 전체를 다시 구성하지 않는다.
 */
@Composable
fun TrimBar(
    uri: Uri,
    durationMs: Long,
    segments: ClipSegments,
    selected: Int,
    position: () -> Long,
    onTap: (Long) -> Unit,
    onMoveStart: (Long) -> Unit,
    onMoveEnd: (Long) -> Unit,
    onDragEnd: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val frames = rememberFilmstrip(uri, durationMs)
    val currentSegments by rememberUpdatedState(segments)
    val currentSelected by rememberUpdatedState(selected)
    val tap by rememberUpdatedState(onTap)
    val moveStart by rememberUpdatedState(onMoveStart)
    val moveEnd by rememberUpdatedState(onMoveEnd)
    val dragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(CameraScrim)
            .semantics { this.contentDescription = contentDescription }
            .testTag(TAG_TRIM_BAR)
            .pointerInput(durationMs) {
                detectTapGestures { offset -> tap((offset.x / size.width * durationMs).toLong()) }
            }
            .pointerInput(durationMs) {
                var handle = Handle.Start
                var x = 0f
                fun timeAt(x: Float) = (x / size.width * durationMs).toLong()
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        x = offset.x
                        val segment = currentSegments.items.getOrNull(currentSelected) ?: return@detectHorizontalDragGestures
                        val t = timeAt(x)
                        handle = if (abs(t - segment.startMs) <= abs(t - segment.endMs)) Handle.Start else Handle.End
                    },
                    onDragEnd = { dragEnd() },
                    onDragCancel = { dragEnd() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        x = (x + dragAmount).coerceIn(0f, size.width.toFloat())
                        when (handle) {
                            Handle.Start -> moveStart(timeAt(x))
                            Handle.End -> moveEnd(timeAt(x))
                        }
                    },
                )
            }
            .drawWithContent {
                drawContent()
                val total = durationMs.coerceAtLeast(1).toFloat()
                fun xOf(ms: Long) = ms / total * size.width

                // 조각 사이와 양 끝, 잘려 나갈 곳을 어둡게 덮는다.
                var gapStart = 0f
                segments.items.forEach { segment ->
                    drawRect(CameraScrim, topLeft = Offset(gapStart, 0f), size = Size(xOf(segment.startMs) - gapStart, size.height))
                    gapStart = xOf(segment.endMs)
                }
                drawRect(CameraScrim, topLeft = Offset(gapStart, 0f), size = Size(size.width - gapStart, size.height))

                segments.items.forEachIndexed { index, segment ->
                    val startX = xOf(segment.startMs)
                    val endX = xOf(segment.endMs)
                    val isSelected = index == selected
                    val border = (if (isSelected) 3.dp else 1.5.dp).toPx()
                    drawRoundRect(
                        color = if (isSelected) Coral80 else Coral80.copy(alpha = 0.6f),
                        topLeft = Offset(startX + border / 2, border / 2),
                        size = Size((endX - startX - border).coerceAtLeast(0f), size.height - border),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(border),
                    )
                    if (isSelected) {
                        // 손잡이는 조각 안쪽으로 그린다 — 양 끝에 붙어도 띠 밖으로 잘려 나가지 않는다.
                        val handleWidth = HandleWidth.toPx()
                        drawRect(Coral80, topLeft = Offset(startX, 0f), size = Size(handleWidth, size.height))
                        drawRect(Coral80, topLeft = Offset(endX - handleWidth, 0f), size = Size(handleWidth, size.height))
                    }
                }

                val playheadX = xOf(position().coerceIn(0, durationMs))
                drawLine(
                    color = CameraControlTint,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            frames.forEach { frame ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    frame?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** 클립 전체에 고르게 흩은 프레임들. 앞에서부터 하나씩 채워진다. */
@Composable
private fun rememberFilmstrip(uri: Uri, durationMs: Long): List<Bitmap?> {
    val context = LocalContext.current
    val frames = remember(uri) { mutableStateListOf<Bitmap?>().apply { repeat(FRAME_COUNT) { add(null) } } }
    LaunchedEffect(uri, durationMs) {
        withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, uri)
                    val rotation = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    val codedLandscape = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) >
                        retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    repeat(FRAME_COUNT) { index ->
                        // 칸 가운데 시각의 프레임 — 첫 칸이 검은 첫 프레임으로 채워지는 것을 피한다.
                        val timeUs = durationMs * 1000 * (2 * index + 1) / (2 * FRAME_COUNT)
                        frames[index] = retriever.frameAt(context, timeUs, rotation, codedLandscape)
                    }
                }
            }
        }
    }
    return frames
}

private fun MediaMetadataRetriever.intMetadata(key: Int): Int = extractMetadata(key)?.toIntOrNull() ?: 0

private fun MediaMetadataRetriever.frameAt(
    context: Context,
    timeUs: Long,
    rotation: Int,
    codedLandscape: Boolean,
): Bitmap? {
    val sizePx = (BarHeight.value * context.resources.displayMetrics.density).toInt()
    val frame = getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, sizePx, sizePx)
        ?: return null
    return frame.uprightFor(rotation, codedLandscape)
}

/**
 * 프레임 디코딩은 기기에 따라 회전 메타데이터를 반영하기도 하고 안 하기도 한다.
 * 90°·270° 클립인데 프레임이 아직 원래 방향 그대로면 그때만 돌린다 — 두 번 돌려 눕히지 않는다.
 */
private fun Bitmap.uprightFor(rotation: Int, codedLandscape: Boolean): Bitmap {
    if (rotation % 180 == 0 || (width > height) != codedLandscape) return this
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
