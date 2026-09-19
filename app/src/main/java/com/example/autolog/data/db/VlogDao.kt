package com.example.autolog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface VlogDao {

    @Query("SELECT * FROM vlog WHERE date = :date")
    suspend fun byDate(date: LocalDate): VlogEntity?

    /** 달력의 '브이로그 생성됨' 마커가 이 목록을 본다 (#20). */
    @Query("SELECT * FROM vlog ORDER BY date DESC")
    fun observeAll(): Flow<List<VlogEntity>>

    /** 달력 마커는 날짜만 있으면 된다 — 결과물 전체를 읽을 이유가 없다 (#20). */
    @Query("SELECT date FROM vlog")
    suspend fun allDates(): List<LocalDate>

    /** 재생성은 덮어쓰기다 — 날짜가 기본 키라 REPLACE가 곧 그 정책이다. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vlog: VlogEntity)

    @Query("DELETE FROM vlog WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)
}
