package com.example.autolog.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import com.example.autolog.R
import com.example.autolog.data.clip.Clip

/**
 * 방금 지운 클립에 실행취소 스낵바를 띄운다 (#102). 스낵바가 그냥 닫히면 그때 원본을 지운다.
 * 놓치면 되돌릴 수 없어 길게(10초) 띄운다.
 *
 * 스낵바가 떠 있는 채로 화면을 옮기면 이 효과가 취소될 뿐 삭제는 기다리는 그대로라, 옮겨 간 화면이
 * 같은 스낵바를 다시 띄운다 — 편집 화면에서 지우고 카메라로 돌아오는 흐름이 이렇게 이어진다.
 */
@Composable
fun UndoDeletionEffect(
    pending: List<Clip>,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onCommit: () -> Unit,
) {
    val message = if (pending.size > 1) {
        stringResource(R.string.deleted_clips, pending.size)
    } else {
        stringResource(R.string.deleted_clip)
    }
    val undoLabel = stringResource(R.string.deleted_undo)
    val undo by rememberUpdatedState(onUndo)
    val commit by rememberUpdatedState(onCommit)
    LaunchedEffect(pending) {
        if (pending.isEmpty()) return@LaunchedEffect
        when (hostState.showSnackbar(message, undoLabel, duration = SnackbarDuration.Long)) {
            SnackbarResult.ActionPerformed -> undo()
            SnackbarResult.Dismissed -> commit()
        }
    }
}
