package com.example.autolog.data.subtitle

import com.example.autolog.data.db.SubtitleDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SubtitleRepository @Inject constructor(
    private val subtitleDao: SubtitleDao,
) {

    fun observe(clipId: Long): Flow<List<Subtitle>> =
        subtitleDao.observeByClip(clipId).map { rows -> rows.map { it.toSubtitle() } }

    /** 병합처럼 하루치를 한꺼번에 볼 때 쓴다. 자막이 없는 클립은 키가 없다. */
    suspend fun byClips(clipIds: List<Long>): Map<Long, List<Subtitle>> =
        subtitleDao.byClips(clipIds).map { it.toSubtitle() }.groupBy { it.clipId }

    suspend fun save(subtitle: Subtitle) = subtitleDao.upsert(subtitle.toEntity())

    suspend fun delete(subtitle: Subtitle) = subtitleDao.delete(subtitle.toEntity())
}
