package com.example.autolog.vlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.autolog.data.db.VlogDao
import com.example.autolog.data.vlog.VlogStore
import com.example.autolog.data.vlog.VlogWorker
import com.example.autolog.navigation.Vlog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VlogViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val vlogStore: VlogStore,
    vlogDao: VlogDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val date: LocalDate = LocalDate.parse(savedStateHandle.toRoute<Vlog>().date)

    private val _showRegenerateAlert = MutableStateFlow(false)
    val showRegenerateAlert = _showRegenerateAlert.asStateFlow()

    /**
     * 작업 상태는 WorkManager가, "이미 만들어 둔 것"은 Room이 들고 있다. 둘 다 봐야 한다 —
     * 앱을 다시 켜면 작업 기록은 정리되어도 결과물은 그대로 남아 있기 때문이다 (#28).
     */
    val uiState = combine(
        workManager.getWorkInfosForUniqueWorkFlow(VlogWorker.workName(date)),
        vlogDao.observeByDate(date),
    ) { infos, saved -> infos.lastOrNull() to saved }
        .mapLatest { (info, saved) -> resolve(info, saved != null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VlogUiState.Loading)

    private suspend fun resolve(info: WorkInfo?, hasSavedRecord: Boolean): VlogUiState = when {
        info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED ->
            VlogUiState.Running(percent = 0)

        info?.state == WorkInfo.State.RUNNING ->
            VlogUiState.Running(percent = info.progress.getInt(VlogWorker.KEY_PROGRESS, 0))

        hasSavedRecord -> vlogStore.saved(date)
            ?.let { VlogUiState.Done(it.uri.toString(), it.durationMs) }
        // 기록은 있는데 원본이 앱 밖에서 지워진 경우다. 없는 것으로 보고 다시 만들게 둔다.
            ?: VlogUiState.Idle

        info?.state == WorkInfo.State.FAILED || info?.state == WorkInfo.State.CANCELLED ->
            VlogUiState.Failed

        else -> VlogUiState.Idle
    }

    /** 만든 적 없는 날짜의 첫 생성. 되물을 것이 없다. */
    fun createVlog() {
        VlogWorker.enqueue(workManager, date)
    }

    fun requestRegenerate() {
        _showRegenerateAlert.value = true
    }

    fun dismissRegenerateAlert() {
        _showRegenerateAlert.value = false
    }

    /** 얼럿에서 확인을 받은 뒤에만 부른다 — 여기서 기존 결과물이 교체된다 (planning 6). */
    fun confirmRegenerate() {
        _showRegenerateAlert.value = false
        VlogWorker.enqueue(workManager, date, replaceExisting = true)
    }
}
