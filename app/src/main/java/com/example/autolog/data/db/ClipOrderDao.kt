package com.example.autolog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipOrderDao {

    @Query("SELECT * FROM clip_order WHERE date = :date ORDER BY position")
    fun observeByDate(date: LocalDate): Flow<List<ClipOrderEntity>>

    @Query("SELECT * FROM clip_order WHERE date = :date ORDER BY position")
    suspend fun byDate(date: LocalDate): List<ClipOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(orders: List<ClipOrderEntity>)

    /** 날짜 하나의 순서를 통째로 갈아 끼운다 — 드래그 결과는 부분 갱신이 아니라 새 배열이다 (#16). */
    @Transaction
    suspend fun replaceDate(date: LocalDate, orders: List<ClipOrderEntity>) {
        deleteByDate(date)
        upsertAll(orders)
    }

    @Query("DELETE FROM clip_order WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)

    /** 앱 밖에서 원본이 지워진 클립의 고아 행을 걷어낸다 (#14). */
    @Query("DELETE FROM clip_order WHERE clipId NOT IN (:existingClipIds)")
    suspend fun deleteMissing(existingClipIds: List<Long>)

    @Query("DELETE FROM clip_order WHERE clipId IN (:clipIds)")
    suspend fun deleteByIds(clipIds: List<Long>)
}
