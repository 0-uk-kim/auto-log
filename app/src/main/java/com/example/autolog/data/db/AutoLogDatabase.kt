package com.example.autolog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2는 되돌린 자막 스키마(2차)라 다시 쓰지 않는다. 자막 빌드가 남은 기기도 같은 번호를 달면
 * 스키마가 달라 열리지 않으므로, 구간 자르기(#90)는 v3으로 올린다.
 * v4: 남길 구간이 하나(`clip_trim`)에서 여러 조각(`clip_segment`)이 됐다 (#92).
 */
@Database(
    entities = [ClipOrderEntity::class, VlogEntity::class, ClipSegmentEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AutoLogDatabase : RoomDatabase() {
    abstract fun clipOrderDao(): ClipOrderDao
    abstract fun vlogDao(): VlogDao
    abstract fun clipSegmentDao(): ClipSegmentDao

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

/** 구간 하나였던 것을 조각 하나로 옮긴다. v1·v2에서 오는 기기는 1→3·2→3을 거쳐 여기로 온다. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `clip_segment` (`clipId` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, " +
                "`endMs` INTEGER NOT NULL, PRIMARY KEY(`clipId`, `startMs`))",
        )
        db.execSQL("INSERT INTO `clip_segment` (`clipId`, `startMs`, `endMs`) SELECT `clipId`, `startMs`, `endMs` FROM `clip_trim`")
        db.execSQL("DROP TABLE `clip_trim`")
    }
}
