package com.example.autolog.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autolog.R
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraHighlight
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlinx.coroutines.delay

const val TAG_ZOOM_CONTROL = "zoom-control"
const val TAG_ZOOM_DIAL = "zoom-dial"

/**
 * 배율 다이얼이 펼쳐져 있는지. 핀치하는 프리뷰와 드래그하는 버튼 줄이 함께 쓴다 —
 * 어느 쪽으로 배율을 바꾸든 갤럭시처럼 다이얼이 떠서 지금 배율을 보여준다.
 */
@Stable
class ZoomDialState {
    var isVisible by mutableStateOf(false)
        private set
    internal var isDragging by mutableStateOf(false)
        private set

    // 만질 때마다 올려 숨김 타이머를 처음부터 다시 센다.
    internal var touches by mutableIntStateOf(0)
        private set

    fun touch() {
        isVisible = true
        touches++
    }

    internal fun startDrag() {
        isDragging = true
        touch()
    }

    internal fun endDrag() {
        isDragging = false
        touches++
    }

    internal fun hide() {
        isVisible = false
    }
}

@Composable
fun rememberZoomDialState(): ZoomDialState = remember { ZoomDialState() }

/**
 * 배율 버튼 줄 + 드래그 다이얼 (#42, #100). 갤럭시 기본 카메라처럼 버튼을 누르면 그 배율로 가고,
 * 버튼 줄을 좌우로 끌면 곡선 다이얼이 펼쳐져 손가락을 따라 배율이 바뀐다 — 왼쪽이 확대, 오른쪽이 축소다.
 * 손을 떼고 잠시 두면 다시 버튼으로 접힌다.
 *
 * [ratio]는 State째로 받는다. 드래그 중 매 프레임 바뀌는 값을 여기서만 읽어 카메라 화면 전체가 다시 그려지지 않게 한다.
 */
@Composable
fun ZoomControl(
    range: ZoomRange,
    ratio: State<Float>,
    dialState: ZoomDialState,
    onSelect: (Float) -> Unit,
    onDrag: (dragPx: Float, pxPerLn: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pxPerLn = with(LocalDensity.current) { DIAL_DP_PER_LN.dp.toPx() }
    // pointerInput은 키가 같으면 다시 시작하지 않으므로 콜백은 호출 시점의 최신 것을 읽는다.
    val latestOnDrag by rememberUpdatedState(onDrag)
    val drag: (Float, Float) -> Unit = { px, perLn -> latestOnDrag(px, perLn) }

    LaunchedEffect(dialState.touches, dialState.isDragging) {
        if (!dialState.isDragging && dialState.isVisible) {
            delay(DIAL_HIDE_DELAY_MS)
            dialState.hide()
        }
    }

    // 다이얼이 숫자 눈금을 지날 때마다 짧게 떨어 손으로도 1x·2x 자리를 느끼게 한다.
    val haptics = LocalHapticFeedback.current
    val labeled = remember(range) { range.ticks().filter { it.label != null }.map { it.ratio } }
    LaunchedEffect(labeled) {
        var previous = ratio.value
        snapshotFlow { ratio.value }.collect { current ->
            if (dialState.isVisible && labeled.any { it crossedBetween (previous to current) }) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            previous = current
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DIAL_HEIGHT.dp)
            .testTag(TAG_ZOOM_CONTROL),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = dialState.isVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.9f, transformOrigin = BottomCenterOrigin),
            exit = fadeOut() + scaleOut(targetScale = 0.9f, transformOrigin = BottomCenterOrigin),
        ) {
            ZoomDial(
                range = range,
                ratio = ratio,
                pxPerLn = pxPerLn,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomDrag(dialState, pxPerLn, drag),
            )
        }

        // 다이얼이 떠도 이 줄은 남겨 둔다 — 버튼 줄에서 시작한 드래그가 다이얼이 뜨는 순간 끊기지 않게.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BUTTON_ROW_HEIGHT.dp)
                .zoomDrag(dialState, pxPerLn, drag),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(visible = !dialState.isVisible, enter = fadeIn(), exit = fadeOut()) {
                ZoomButtons(range = range, ratio = ratio.value, onSelect = onSelect)
            }
        }
    }
}

private fun Modifier.zoomDrag(
    dialState: ZoomDialState,
    pxPerLn: Float,
    onDrag: (Float, Float) -> Unit,
): Modifier = pointerInput(dialState, pxPerLn) {
    detectHorizontalDragGestures(
        onDragStart = { dialState.startDrag() },
        onDragEnd = { dialState.endDrag() },
        onDragCancel = { dialState.endDrag() },
    ) { change, dragAmount ->
        change.consume()
        onDrag(dragAmount, pxPerLn)
    }
}

private infix fun Float.crossedBetween(span: Pair<Float, Float>): Boolean {
    val (a, b) = span
    return a != b && this > minOf(a, b) && this <= maxOf(a, b)
}

