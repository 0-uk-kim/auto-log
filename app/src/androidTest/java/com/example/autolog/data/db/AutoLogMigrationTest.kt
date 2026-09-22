package com.example.autolog.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1로 쓰던 DB를 새 버전 앱이 열었을 때 자동 마이그레이션이 돌고 기존 순서가 남는지 본다.
 *
 * room-testing의 MigrationTestHelper는 앱이 쓰는 kotlinx-serialization과 맞지 않아
 * 스키마 JSON을 읽다 AbstractMethodError로 죽는다. 그래서 v1 DB를 `schemas/.../1.json`의
 * SQL 그대로 손으로 만들고, 실제 Room 빌더로 연다 — 앱이 겪는 경로와 같다.
 */
@RunWith(AndroidJUnit4::class)
class AutoLogMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun v1의_순서는_v2로_올려도_남고_자막을_저장할_수_있다() = runTest {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB_NAME), null).use { db ->
            V1_SCHEMA.forEach(db::execSQL)
            db.execSQL("INSERT INTO clip_order (clipId, date, position, isEdited) VALUES (7, '2026-09-23', 0, 0)")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AutoLogDatabase::class.java, DB_NAME).build()
        try {
            assertEquals(listOf(7L), room.clipOrderDao().byDate(LocalDate.of(2026, 9, 23)).map { it.clipId })

            room.subtitleDao().upsert(SubtitleEntity(clipId = 7, startMs = 0, endMs = 1_000, text = "안녕"))
            assertEquals(listOf("안녕"), room.subtitleDao().byClips(listOf(7L)).map { it.text })
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"

        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `clip_order` (`clipId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `isEdited` INTEGER NOT NULL, PRIMARY KEY(`clipId`))",
            "CREATE INDEX IF NOT EXISTS `index_clip_order_date` ON `clip_order` (`date`)",
            "CREATE TABLE IF NOT EXISTS `vlog` (`date` TEXT NOT NULL, `mediaId` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`date`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8d1816089fa32b55ac0b6f7101339c61')",
        )
    }
}
