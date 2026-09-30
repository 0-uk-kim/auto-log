package com.example.autolog.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.autolog.data.clip.ClipSegment
import com.example.autolog.data.clip.ClipSegments
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraHighlight
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val TAG_TRIM_BAR = "edit-trim-bar"

private val StripHeight = 52.dp
/** 재생 위치 손잡이와 선택 테두리가 띠 위아래로 삐져나올 자리. */
private val Overhang = 8.dp
/** 재생 위치 시각 말풍선이 띠 위에 뜰 자리. */
private val BubbleHeight = 22.dp
private val HandleWidth = 16.dp
/** 손잡이는 눈에 보이는 폭보다 넓게 잡힌다 — 손가락이 손잡이를 가려 정확히 겨냥할 수 없다. */
private val HandleReach = 28.dp
private const val FRAME_COUNT = 8

private val RemovedScrim = Color(0xCC000000)
private val RemovedHatch = Color(0x33FFFFFF)

enum class TrimDrag { Start, End, Scrub }

/**
 * 끌기를 시작한 자리로 무엇을 움직일지 정한다. 선택한 조각의 손잡이에서 [reachMs] 안이면 그 손잡이,
 * 아니면 재생 위치를 옮긴다(탐색). 조각이 짧아 두 손잡이가 다 닿으면 가까운 쪽이다.
 */
internal fun trimDragFor(selected: ClipSegment?, touchMs: Long, reachMs: Long): TrimDrag {
    if (selected == null) return TrimDrag.Scrub
    val toStart = abs(touchMs - selected.startMs)
    val toEnd = abs(touchMs - selected.endMs)
    return when {
        toStart > reachMs && toEnd > reachMs -> TrimDrag.Scrub
        toStart <= toEnd -> TrimDrag.Start
        else -> TrimDrag.End
    }
}

