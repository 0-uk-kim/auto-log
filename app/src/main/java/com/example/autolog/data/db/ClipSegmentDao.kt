package com.example.autolog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ClipSegmentDao {

    @Query("SELECT * FROM clip_segment WHERE clipId IN (:clipIds) ORDER BY clipId, startMs")
    suspend fun byClips(clipIds: List<Long>): List<ClipSegmentEntity>

    @Insert
    suspend fun insertAll(segments: List<ClipSegmentEntity>)

    /** 클립 하나의 조각을 통째로 갈아 끼운다 — 편집 결과는 부분 갱신이 아니라 새 목록이다. */
    @Transaction
    suspend fun replaceClip(clipId: Long, segments: List<ClipSegmentEntity>) {
        deleteByIds(listOf(clipId))
        insertAll(segments)
    }

    @Query("DELETE FROM clip_segment WHERE clipId NOT IN (:existingClipIds)")
    suspend fun deleteMissing(existingClipIds: List<Long>)

    @Query("DELETE FROM clip_segment WHERE clipId IN (:clipIds)")
    suspend fun deleteByIds(clipIds: List<Long>)
}
