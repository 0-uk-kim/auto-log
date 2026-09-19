package com.example.autolog.vlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.autolog.R
import com.example.autolog.data.vlog.VlogFailure
import com.example.autolog.list.formatClipDuration
import com.example.autolog.ui.VideoSurface
import com.example.autolog.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_VLOG_PROGRESS = "vlog-progress"
const val TAG_VLOG_DONE = "vlog-done"
const val TAG_REGENERATE_ALERT = "vlog-regenerate-alert"
const val TAG_VLOG_SHARE = "vlog-share"
const val TAG_VLOG_FAILED = "vlog-failed"

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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.vlog_saved_to_gallery)
    val openLabel = stringResource(R.string.vlog_open_gallery)

    // 갓 만들어진 것과 예전에 만들어 둔 것을 가른다 — 들어올 때마다 "저장했어요"라고 하면
    // 방금 무슨 일이 있었는지가 묻힌다.
    var justMerged by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is VlogUiState.Running) justMerged = true
        if (state is VlogUiState.Done && justMerged) {
            justMerged = false
            val action = snackbarHostState.showSnackbar(
                message = savedMessage,
                actionLabel = openLabel,
                // 버튼이 붙으면 기본이 Indefinite라 안내가 안 사라지고 아래 버튼을 계속 가린다.
                duration = SnackbarDuration.Long,
            )
            if (action == SnackbarResult.ActionPerformed) {
                context.openInGallery(state.uri.toUri())
            }
        }
    }

    // FAB의 뜻이 '생성'이라 들어온 것 자체가 시작 신호다. 단, 이미 만들어 둔 날짜는
    // Idle이 아니므로 여기서 다시 만들어지지 않는다 — 덮어쓰기는 확인을 받고 한다.
    LaunchedEffect(uiState) {
        if (uiState == VlogUiState.Idle) viewModel.createVlog()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    onShare = { context.shareVlog(state.uri.toUri()) },
                )

                is VlogUiState.Failed -> Failed(
                    reason = state.reason,
                    onRetry = viewModel::createVlog,
                )
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

/**
 * 완성된 브이로그를 그 자리에서 확인한다 (planning 3-5 "생성 결과를 미리보기로 확인").
 *
 * 공유·갤러리 저장은 P7에서 이 아래에 붙는다.
 */
@Composable
private fun Done(
    state: VlogUiState.Done,
    onRegenerate: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_VLOG_DONE),
    ) {
        VlogPlayer(
            uri = state.uri,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.vlog_done_duration,
                formatClipDuration(state.durationMs),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 저장은 만들 때 이미 끝나 있다 — 어디에 있는지 알려 주는 줄이다 (planning 3-6).
        Text(
            text = stringResource(R.string.vlog_gallery_location),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = onRegenerate) {
                Text(stringResource(R.string.vlog_regenerate))
            }
            Button(onClick = onShare, modifier = Modifier.testTag(TAG_VLOG_SHARE)) {
                Text(stringResource(R.string.vlog_share))
            }
        }
    }
}

@Composable
private fun VlogPlayer(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(player, uri) {
        player.setMediaItem(MediaItem.fromUri(uri.toUri()))
        player.prepare()
        // 방금 만든 결과물이라 확인하려고 들어온 것이다 — 누르지 않아도 바로 튼다.
        player.playWhenReady = true
    }

    // 화면을 벗어나면 소리부터 멈춘다.
    LifecycleResumeEffect(player) {
        onPauseOrDispose { player.pause() }
    }

    VideoSurface(player = player, modifier = modifier)
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

/**
 * 실패는 이유마다 사용자가 할 일이 다르다 (#32).
 * 클립이 없으면 다시 시도해도 같은 결과라 버튼을 주지 않는다.
 */
@Composable
private fun Failed(reason: VlogFailure, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.testTag(TAG_VLOG_FAILED),
    ) {
        Text(
            text = stringResource(
                when (reason) {
                    VlogFailure.NoClips -> R.string.vlog_failed_no_clips
                    VlogFailure.NotEnoughStorage -> R.string.vlog_failed_storage
                    VlogFailure.MergeFailed -> R.string.vlog_failed
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (reason != VlogFailure.NoClips) {
            Button(onClick = onRetry) { Text(stringResource(R.string.vlog_retry)) }
        }
    }
}

@Composable
private fun rememberDateTitle(date: String): String {
    val pattern = stringResource(R.string.clip_list_date_pattern)
    return remember(date, pattern) {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
}
