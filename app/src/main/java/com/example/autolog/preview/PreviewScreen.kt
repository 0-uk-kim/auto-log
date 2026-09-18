package com.example.autolog.preview

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.autolog.navigation.Preview
import com.example.autolog.ui.PlaceholderScreen

@Composable
fun PreviewScreen(
    date: String,
    clipIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val start = if (clipIndex == Preview.LATEST_CLIP) "직전 촬영본" else "$clipIndex 번째"
    PlaceholderScreen(
        title = "미리보기 재생",
        route = "Preview(date=$date, start=$start) · ${viewModel::class.java.simpleName}",
        modifier = modifier,
    ) {
        TextButton(onClick = onBack) { Text("뒤로") }
    }
}
