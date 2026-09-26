package com.example.autolog.data.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.autolog.data.db.AutoLogDatabase
import com.example.autolog.data.db.ClipOrderDao
import com.example.autolog.data.db.VlogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutoLogDatabase =
        Room.databaseBuilder(context, AutoLogDatabase::class.java, AutoLogDatabase.NAME)
            // 자막(2차)을 걷어내며 스키마가 v2에서 v1로 내려갔다. 자막 버전을 쓰던 기기는
            // 내려온 DB를 열지 못해 실행하자마자 죽는다 — 그때는 새로 만든다.
            // 잃는 것은 순서와 브이로그 기록뿐이고, 원본과 결과물은 MediaStore에 그대로 있다.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideClipOrderDao(database: AutoLogDatabase): ClipOrderDao = database.clipOrderDao()

    @Provides
    fun provideVlogDao(database: AutoLogDatabase): VlogDao = database.vlogDao()
}
