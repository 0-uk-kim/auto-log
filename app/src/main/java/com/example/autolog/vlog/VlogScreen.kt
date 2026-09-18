package com.example.autolog.vlog

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.autolog.ui.PlaceholderScreen

@Composable
fun VlogScreen(
    date: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VlogViewModel = hiltViewModel(),
) {
    PlaceholderScreen(
        title = "브이로그",
        route = "Vlog(date=$date) · ${viewModel::class.java.simpleName}",
        modifier = modifier,
    ) {
        TextButton(onClick = onBack) { Text("뒤로") }
    }
}
