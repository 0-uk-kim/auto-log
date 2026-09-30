package com.example.autolog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2는 되돌린 자막 스키마(2차)라 다시 쓰지 않는다. 자막 빌드가 남은 기기도 같은 번호를 달면
 * 스키마가 달라 열리지 않으므로, 구간 자르기(#90)는 v3으로 올린다.
 */
@Database(
    entities = [ClipOrderEntity::class, VlogEntity::class, ClipTrimEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AutoLogDatabase : RoomDatabase() {
    abstract fun clipOrderDao(): ClipOrderDao
    abstract fun vlogDao(): VlogDao
    abstract fun clipTrimDao(): ClipTrimDao

    companion object {
        const val NAME = "autolog.db"
    }
}

private const val CREATE_CLIP_TRIM =
    "CREATE TABLE IF NOT EXISTS `clip_trim` (`clipId` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, " +
        "`endMs` INTEGER NOT NULL, PRIMARY KEY(`clipId`))"

val MIGRATION_1_3 = object : Migration(1, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_CLIP_TRIM)
    }
}

/** 자막 빌드에서 올라오는 기기. 자막은 이제 쓰지 않으니 버리고, 순서와 브이로그 기록은 그대로 둔다. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `subtitle`")
        db.execSQL(CREATE_CLIP_TRIM)
    }
}
