package com.example.autolog.data.vlog

import android.Manifest
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.autolog.data.clip.Clip
import com.example.autolog.data.clip.ClipMediaStoreSource
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 기기에 실제로 찍혀 있는 클립으로 병합을 돌린다 — 이 단계의 불확실함은 코덱·컨테이너에 있어서
 * 가짜 입력으로는 확인되지 않는다 (tasks.md의 #25 리스크 표시).
 */
@RunWith(AndroidJUnit4::class)
class ClipMergerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * 테스트가 끝나면 앱이 지워져 MediaStore 소유권이 풀린다 — 다음 회차에는 권한 없이는
     * 자기가 찍은 클립도 못 읽는다 (#35와 같은 상황). 그래서 매번 직접 받아 둔다.
     */
    @Before
    fun grantMediaRead() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, permission)
    }

    @Test
    fun 클립을_순서대로_이어붙인다() = runBlocking {
        val clips = loadClips().take(3)
        assumeTrue("앱으로 촬영한 클립이 2건 이상 있어야 한다", clips.size >= 2)

        val output = File(context.cacheDir, "merge-test.mp4")
        output.delete()

        val result = ClipMerger(context).merge(clips, output.absolutePath)

        assertTrue("결과 파일이 만들어져야 한다", output.exists() && output.length() > 0)

        // 이어붙인 길이는 원본 길이의 합이다. 프레임 경계 때문에 딱 떨어지지는 않는다.
        val expected = clips.sumOf { it.durationMs }
        assertTrue(
            "길이 ${result.durationMs}ms 가 합계 ${expected}ms 와 크게 다르다",
            abs(result.durationMs - expected) < 1_000,
        )

        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(output.absolutePath)
            // 원본 오디오를 그대로 이어붙인다 (planning 6 "오디오 처리").
            assertEquals(
                "yes",
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO),
            )
            assertEquals(
                "video/mp4",
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            )
        }
    }

    private suspend fun loadClips(): List<Clip> =
        ClipMediaStoreSource(context.contentResolver, Dispatchers.IO).loadClips()
}
