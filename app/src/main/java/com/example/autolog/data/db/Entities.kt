package com.example.autolog.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * 클립 하나의 자리. MediaStore가 원본이고, Room은 "사용자가 정한 순서"만 기억한다.
 *
 * 순서를 한 번도 건드리지 않은 날짜에는 행이 아예 없다 — 그때는 촬영 시각 오름차순이 곧 순서다
 * (planning 6 "순서의 기본값").
 */
@Entity(tableName = "clip_order", indices = [Index("date")])
data class ClipOrderEntity(
    /** MediaStore `_ID`. 앱 밖에서 원본이 지워지면 고아가 되므로 #14에서 정리한다. */
    @PrimaryKey val clipId: Long,
    val date: LocalDate,
    val position: Int,
    /** 1차는 편집 기능이 없어 항상 false. 나중 마이그레이션을 줄이려고 미리 둔다 (planning 5). */
    val isEdited: Boolean = false,
)

/** 날짜 하나가 브이로그 하나다. 재생성은 덮어쓰기라서 날짜가 곧 기본 키다 (planning 6 "브이로그 재생성"). */
@Entity(tableName = "vlog")
data class VlogEntity(
    @PrimaryKey val date: LocalDate,
    /** 병합 결과물의 MediaStore `_ID`. */
    val mediaId: Long,
    val createdAt: Instant,
)
