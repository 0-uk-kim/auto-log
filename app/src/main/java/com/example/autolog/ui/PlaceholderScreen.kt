package com.example.autolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.autolog.ui.theme.Spacing

/**
 * 각 화면의 실제 UI가 들어오기 전까지 라우팅만 확인하는 껍데기.
 * P1~P6에서 화면별로 하나씩 걷어낸다.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    route: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .padding(Spacing.lg)
                .testTag(title),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(route, style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}
