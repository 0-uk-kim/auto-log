package com.example.autolog.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleDaoTest {

    private lateinit var db: AutoLogDatabase
    private lateinit var dao: SubtitleDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AutoLogDatabase::class.java,
        ).build()
        dao = db.subtitleDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun 클립의_자막은_시작_시각_순이다() = runTest {
        dao.upsert(subtitle(clipId = 1, startMs = 3_000))
        dao.upsert(subtitle(clipId = 1, startMs = 1_000))
        dao.upsert(subtitle(clipId = 2, startMs = 0))

        assertEquals(listOf(1_000L, 3_000L), dao.observeByClip(1).first().map { it.startMs })
    }

    @Test
    fun 같은_id로_저장하면_고친다() = runTest {
        dao.upsert(subtitle(clipId = 1, startMs = 0, text = "처음"))
        val stored = dao.observeByClip(1).first().single()

        dao.upsert(stored.copy(text = "고침"))

        assertEquals(listOf("고침"), dao.observeByClip(1).first().map { it.text })
    }

    @Test
    fun 원본이_사라진_클립의_자막은_걷힌다() = runTest {
        dao.upsert(subtitle(clipId = 1, startMs = 0))
        dao.upsert(subtitle(clipId = 2, startMs = 0))

        dao.deleteMissing(existingClipIds = listOf(1L))

        assertEquals(listOf(1L), dao.byClips(listOf(1L, 2L)).map { it.clipId })
    }

    @Test
    fun 삭제한_클립의_자막만_걷힌다() = runTest {
        dao.upsert(subtitle(clipId = 1, startMs = 0))
        dao.upsert(subtitle(clipId = 2, startMs = 0))

        dao.deleteByClips(listOf(2L))

        assertEquals(listOf(1L), dao.byClips(listOf(1L, 2L)).map { it.clipId })
    }

    private fun subtitle(clipId: Long, startMs: Long, text: String = "자막") =
        SubtitleEntity(clipId = clipId, startMs = startMs, endMs = startMs + 1_000, text = text)
}
