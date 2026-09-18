package com.example.autolog.camera

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.autolog.ui.PlaceholderScreen

@Composable
fun CameraScreen(
    onOpenClipList: () -> Unit,
    onOpenLatestClip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    PlaceholderScreen(
        title = "카메라",
        route = "Camera · ${viewModel::class.java.simpleName}",
        modifier = modifier,
    ) {
        Button(onClick = onOpenLatestClip) { Text("좌측 하단 · 직전 촬영본") }
        Button(onClick = onOpenClipList) { Text("우측 하단 · 영상 목록") }
    }
}
