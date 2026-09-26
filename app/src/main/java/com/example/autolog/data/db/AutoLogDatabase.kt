package com.example.autolog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ClipOrderEntity::class, VlogEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AutoLogDatabase : RoomDatabase() {
    abstract fun clipOrderDao(): ClipOrderDao
    abstract fun vlogDao(): VlogDao

    companion object {
        const val NAME = "autolog.db"
    }
}
