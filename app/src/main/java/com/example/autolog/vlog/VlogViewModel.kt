package com.example.autolog.vlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.autolog.data.vlog.VlogWorker
import com.example.autolog.navigation.Vlog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class VlogViewModel @Inject constructor(
    private val workManager: WorkManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val date: LocalDate = LocalDate.parse(savedStateHandle.toRoute<Vlog>().date)

    /**
     * 작업 상태는 WorkManager가 원본이다 — 화면이 죽었다 살아나도 여기서 다시 읽으면 되므로
     * 진행 상황을 ViewModel이 따로 기억하지 않는다.
     */
    val uiState = workManager
        .getWorkInfosForUniqueWorkFlow(VlogWorker.workName(date))
        .map { infos -> infos.lastOrNull().toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VlogUiState.Idle)

    fun createVlog() {
        VlogWorker.enqueue(workManager, date)
    }
}

private fun WorkInfo?.toUiState(): VlogUiState = when (this?.state) {
    null -> VlogUiState.Idle
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
        VlogUiState.Running(percent = 0)

    WorkInfo.State.RUNNING ->
        VlogUiState.Running(percent = progress.getInt(VlogWorker.KEY_PROGRESS, 0))

    WorkInfo.State.SUCCEEDED -> {
        val path = outputData.getString(VlogWorker.KEY_OUTPUT_PATH)
        if (path == null) {
            VlogUiState.Failed
        } else {
            VlogUiState.Done(path, outputData.getLong(VlogWorker.KEY_DURATION_MS, 0L))
        }
    }

    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> VlogUiState.Failed
}
