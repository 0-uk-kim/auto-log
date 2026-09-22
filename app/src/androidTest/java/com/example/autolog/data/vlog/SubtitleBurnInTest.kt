package com.example.autolog.data.vlog

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.autolog.data.clip.Clip
import com.example.autolog.data.clip.ClipMediaStoreSource
import com.example.autolog.data.subtitle.Subtitle
import com.example.autolog.data.subtitle.SubtitleStyle
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 자막이 **그 클립의 그 구간에만** 새겨지는지 본다. 같은 클립을 자막 없이 한 번, 있이 한 번 병합해
 * 자막 자리의 픽셀 차이를 잰다 — 구간 안에서는 크게, 밖에서는 인코딩 잡음 수준이어야 한다.
 *
 * 두 번째 클립에 자막을 거는 것이 핵심이다. 효과가 받는 시각은 합성 전체 기준이라, 클립 안의
 * 시각으로 옮기지 못하면 두 번째 클립부터 자막이 엉뚱한 때 뜬다.
 */
@RunWith(AndroidJUnit4::class)
class SubtitleBurnInTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun grantMediaRead() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_VIDEO)
    }

    @Test
    fun 두_번째_클립의_자막은_그_클립_구간에만_새겨진다() = runBlocking {
        val clips = ClipMediaStoreSource(context.contentResolver, Dispatchers.IO).loadClips()
            .filter { it.durationMs >= 3_000 }
            .take(2)
        assumeTrue("3초 이상인 클립이 2건 있어야 한다", clips.size == 2)
        val (first, second) = clips

        val subtitle = Subtitle(clipId = second.id, startMs = 0, endMs = 1_500, text = "자막 새기기 확인")
        val plain = merge(clips, "burn-in-plain.mp4", emptyMap())
        val burned = merge(clips, "burn-in-subtitle.mp4", mapOf(second.id to listOf(subtitle)))

        val inside = subtitleAreaDiff(plain, burned, atMs = first.durationMs + 700)
        val outside = subtitleAreaDiff(plain, burned, atMs = first.durationMs + 2_500)
        val firstClip = subtitleAreaDiff(plain, burned, atMs = 700)

        assertTrue("자막 구간 안인데 차이가 작다: $inside", inside > 25)
        assertTrue("자막 구간이 끝났는데 차이가 크다: $outside", outside < 10)
        assertTrue("자막이 없는 첫 클립에 차이가 크다: $firstClip", firstClip < 10)
    }

    private suspend fun merge(clips: List<Clip>, name: String, subtitles: Map<Long, List<Subtitle>>): File {
        val output = File(context.cacheDir, name).apply { delete() }
        ClipMerger(context).merge(clips, output.absolutePath, subtitles)
        return output
    }

    /** 자막 상자가 놓이는 영상 하단 가운데 띠의 평균 밝기 차이(0..255). */
    private fun subtitleAreaDiff(a: File, b: File, atMs: Long): Double {
        val frameA = frameAt(a, atMs)
        val frameB = frameAt(b, atMs)
        val bottom = (frameA.height * (1 - SubtitleStyle.BOTTOM_MARGIN_RATIO)).toInt() - 2
        val top = bottom - (frameA.height * SubtitleStyle.TEXT_SIZE_RATIO).toInt()
        val left = (frameA.width * 0.4).toInt()
        val right = (frameA.width * 0.6).toInt()
        var sum = 0.0
        var count = 0
        for (y in top until bottom step 2) {
            for (x in left until right step 2) {
                sum += abs(luma(frameA.getPixel(x, y)) - luma(frameB.getPixel(x, y)))
                count++
            }
        }
        return sum / count
    }

    private fun frameAt(file: File, atMs: Long): Bitmap = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(file.absolutePath)
        checkNotNull(retriever.getFrameAtTime(atMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST))
    }

    private fun luma(pixel: Int): Double =
        0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)
}
