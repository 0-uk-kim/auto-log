package com.example.autolog

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.example.autolog.camera.CameraViewModel
import com.example.autolog.list.ClipListViewModel
import com.example.autolog.preview.PreviewViewModel
import com.example.autolog.ui.theme.AutoLogTheme
import com.example.autolog.ui.theme.RecordRed
import com.example.autolog.ui.theme.Spacing
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
                    PreflightScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// P0 #4에서 Navigation 그래프로 교체된다. 그 전까지 DI 그래프·권한 선언·방향 고정을 눈으로 확인하는 화면.
@Composable
private fun PreflightScreen(modifier: Modifier = Modifier) {
    val viewModels: List<ViewModel> = listOf(
        hiltViewModel<CameraViewModel>(),
        hiltViewModel<ClipListViewModel>(),
        hiltViewModel<PreviewViewModel>(),
        hiltViewModel<VlogViewModel>(),
    )
    val context = LocalContext.current
    val permissions = remember(context) { context.declaredPermissions() }
    val isPortrait = LocalResources.current.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("AutoLog — preflight", style = MaterialTheme.typography.titleLarge)

        Text("DI", style = MaterialTheme.typography.titleMedium)
        viewModels.forEach { vm ->
            Text("✓ ${vm::class.java.simpleName}", style = MaterialTheme.typography.bodyMedium)
        }

        Text("선언 권한", style = MaterialTheme.typography.titleMedium)
        permissions.forEach { permission ->
            Text("✓ ${permission.substringAfterLast('.')}", style = MaterialTheme.typography.bodyMedium)
        }

        Text("방향: ${if (isPortrait) "PORTRAIT (고정)" else "LANDSCAPE"}", style = MaterialTheme.typography.titleMedium)

        Text("색상 토큰", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(scheme.primary, scheme.primaryContainer, scheme.secondary, scheme.tertiary, RecordRed)
                .forEach { token ->
                    Spacer(
                        Modifier
                            .size(48.dp)
                            .background(token, MaterialTheme.shapes.small)
                    )
                }
        }
    }
}

private fun Context.declaredPermissions(): List<String> {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
    }
    return info.requestedPermissions.orEmpty().filter { it.startsWith("android.permission.") }
}
