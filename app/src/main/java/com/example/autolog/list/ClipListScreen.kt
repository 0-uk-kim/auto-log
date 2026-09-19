package com.example.autolog.list

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

private val END_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun ClipListScreen(
    date: String,
    onOpenClip: (clipIndex: Int) -> Unit,
    onCreateVlog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClipListViewModel = hiltViewModel(),
) {
    val clipsByDate by viewModel.clipsByDate.collectAsStateWithLifecycle()

    PlaceholderScreen(
        title = "영상 목록",
        route = "ClipList(date=$date) · ${viewModel::class.java.simpleName}",
        modifier = modifier,
    ) {
        // P3 #15에서 진짜 목록 UI로 교체된다. 지금은 날짜 묶기 결과를 눈으로 확인하는 용도다.
        val groups = clipsByDate
        if (groups == null) {
            Text("스캔 중…")
        } else {
            Text("${groups.size}개 날짜 · 클립 ${groups.values.sumOf { it.size }}건")
            groups.forEach { (day, clips) ->
                Text("$day (${clips.size})", style = MaterialTheme.typography.titleMedium)
                clips.forEach { clip -> Text(clip.toDebugLine()) }
            }
        }

        Button(onClick = { onOpenClip(0) }) { Text("클립 탭 → 미리보기") }
        Button(onClick = onCreateVlog) { Text("FAB · 브이로그 생성") }
        TextButton(onClick = onBack) { Text("뒤로") }
    }
}

private fun Clip.toDebugLine(): String {
    val zone = ZoneId.systemDefault()
    val started = startedAt.atZone(zone).format(END_TIME_FORMAT)
    val ended = endedAt.atZone(zone).format(END_TIME_FORMAT)
    val seconds = String.format(Locale.US, "%.1f", durationMs / 1000.0)
    return "$displayName · ${seconds}s · $started → $ended"
}
