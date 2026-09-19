package com.example.autolog.list

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autolog.data.clip.Clip
import com.example.autolog.ui.PlaceholderScreen
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val END_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")

@Composable
fun ClipListScreen(
    date: String,
    onOpenClip: (clipIndex: Int) -> Unit,
    onCreateVlog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClipListViewModel = hiltViewModel(),
) {
    val clips by viewModel.clips.collectAsStateWithLifecycle()

    PlaceholderScreen(
        title = "영상 목록",
        route = "ClipList(date=$date) · ${viewModel::class.java.simpleName}",
        modifier = modifier,
    ) {
        // P3 #15에서 진짜 목록 UI로 교체된다. 지금은 스캔 결과를 눈으로 확인하는 용도다.
        Text(clips?.let { "스캔된 앱 촬영분 ${it.size}건" } ?: "스캔 중…")
        clips?.forEach { clip -> Text(clip.toDebugLine()) }

        Button(onClick = { onOpenClip(0) }) { Text("클립 탭 → 미리보기") }
        Button(onClick = onCreateVlog) { Text("FAB · 브이로그 생성") }
        TextButton(onClick = onBack) { Text("뒤로") }
    }
}

private fun Clip.toDebugLine(): String {
    val endedAt = endedAt.atZone(ZoneId.systemDefault()).format(END_TIME_FORMAT)
    val seconds = durationMs / 1000.0
    return "$displayName · ${String.format(Locale.US, "%.1f", seconds)}s · 종료 $endedAt"
}
