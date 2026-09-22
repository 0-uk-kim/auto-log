package com.example.autolog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleDao {

    @Query("SELECT * FROM subtitle WHERE clipId = :clipId ORDER BY startMs, id")
    fun observeByClip(clipId: Long): Flow<List<SubtitleEntity>>

    @Query("SELECT * FROM subtitle WHERE clipId IN (:clipIds) ORDER BY startMs, id")
    fun observeByClips(clipIds: List<Long>): Flow<List<SubtitleEntity>>

    @Query("SELECT * FROM subtitle WHERE clipId IN (:clipIds) ORDER BY startMs, id")
    suspend fun byClips(clipIds: List<Long>): List<SubtitleEntity>

    @Upsert
    suspend fun upsert(subtitle: SubtitleEntity)

    @Delete
    suspend fun delete(subtitle: SubtitleEntity)

    @Query("DELETE FROM subtitle WHERE clipId NOT IN (:existingClipIds)")
    suspend fun deleteMissing(existingClipIds: List<Long>)

    @Query("DELETE FROM subtitle WHERE clipId IN (:clipIds)")
    suspend fun deleteByClips(clipIds: List<Long>)
}
