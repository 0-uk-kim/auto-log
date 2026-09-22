package com.example.autolog.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ClipOrderEntity::class, VlogEntity::class, SubtitleEntity::class],
    version = 2,
    exportSchema = true,
    // v2: 자막 테이블 추가 (2차). 기존 테이블은 그대로라 자동 마이그레이션으로 충분하다.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AutoLogDatabase : RoomDatabase() {
    abstract fun clipOrderDao(): ClipOrderDao
    abstract fun vlogDao(): VlogDao
    abstract fun subtitleDao(): SubtitleDao

    companion object {
        const val NAME = "autolog.db"
    }
}
