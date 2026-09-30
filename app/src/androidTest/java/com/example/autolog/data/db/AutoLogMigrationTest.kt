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
 * 옛 DB를 v3 앱이 열었을 때 기존 순서가 남고 구간을 저장할 수 있는지 본다 (#90).
 *
 * room-testing의 MigrationTestHelper는 앱이 쓰는 kotlinx-serialization과 맞지 않아
 * 스키마 JSON을 읽다 AbstractMethodError로 죽는다. 그래서 옛 DB를 SQL 그대로 손으로 만들고,
 * 앱과 같은 마이그레이션을 단 Room 빌더로 연다.
 */
@RunWith(AndroidJUnit4::class)
class AutoLogMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val day = LocalDate.of(2026, 9, 29)

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun v1에서_올라와도_순서가_남고_구간을_저장할_수_있다() = runTest {
        createOldDatabase(version = 1, V1_SCHEMA)

        assertOrderKeptAndTrimWorks()
    }

    /** 되돌린 자막 빌드(v2)가 깔렸던 기기. 같은 번호를 다시 쓰면 여기서 열리지 않는다. */
    @Test
    fun 자막_v2에서_올라와도_순서가_남고_구간을_저장할_수_있다() = runTest {
        createOldDatabase(version = 2, V1_SCHEMA + SUBTITLE_SCHEMA)

        assertOrderKeptAndTrimWorks()
    }

    private fun createOldDatabase(version: Int, schema: List<String>) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB_NAME), null).use { db ->
            schema.forEach(db::execSQL)
            db.execSQL("INSERT INTO clip_order (clipId, date, position, isEdited) VALUES (7, '$day', 0, 0)")
            db.version = version
        }
    }

    private suspend fun assertOrderKeptAndTrimWorks() {
        val room = Room.databaseBuilder(context, AutoLogDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_3, MIGRATION_2_3)
            .build()
        try {
            assertEquals(listOf(7L), room.clipOrderDao().byDate(day).map { it.clipId })

            room.clipTrimDao().upsert(ClipTrimEntity(clipId = 7, startMs = 500, endMs = 2_000))
            assertEquals(
                listOf(ClipTrimEntity(clipId = 7, startMs = 500, endMs = 2_000)),
                room.clipTrimDao().byClips(listOf(7L)),
            )
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

        val SUBTITLE_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `subtitle` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`clipId` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `text` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_subtitle_clipId` ON `subtitle` (`clipId`)",
        )
    }
}