/**
 * 클립 전체를 프레임 띠로 깔고, 남길 조각은 테두리로 감싸고 잘라낼 곳은 빗금으로 덮는다.
 *
 * - 탭: 그 자리로 재생 위치를 옮기고, 조각 안이면 그 조각을 고른다.
 * - 끌기: 선택한 조각의 손잡이 근처에서 시작하면 손잡이를, 그 밖이면 재생 위치를 옮긴다.
 *
 * 띠는 화면 가장자리까지 닿으므로 시스템 뒤로가기 제스처에서 뺀다 — 빼지 않으면 양 끝 손잡이를
 * 끌 때 화면이 닫힌다 (#90 녹화 때 실측).
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
    onScrub: (Long) -> Unit,
    onHandleDragStart: () -> Unit,
    onMoveStart: (Long) -> Unit,
    onMoveEnd: (Long) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val frames = rememberFilmstrip(uri, durationMs)
    val currentSegments by rememberUpdatedState(segments)
    val currentSelected by rememberUpdatedState(selected)
    val tap by rememberUpdatedState(onTap)
    val scrub by rememberUpdatedState(onScrub)
    val handleDragStart by rememberUpdatedState(onHandleDragStart)
    val moveStart by rememberUpdatedState(onMoveStart)
    val moveEnd by rememberUpdatedState(onMoveEnd)
    val textMeasurer = rememberTextMeasurer()
    val bubbleStyle = MaterialTheme.typography.labelMedium.copy(color = CameraBackground)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BubbleHeight + StripHeight + Overhang * 2)
            .systemGestureExclusion()
            .semantics { this.contentDescription = contentDescription }
            .testTag(TAG_TRIM_BAR)
            .pointerInput(durationMs) {
                detectTapGestures { offset -> tap(timeAt(offset.x, size.width, durationMs)) }
            }
            .pointerInput(durationMs) {
                var drag = TrimDrag.Scrub
                var x = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        x = offset.x
                        val reachMs = (HandleReach.toPx() / size.width * durationMs).toLong()
                        drag = trimDragFor(
                            currentSegments.items.getOrNull(currentSelected),
                            timeAt(x, size.width, durationMs),
                            reachMs,
                        )
                        if (drag == TrimDrag.Scrub) scrub(timeAt(x, size.width, durationMs)) else handleDragStart()
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        x = (x + dragAmount).coerceIn(0f, size.width.toFloat())
                        val t = timeAt(x, size.width, durationMs)
                        when (drag) {
                            TrimDrag.Start -> moveStart(t)
                            TrimDrag.End -> moveEnd(t)
                            TrimDrag.Scrub -> scrub(t)
                        }
                    },
                )
            }
            .drawWithContent {
                drawContent()
                drawSegments(segments, selected, durationMs)
                drawPlayhead(position().coerceIn(0, durationMs), durationMs, textMeasurer, bubbleStyle)
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(top = BubbleHeight + Overhang, bottom = Overhang)
                .clip(RoundedCornerShape(6.dp)),
        ) {
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

private fun timeAt(x: Float, width: Int, durationMs: Long): Long =
    (x / width.coerceAtLeast(1) * durationMs).toLong().coerceIn(0, durationMs)

private fun DrawScope.drawSegments(segments: ClipSegments, selected: Int, durationMs: Long) {
    val total = durationMs.coerceAtLeast(1).toFloat()
    fun xOf(ms: Long) = ms / total * size.width
    val top = (BubbleHeight + Overhang).toPx()
    val bottom = size.height - Overhang.toPx()

    // 잘라낼 곳: 어둡게 덮고 빗금을 친다 — 어둡기만으로는 "조금 어두운 장면"과 구분되지 않는다.
    var gapStart = 0f
    (segments.items.map { xOf(it.startMs) to xOf(it.endMs) } + (size.width to size.width)).forEach { (start, end) ->
        if (start > gapStart) drawRemoved(gapStart, start, top, bottom)
        gapStart = end
    }

    segments.items.forEachIndexed { index, segment ->
        if (index == selected) return@forEachIndexed
        val border = 1.5.dp.toPx()
        drawRoundRect(
            color = CameraControlTint.copy(alpha = 0.8f),
            topLeft = Offset(xOf(segment.startMs) + border / 2, top + border / 2),
            size = Size(xOf(segment.endMs) - xOf(segment.startMs) - border, bottom - top - border),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(border),
        )
    }
    segments.items.getOrNull(selected)?.let { drawSelected(xOf(it.startMs), xOf(it.endMs), top, bottom) }
}

private fun DrawScope.drawRemoved(left: Float, right: Float, top: Float, bottom: Float) {
    drawRect(RemovedScrim, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
    clipRect(left, top, right, bottom) {
        val step = 8.dp.toPx()
        val height = bottom - top
        var x = left - height
        while (x < right) {
            drawLine(RemovedHatch, Offset(x, bottom), Offset(x + height, top), strokeWidth = 1.5.dp.toPx())
            x += step
        }
    }
}

/** 선택한 조각: 위아래 굵은 테두리와 양 끝의 잡기 좋은 손잡이. 손잡이는 조각 안쪽으로 그린다. */
private fun DrawScope.drawSelected(startX: Float, endX: Float, top: Float, bottom: Float) {
    val handle = HandleWidth.toPx()
    val edge = 3.dp.toPx()
    val radius = CornerRadius(6.dp.toPx())
    drawRect(CameraHighlight, topLeft = Offset(startX, top - edge), size = Size(endX - startX, edge))
    drawRect(CameraHighlight, topLeft = Offset(startX, bottom), size = Size(endX - startX, edge))
    listOf(startX, endX - handle).forEach { left ->
        drawRoundRect(CameraHighlight, topLeft = Offset(left, top - edge), size = Size(handle, bottom - top + edge * 2), cornerRadius = radius)
        // 그립 표시 — 끌 수 있는 것임을 알린다.
        val center = left + handle / 2
        val gripTop = (top + bottom) / 2 - 8.dp.toPx()
        val gripBottom = (top + bottom) / 2 + 8.dp.toPx()
        listOf(center - 2.5.dp.toPx(), center + 2.5.dp.toPx()).forEach { gx ->
            drawLine(CameraBackground, Offset(gx, gripTop), Offset(gx, gripBottom), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/**
 * 재생 위치 선과 그 위의 시각 말풍선. 시각을 선에 붙여 두면 "지금 보는 장면이 어디인지"를 따로 읽을 필요가 없다.
 * 말풍선은 띠 양 끝에서 잘리지 않게 안쪽으로 밀어 넣는다.
 */
private fun DrawScope.drawPlayhead(
    positionMs: Long,
    durationMs: Long,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val x = positionMs / durationMs.coerceAtLeast(1).toFloat() * size.width
    val bubbleBottom = BubbleHeight.toPx()
    drawLine(
        color = CameraControlTint,
        start = Offset(x, bubbleBottom),
        end = Offset(x, size.height - Overhang.toPx() / 2),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )

    val text = textMeasurer.measure(formatTrimTime(positionMs), style)
    val padding = 6.dp.toPx()
    val width = text.size.width + padding * 2
    val left = (x - width / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
    drawRoundRect(
        color = CameraControlTint,
        topLeft = Offset(left, 0f),
        size = Size(width, bubbleBottom),
        cornerRadius = CornerRadius(bubbleBottom / 2),
    )
    drawText(text, topLeft = Offset(left + padding, (bubbleBottom - text.size.height) / 2))
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
    val sizePx = (StripHeight.value * context.resources.displayMetrics.density).toInt()
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
