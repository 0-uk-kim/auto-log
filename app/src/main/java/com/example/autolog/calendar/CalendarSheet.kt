package com.example.autolog.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.data.clip.CalendarMarks
import com.example.autolog.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

const val TAG_CALENDAR_SHEET = "calendar-sheet"
const val TAG_CALENDAR_MONTH = "calendar-month"

private val DayCircle = 34.dp
private val MarkerDot = 6.dp

/**
 * 다른 날짜의 목록으로 옮겨 가기 위한 달력 (planning 3-4).
 *
 * 날짜를 골라 목록을 바꾸는 것은 #21에서 이 위에 얹힌다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSheet(
    viewedDate: LocalDate,
    marks: CalendarMarks,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    // 시트를 닫으면 사라지는 상태다 — 다시 열면 보고 있는 날짜의 달에서 시작한다.
    var month by remember(viewedDate) { mutableStateOf(YearMonth.from(viewedDate)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = modifier.testTag(TAG_CALENDAR_SHEET),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            MonthHeader(
                month = month,
                onPreviousMonth = { month = month.minusMonths(1) },
                onNextMonth = { month = month.plusMonths(1) },
            )
            WeekDayHeader()
            MonthGrid(month = month, today = today, viewedDate = viewedDate, marks = marks)
            MarkerLegend()
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = stringResource(R.string.calendar_previous_month),
            )
        }
        Text(
            text = stringResource(R.string.calendar_month_title, month.year, month.monthValue),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(TAG_CALENDAR_MONTH),
        )
        IconButton(onClick = onNextMonth) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = stringResource(R.string.calendar_next_month),
                modifier = Modifier.rotate(180f),
            )
        }
    }
}

@Composable
private fun WeekDayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        WeekDays.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = day.labelColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    viewedDate: LocalDate,
    marks: CalendarMarks,
) {
    Column {
        weeksOf(month).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isViewed = date == viewedDate,
                                hasClips = date in marks.datesWithClips,
                                hasVlog = date in marks.datesWithVlog,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 날짜 한 칸. 숫자 아래 점 두 종류가 "영상이 있는 날"과 "브이로그를 만든 날"을 가른다 (#20).
 *
 * 보고 있는 날짜는 채워서, 오늘은 굵게 — 둘이 겹칠 수 있으므로 표시 수단을 다르게 둔다.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isViewed: Boolean,
    hasClips: Boolean,
    hasVlog: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .size(DayCircle)
                .clip(CircleShape)
                .background(if (isViewed) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isViewed) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    date.dayOfWeek.labelColor()
                },
            )
        }

        // 점 자리는 표시가 없어도 항상 잡아 둔다 — 안 그러면 줄마다 숫자 높이가 어긋난다.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs / 2),
            modifier = Modifier.padding(top = Spacing.xs / 2),
        ) {
            Marker(visible = hasClips, color = MaterialTheme.colorScheme.primary)
            Marker(visible = hasVlog, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun Marker(visible: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .size(MarkerDot)
            .clip(CircleShape)
            .background(if (visible) color else Color.Transparent),
    )
}

/** 점 두 개가 무엇을 뜻하는지 달력 안에서 바로 읽히게 한다. */
@Composable
private fun MarkerLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(MaterialTheme.colorScheme.primary, R.string.calendar_legend_clips)
        LegendItem(MaterialTheme.colorScheme.tertiary, R.string.calendar_legend_vlog)
    }
}

@Composable
private fun LegendItem(color: Color, @StringRes labelRes: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marker(visible = true, color = color)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 주말은 색으로 구분한다 — 하루가 곧 브이로그 한 편이라 주말 단위로 훑는 일이 잦다. */
@Composable
private fun DayOfWeek.labelColor() = when (this) {
    DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
    DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface
}
