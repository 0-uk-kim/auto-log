package com.example.autolog.vlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autolog.R
import com.example.autolog.list.formatClipDuration
import com.example.autolog.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_VLOG_PROGRESS = "vlog-progress"
const val TAG_VLOG_DONE = "vlog-done"
const val TAG_REGENERATE_ALERT = "vlog-regenerate-alert"

/**
 * 브이로그 생성 화면 (planning 3-5).
 *
 * 병합은 WorkManager가 들고 있어서 이 화면을 벗어나도 계속 돈다. 화면은 진행률만 구독한다.
 * 결과물 재생은 #29에서 이 위에 얹힌다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlogScreen(
    date: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VlogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showAlert by viewModel.showRegenerateAlert.collectAsStateWithLifecycle()

    // FAB의 뜻이 '생성'이라 들어온 것 자체가 시작 신호다. 단, 이미 만들어 둔 날짜는
    // Idle이 아니므로 여기서 다시 만들어지지 않는다 — 덮어쓰기는 확인을 받고 한다.
    LaunchedEffect(uiState) {
        if (uiState == VlogUiState.Idle) viewModel.createVlog()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(rememberDateTitle(date)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.vlog_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                VlogUiState.Loading -> Unit

                VlogUiState.Idle,
                is VlogUiState.Running,
                -> Merging(percent = (state as? VlogUiState.Running)?.percent ?: 0)

                is VlogUiState.Done -> Done(
                    state = state,
                    onRegenerate = viewModel::requestRegenerate,
                )

                VlogUiState.Failed -> Failed(onRetry = viewModel::createVlog)
            }
        }

        if (showAlert) {
            RegenerateAlert(
                onConfirm = viewModel::confirmRegenerate,
                onDismiss = viewModel::dismissRegenerateAlert,
            )
        }
    }
}

@Composable
private fun Merging(percent: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.testTag(TAG_VLOG_PROGRESS),
    ) {
        Text(
            text = stringResource(R.string.vlog_merging),
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.vlog_percent, percent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.vlog_keep_running),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Done(state: VlogUiState.Done, onRegenerate: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.testTag(TAG_VLOG_DONE),
    ) {
        Text(
            text = stringResource(R.string.vlog_done),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(
                R.string.vlog_done_duration,
                formatClipDuration(state.durationMs),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onRegenerate,
            modifier = Modifier.padding(top = Spacing.md),
        ) {
            Text(stringResource(R.string.vlog_regenerate))
        }
    }
}

/**
 * 재생성은 되돌릴 수 없다 — 날짜당 브이로그는 1개라 새로 만들면 기존 결과물이 갤러리에서
 * 사라진다 (planning 6 "브이로그 재생성"). 그래서 확인을 받고서야 시작한다.
 */
@Composable
private fun RegenerateAlert(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vlog_regenerate_title)) },
        text = { Text(stringResource(R.string.vlog_regenerate_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.vlog_regenerate_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.vlog_regenerate_cancel))
            }
        },
        modifier = Modifier.testTag(TAG_REGENERATE_ALERT),
    )
}

@Composable
private fun Failed(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.vlog_failed),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.vlog_retry)) }
    }
}

@Composable
private fun rememberDateTitle(date: String): String {
    val pattern = stringResource(R.string.clip_list_date_pattern)
    return remember(date, pattern) {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
}
