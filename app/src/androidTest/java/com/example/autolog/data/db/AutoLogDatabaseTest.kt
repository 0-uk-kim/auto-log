package com.example.autolog.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoLogDatabaseTest {

    private lateinit var db: AutoLogDatabase
    private lateinit var clipOrderDao: ClipOrderDao
    private lateinit var vlogDao: VlogDao

    private val day = LocalDate.of(2026, 9, 19)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AutoLogDatabase::class.java,
        ).build()
        clipOrderDao = db.clipOrderDao()
        vlogDao = db.vlogDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun 순서는_position대로_돌아온다() = runTest {
        clipOrderDao.upsertAll(
            listOf(order(clipId = 3, position = 0), order(clipId = 1, position = 1)),
        )

        assertEquals(listOf(3L, 1L), clipOrderDao.byDate(day).map { it.clipId })
    }

    @Test
    fun isEdited는_기본값이_false다() = runTest {
        clipOrderDao.upsertAll(listOf(order(clipId = 1, position = 0)))

        assertEquals(false, clipOrderDao.byDate(day).single().isEdited)
    }

    @Test
    fun replaceDate는_그_날짜만_갈아_끼운다() = runTest {
        val otherDay = day.minusDays(1)
        clipOrderDao.upsertAll(
            listOf(order(clipId = 1, position = 0), order(clipId = 2, position = 0, date = otherDay)),
        )

        clipOrderDao.replaceDate(day, listOf(order(clipId = 9, position = 0)))

        assertEquals(listOf(9L), clipOrderDao.byDate(day).map { it.clipId })
        assertEquals(listOf(2L), clipOrderDao.byDate(otherDay).map { it.clipId })
    }

    @Test
    fun 원본이_사라진_클립의_행은_걷힌다() = runTest {
        clipOrderDao.upsertAll(
            listOf(order(clipId = 1, position = 0), order(clipId = 2, position = 1)),
        )

        clipOrderDao.deleteMissing(existingClipIds = listOf(1L))

        assertEquals(listOf(1L), clipOrderDao.byDate(day).map { it.clipId })
    }

    @Test
    fun 삭제한_클립의_행만_걷히고_남은_순서는_유지된다() = runTest {
        clipOrderDao.upsertAll(
            listOf(
                order(clipId = 1, position = 0),
                order(clipId = 2, position = 1),
                order(clipId = 3, position = 2),
            ),
        )

        clipOrderDao.deleteByIds(listOf(2L))

        assertEquals(listOf(1L, 3L), clipOrderDao.byDate(day).map { it.clipId })
    }

    @Test
    fun 브이로그는_날짜당_1건이라_재생성이_덮어쓴다() = runTest {
        vlogDao.upsert(VlogEntity(date = day, mediaId = 100, createdAt = Instant.ofEpochMilli(1)))
        vlogDao.upsert(VlogEntity(date = day, mediaId = 200, createdAt = Instant.ofEpochMilli(2)))

        val stored = vlogDao.byDate(day)

        assertEquals(200L, stored?.mediaId)
        assertEquals(Instant.ofEpochMilli(2), stored?.createdAt)
    }

    @Test
    fun 브이로그가_없는_날짜는_null이다() = runTest {
        assertNull(vlogDao.byDate(day))
    }

    private fun order(clipId: Long, position: Int, date: LocalDate = day) =
        ClipOrderEntity(clipId = clipId, date = date, position = position)
}
