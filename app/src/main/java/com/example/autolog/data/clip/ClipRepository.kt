package com.example.autolog.data.clip

import android.content.IntentSender
import com.example.autolog.data.db.ClipOrderDao
import com.example.autolog.data.db.ClipOrderEntity
import com.example.autolog.data.db.ClipSegmentDao
import com.example.autolog.data.db.ClipSegmentEntity
import com.example.autolog.data.db.VlogDao
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaStore(원본) ⊕ Room(사용자가 정한 순서)를 한 목록으로 합친다.
 *
 * 순서를 정한 적 없는 날짜에는 Room 행이 없고, 그때는 촬영 종료 시각 오름차순이 곧 순서다.
 * 순서를 정한 뒤 추가된 클립은 기존 순서를 밀지 않고 뒤에 붙는다 (planning 6 "순서의 기본값").
 */
@Singleton
class ClipRepository @Inject constructor(
    private val mediaStoreSource: ClipMediaStoreSource,
    private val clipOrderDao: ClipOrderDao,
    private val vlogDao: VlogDao,
    private val clipSegmentDao: ClipSegmentDao,
) {

    suspend fun clipsByDate(): Map<LocalDate, List<Clip>> {
        val clips = mediaStoreSource.loadClips()
        removeOrphanRows(clips)
        val segments = clipSegmentDao.byClips(clips.map { it.id }).groupBy { it.clipId }
        return clips
            .map { clip -> segments[clip.id]?.let { clip.withSegments(it) } ?: clip }
            .groupByRecordedDate(ZoneId.systemDefault())
            .mapValues { (date, ofDate) -> ofDate.applySavedOrder(clipOrderDao.byDate(date)) }
    }

    suspend fun clipsOn(date: LocalDate): List<Clip> = clipsByDate()[date].orEmpty()

    /**
     * 달력이 날짜마다 무엇을 표시할지 (#20).
     *
     * 둘은 포함 관계가 아니다 — 브이로그를 만든 뒤 원본 클립을 앱 밖에서 지우면
     * "영상은 없고 브이로그만 있는" 날짜가 남는다.
     */
    suspend fun calendarMarks(): CalendarMarks = CalendarMarks(
        datesWithClips = clipsByDate().keys,
        datesWithVlog = vlogDao.allDates().toSet(),
    )

    /**
     * 사용자가 정한 순서를 그날 것만 통째로 갈아 끼운다 (#16).
     *
     * 행을 갈아 끼우는 것이라 편집 여부도 같이 다시 써야 한다 — 빠뜨리면 순서를 바꿀 때마다
     * 편집 표시가 지워진다 (#18).
     */
    suspend fun saveOrder(date: LocalDate, clips: List<Clip>) {
        clipOrderDao.replaceDate(
            date,
            clips.mapIndexed { index, clip ->
                ClipOrderEntity(
                    clipId = clip.id,
                    date = date,
                    position = index,
                    isEdited = clip.isEdited,
                )
            },
        )
    }

    /** 남길 조각을 저장한다. 잘라낸 곳이 없으면 자르지 않은 것이라 행을 지운다 (#92). */
    suspend fun saveSegments(clip: Clip, segments: ClipSegments) {
        if (segments.coversWhole(clip.durationMs)) {
            clipSegmentDao.deleteByIds(listOf(clip.id))
        } else {
            clipSegmentDao.replaceClip(
                clip.id,
                segments.items.map { ClipSegmentEntity(clip.id, it.startMs, it.endMs) },
            )
        }
    }

    fun deleteRequest(clips: List<Clip>): IntentSender = mediaStoreSource.deleteRequest(clips)

    /**
     * 시스템 창에서 삭제가 승인된 뒤 순서·조각 행을 걷어낸다. 남은 클립의 순서는 행 사이 빈 자리를
     * 그대로 둬도 [applySavedOrder]가 position 순으로 읽어 유지된다.
     */
    suspend fun forgetDeleted(clips: List<Clip>) {
        val ids = clips.map { it.id }
        clipOrderDao.deleteByIds(ids)
        clipSegmentDao.deleteByIds(ids)
    }

    /**
     * 앱 밖에서 지워진 클립의 순서·조각 행을 걷어낸다.
     *
     * 스캔 결과가 비었을 때는 건드리지 않는다 — 진짜로 클립이 없는 것과 권한이 없어 못 읽은 것을
     * 여기서 구분할 수 없어서, 한 번의 빈 조회로 사용자가 정한 순서를 날리지 않게 한다.
     */
    private suspend fun removeOrphanRows(clips: List<Clip>) {
        if (clips.isEmpty()) return
        val ids = clips.map { it.id }
        clipOrderDao.deleteMissing(ids)
        clipSegmentDao.deleteMissing(ids)
    }
}

/**
 * 저장된 조각을 입힌다. 원본이 저장 뒤에 다른 길이로 바뀌었을 수 있어 지금 길이에 다시 맞춘다.
 * 맞추고 나서 전체가 되면 자른 것이 아니다.
 */
internal fun Clip.withSegments(rows: List<ClipSegmentEntity>): Clip {
    val segments = ClipSegments.of(rows.map { ClipSegment(it.startMs, it.endMs) }, durationMs)
    return copy(segments = segments.takeUnless { it.coversWhole(durationMs) })
}

/**
 * 저장된 순서를 실제 클립에 입힌다. 저장된 행 중 원본이 사라진 것은 자연히 빠지고,
 * 순서에 없는 클립은 종료 시각 순으로 뒤에 붙는다.
 *
 * 편집 여부도 같은 행에 있으므로 여기서 함께 입힌다 — 행이 없는 클립은 아직 손댄 적이 없다 (#18).
 */
internal fun List<Clip>.applySavedOrder(saved: List<ClipOrderEntity>): List<Clip> {
    if (saved.isEmpty()) return sortedBy { it.endedAt }
    val byId = associateBy { it.id }
    val ordered = saved.sortedBy { it.position }
        .mapNotNull { row -> byId[row.clipId]?.copy(isEdited = row.isEdited) }
    val orderedIds = ordered.mapTo(mutableSetOf()) { it.id }
    return ordered + filterNot { it.id in orderedIds }.sortedBy { it.endedAt }
}
