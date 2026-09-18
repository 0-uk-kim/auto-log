package com.example.autolog.list

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.autolog.ui.PlaceholderScreen

@Composable
fun ClipListScreen(
    date: String,
    onOpenClip: (clipIndex: Int) -> Unit,
    onCreateVlog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClipListViewModel = hiltViewModel(),
) {
    PlaceholderScreen(
        title = "영상 목록",
        route = "ClipList(date=$date) · ${viewModel::class.java.simpleName}",
        modifier = modifier,
    ) {
        Button(onClick = { onOpenClip(0) }) { Text("클립 탭 → 미리보기") }
        Button(onClick = onCreateVlog) { Text("FAB · 브이로그 생성") }
        TextButton(onClick = onBack) { Text("뒤로") }
    }
}
