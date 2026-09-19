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
        Room.databaseBuilder(context, AutoLogDatabase::class.java, AutoLogDatabase.NAME).build()

    @Provides
    fun provideClipOrderDao(database: AutoLogDatabase): ClipOrderDao = database.clipOrderDao()

    @Provides
    fun provideVlogDao(database: AutoLogDatabase): VlogDao = database.vlogDao()
}
