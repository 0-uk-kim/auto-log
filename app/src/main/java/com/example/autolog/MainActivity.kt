package com.example.autolog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.example.autolog.camera.CameraViewModel
import com.example.autolog.list.ClipListViewModel
import com.example.autolog.preview.PreviewViewModel
import com.example.autolog.ui.theme.AutoLogTheme
import com.example.autolog.vlog.VlogViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoLogTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DiSkeletonScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// P0 #4에서 Navigation 그래프로 교체된다. 그 전까지 Hilt 그래프가 실제로 해석되는지 눈으로 확인하는 화면.
@Composable
private fun DiSkeletonScreen(modifier: Modifier = Modifier) {
    val viewModels: List<ViewModel> = listOf(
        hiltViewModel<CameraViewModel>(),
        hiltViewModel<ClipListViewModel>(),
        hiltViewModel<PreviewViewModel>(),
        hiltViewModel<VlogViewModel>(),
    )
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("AutoLog — DI skeleton", style = MaterialTheme.typography.titleLarge)
        viewModels.forEach { vm ->
            Text("✓ ${vm::class.java.simpleName}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
