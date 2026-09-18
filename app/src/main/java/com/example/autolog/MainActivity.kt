package com.example.autolog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AutoLogNavHost(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
