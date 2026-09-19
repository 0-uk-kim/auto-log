package com.example.autolog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.autolog.navigation.AutoLogNavHost
import com.example.autolog.ui.theme.AutoLogTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoLogTheme {
                // 카메라가 시스템 바 아래까지 꽉 차야 해서 인셋은 화면별로 처리한다.
                AutoLogNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
