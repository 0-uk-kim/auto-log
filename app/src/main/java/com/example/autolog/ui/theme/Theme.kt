package com.example.autolog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// 주 버튼은 흰 바탕·검은 글자, 켜진 상태는 노랑. 컨테이너는 한 단계씩 밝아지는 회색으로 깊이를 낸다.
private val ColorScheme = darkColorScheme(
    primary = TextPrimary,
    onPrimary = Ink,
    primaryContainer = Surface3,
    onPrimaryContainer = TextPrimary,
    secondary = CameraHighlight,
    onSecondary = Ink,
    secondaryContainer = Surface2,
    onSecondaryContainer = TextPrimary,
    tertiary = CameraHighlight,
    onTertiary = Ink,
    tertiaryContainer = CameraHighlight,
    onTertiaryContainer = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    surfaceTint = Ink,
    inverseSurface = Surface3,
    inverseOnSurface = TextPrimary,
    inversePrimary = CameraHighlight,
    outline = Outline,
    outlineVariant = Surface3,
    error = Danger,
    onError = Ink,
    errorContainer = DangerContainer,
    onErrorContainer = OnDangerContainer,
)

// 라이트 테마와 다이내믹 컬러는 쓰지 않는다. 촬영 → 목록 → 편집을 오가는 동안 바탕이 흰색·검정으로
// 번갈아 바뀌면 눈이 매번 적응해야 하고, 영상의 대비도 기기 설정에 따라 흔들린다.
@Composable
fun AutoLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
