package com.example.autolog.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.example.autolog.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val anrTraceStore: AnrTraceStore,
) : ViewModel() {
    suspend fun exportReport(): File? = anrTraceStore.export()
}

/** 상단 바의 더보기 메뉴. 테스터가 "앱이 멈췄다"고 알려 줄 때 트레이스를 함께 보내는 길이다 (#88). */
@Composable
fun DiagnosticsMenu(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val emptyMessage = stringResource(R.string.diagnostics_empty)

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.diagnostics_more),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.diagnostics_send)) },
                onClick = {
                    expanded = false
                    scope.launch {
                        val report = viewModel.exportReport()
                        if (report == null) {
                            Toast.makeText(context, emptyMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            context.shareDiagnostics(report)
                        }
                    }
                },
            )
        }
    }
}

private fun Context.shareDiagnostics(report: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName.diagnostics", report)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostics_subject))
        // 공유 시트를 거치면 EXTRA_STREAM만으로는 읽기 권한이 받는 앱까지 넘어가지 않는다.
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, null))
}
