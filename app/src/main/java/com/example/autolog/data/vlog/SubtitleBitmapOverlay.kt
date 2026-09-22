package com.example.autolog.data.vlog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.C
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.example.autolog.data.subtitle.Subtitle
import com.example.autolog.data.subtitle.SubtitleStyle
import com.example.autolog.data.subtitle.textAt
import kotlin.math.ceil

/**
 * 클립 하나의 자막을 그 클립 구간에만 영상에 새긴다. 모양은 화면 오버레이(`ui/SubtitleOverlay`)와
 * 같은 [SubtitleStyle] 비율을 따른다.
 *
 * Transformer는 효과에 **합성 전체 기준** 시각을 넘긴다 — 앞 클립 길이에 내부 시작 오프셋까지 더해져
 * 있어 클립 안의 위치를 바로 알 수 없다. 그래서 클립마다 이 객체를 따로 만들고, 처음 받은 프레임
 * 시각을 그 클립의 0으로 삼는다. 클립은 항상 처음부터 이어붙이므로(트리밍은 3차) 이 가정이 맞다.
 */
@UnstableApi
class SubtitleBitmapOverlay(private val subtitles: List<Subtitle>) : BitmapOverlay() {

    private var frameSize = Size(1, 1)
    private var originUs = C.TIME_UNSET
    private val rendered = HashMap<String, Bitmap>()

    // 자막이 없는 구간에도 무언가는 돌려줘야 한다. 투명한 한 점이라 보이지 않는다.
    private val blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    private val settings = StaticOverlaySettings.Builder()
        // 좌표는 -1(아래)..1(위). 자막 상자의 아래 가장자리를 영상 아래에서 여백만큼 띄운 곳에 붙인다.
        .setBackgroundFrameAnchor(0f, -1f + 2 * SubtitleStyle.BOTTOM_MARGIN_RATIO)
        .setOverlayFrameAnchor(0f, -1f)
        .build()

    override fun configure(videoSize: Size) {
        frameSize = videoSize
        rendered.clear()
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val text = subtitles.textAt(positionMs(presentationTimeUs)) ?: return blank
        return rendered.getOrPut(text) { render(text) }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings

    private fun positionMs(presentationTimeUs: Long): Long {
        if (originUs == C.TIME_UNSET) originUs = presentationTimeUs
        return (presentationTimeUs - originUs) / 1_000
    }

    private fun render(text: String): Bitmap {
        val height = frameSize.height.toFloat()
        val padding = height * SubtitleStyle.PADDING_RATIO
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SubtitleStyle.TEXT_COLOR
            textSize = height * SubtitleStyle.TEXT_SIZE_RATIO
            typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        }
        // 화면 쪽은 폭 상한 안에 여백까지 들어간다 — 같은 기준으로 글이 들어갈 폭을 뺀다.
        val maxTextWidth = (frameSize.width * SubtitleStyle.MAX_WIDTH_RATIO - padding * 4).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        val textWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }

        val bitmap = Bitmap.createBitmap(
            ceil(textWidth + padding * 4).toInt(),
            ceil(layout.height + padding * 2).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SubtitleStyle.BACKGROUND_COLOR }
        canvas.drawRoundRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), padding, padding, background)
        // 레이아웃은 최대 폭 기준으로 가운데 맞춰져 있다. 실제 글 폭만큼 줄인 상자 안으로 끌어온다.
        canvas.translate(padding * 2 - (maxTextWidth - textWidth) / 2, padding)
        layout.draw(canvas)
        return bitmap
    }
}