/** 갤럭시처럼 고른 버튼만 크게 `1x`로, 나머지는 작은 원에 숫자만 둔다. 버튼 사이 배율이면 아래쪽 버튼이 실제 배율을 보인다. */
@Composable
private fun ZoomButtons(range: ZoomRange, ratio: Float, onSelect: (Float) -> Unit) {
    val presets = range.presets()
    val active = presets.activePreset(ratio)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ZoomRowScrim)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { preset ->
            val isActive = preset == active
            val size by animateDpAsState(if (isActive) 40.dp else 32.dp, label = "zoomChip")
            val description = stringResource(R.string.camera_zoom_preset, formatZoom(preset))
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (isActive) ActiveChip else InactiveChip)
                    .clickable { onSelect(preset) }
                    .semantics {
                        contentDescription = description
                        selected = isActive
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isActive) formatZoom(ratio) else formatZoomShort(preset),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) CameraHighlight else CameraControlTint,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * 곡선 눈금 다이얼. 큰 원의 윗부분을 잘라 쓴다 — 눈금은 반지름 방향으로 서고 숫자는 그 바깥에 놓인다.
 * 눈금 사이 호의 길이가 드래그 거리와 같도록 각도를 잡아, 손가락 아래 눈금이 손가락과 함께 움직인다.
 */
@Composable
private fun ZoomDial(range: ZoomRange, ratio: State<Float>, pxPerLn: Float, modifier: Modifier = Modifier) {
    val ticks = remember(range) { range.ticks() }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = CameraControlTint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val valueStyle = TextStyle(color = CameraHighlight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val labels = remember(ticks) { ticks.mapNotNull { tick -> tick.label?.let { tick.ratio to measurer.measure(it, labelStyle) } }.toMap() }

    Canvas(modifier = modifier.clipToBounds().semantics { contentDescription = formatZoom(ratio.value) }.testTag(TAG_ZOOM_DIAL)) {
        val current = ratio.value
        val radius = size.width * DIAL_RADIUS_OF_WIDTH
        val arcTop = DIAL_ARC_TOP.dp.toPx()
        val center = Offset(size.width / 2, arcTop + radius)
        val visibleAngle = asin(((size.width / 2) / radius).coerceAtMost(1f))

        // 바깥 띠(숫자 자리)까지 덮는 반투명 반원 — 밝은 프리뷰 위에서도 눈금이 보이게 한다.
        drawCircle(DialScrim, radius = radius + 28.dp.toPx(), center = center)

        ticks.forEach { tick ->
            val angle = ((ln(tick.ratio) - ln(current)) * pxPerLn / radius)
            if (abs(angle) > visibleAngle) return@forEach
            // 가장자리로 갈수록 흐려져 호가 화면 밖으로 휘어 들어가는 느낌을 준다.
            val fade = 1f - (abs(angle) / visibleAngle).let { it * it }
            val isMajor = tick.label != null
            val inner = radius - (if (isMajor) 12.dp else 7.dp).toPx()
            drawLine(
                color = CameraControlTint.copy(alpha = fade * if (isMajor) 1f else 0.6f),
                start = center + polar(radius, angle),
                end = center + polar(inner, angle),
                strokeWidth = (if (isMajor) 2.dp else 1.dp).toPx(),
                cap = StrokeCap.Round,
            )
            labels[tick.ratio]?.let { layout ->
                // 지시선 바로 위는 현재 배율 자리다. 가까이 온 숫자는 비켜 흐려진다.
                val nearCenter = (abs(angle) * radius / CENTER_CLEAR_DP.dp.toPx()).coerceAtMost(1f)
                rotateRad(angle, pivot = center) {
                    drawText(
                        textLayoutResult = layout,
                        alpha = fade * nearCenter,
                        topLeft = Offset(
                            center.x - layout.size.width / 2,
                            center.y - radius - 8.dp.toPx() - layout.size.height,
                        ),
                    )
                }
            }
        }

        // 가운데 지시선과 그 위 현재 배율.
        drawLine(
            color = CameraHighlight,
            start = Offset(center.x, arcTop - 2.dp.toPx()),
            end = Offset(center.x, arcTop + 18.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val pointer = Path().apply {
            val tip = arcTop - 4.dp.toPx()
            moveTo(center.x, tip)
            lineTo(center.x - 5.dp.toPx(), tip - 7.dp.toPx())
            lineTo(center.x + 5.dp.toPx(), tip - 7.dp.toPx())
            close()
        }
        drawPath(pointer, CameraHighlight)
        val value = measurer.measure(formatZoom(current), valueStyle)
        drawText(
            textLayoutResult = value,
            topLeft = Offset(center.x - value.size.width / 2, arcTop - 34.dp.toPx() - value.size.height / 2 + 6.dp.toPx()),
        )
    }
}

/** 원의 꼭대기에서 [angle]만큼 시계 방향으로 돈 점. */
private fun polar(radius: Float, angle: Float) = Offset(radius * sin(angle), -radius * cos(angle))

private val BottomCenterOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)

private const val DIAL_HEIGHT = 120
private const val BUTTON_ROW_HEIGHT = 52
private const val DIAL_ARC_TOP = 62

/** 이만큼(dp) 끌면 배율이 e(≈2.7)배 바뀐다. 0.6x→10x가 화면 폭 남짓이다. */
private const val DIAL_DP_PER_LN = 150

/** 반지름을 화면 폭보다 크게 잡아 호가 완만하다. */
private const val DIAL_RADIUS_OF_WIDTH = 0.8f

private const val CENTER_CLEAR_DP = 28

private const val DIAL_HIDE_DELAY_MS = 1_500L

private val ZoomRowScrim = Color(0x33000000)
private val ActiveChip = Color(0x80000000)
private val InactiveChip = Color(0x59000000)
private val DialScrim = Color(0x66000000)
