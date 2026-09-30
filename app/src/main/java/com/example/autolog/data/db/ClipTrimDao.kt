package com.example.autolog.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ClipTrimDao {

    @Query("SELECT * FROM clip_trim WHERE clipId IN (:clipIds)")
    suspend fun byClips(clipIds: List<Long>): List<ClipTrimEntity>

    @Upsert
    suspend fun upsert(trim: ClipTrimEntity)

    @Query("DELETE FROM clip_trim WHERE clipId NOT IN (:existingClipIds)")
    suspend fun deleteMissing(existingClipIds: List<Long>)

    @Query("DELETE FROM clip_trim WHERE clipId IN (:clipIds)")
    suspend fun deleteByIds(clipIds: List<Long>)
}
