package com.example.autolog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 녹화 경과 시간·클립 길이처럼 바뀌는 숫자는 폭이 흔들리지 않게 tabular 숫자를 쓴다.
// 고정폭 글꼴은 같은 효과를 내지만 한글과 섞이면 타자기처럼 보인다.
private const val TabularNumbers = "tnum"

private val Base = Typography()

val Typography = Base.copy(
    headlineSmall = Base.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = Base.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = Base.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = Base.bodyLarge.copy(letterSpacing = 0.sp),
    bodyMedium = Base.bodyMedium.copy(letterSpacing = 0.sp),
    bodySmall = Base.bodySmall.copy(letterSpacing = 0.sp),
    labelLarge = Base.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = TabularNumbers,
    ),
    labelMedium = Base.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        fontFeatureSettings = TabularNumbers,
    ),
    labelSmall = Base.labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        fontFeatureSettings = TabularNumbers,
    ),
)
