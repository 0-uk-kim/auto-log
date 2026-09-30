package com.example.autolog.calendar

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.autolog.ui.rememberClipThumbnail
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.CameraHighlight
import com.example.autolog.ui.theme.CameraScrim
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.data.clip.CalendarMarks
import com.example.autolog.ui.GlassIconButton
import com.example.autolog.ui.theme.Saturday
import com.example.autolog.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

const val TAG_CALENDAR_SHEET = "calendar-sheet"
const val TAG_CALENDAR_MONTH = "calendar-month"

fun dayCellTag(date: LocalDate) = "calendar-day-$date"

private val VlogBadge = 16.dp

/** 다른 날짜의 목록으로 옮겨 가기 위한 달력 (planning 3-4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSheet(
    viewedDate: LocalDate,
    marks: CalendarMarks,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    // 시트를 닫으면 사라지는 상태다 — 다시 열면 보고 있는 날짜의 달에서 시작한다.
    var month by remember(viewedDate) { mutableStateOf(YearMonth.from(viewedDate)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 반만 펼치면 마지막 주와 범례가 잘린다 — 달력은 한 번에 다 보여야 고를 수 있다.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_CALENDAR_SHEET),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = Spacing.md)) {
            MonthHeader(
                month = month,
                onPreviousMonth = { month = month.minusMonths(1) },
                onNextMonth = { month = month.plusMonths(1) },
            )
            MonthSummary(month = month, marks = marks)
            WeekDayHeader()
            MonthGrid(
                month = month,
                today = today,
                viewedDate = viewedDate,
                marks = marks,
                onSelectDate = onSelectDate,
            )
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
    // 달 이름은 왼쪽에 크게, 넘기는 버튼은 오른쪽에 모은다 — 앞뒤로 여러 번 넘길 때 손가락이 옮겨 다니지 않는다.
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.calendar_month_title, month.year, month.monthValue),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f).testTag(TAG_CALENDAR_MONTH),
        )
        GlassIconButton(
            icon = R.drawable.ic_chevron,
            contentDescription = stringResource(R.string.calendar_previous_month),
            onClick = onPreviousMonth,
        )
        GlassIconButton(
            icon = R.drawable.ic_chevron,
            contentDescription = stringResource(R.string.calendar_next_month),
            onClick = onNextMonth,
            modifier = Modifier.rotate(180f),
        )
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
    onSelectDate: (LocalDate) -> Unit,
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
                                cover = marks.covers[date],
                                hasVlog = date in marks.datesWithVlog,
                                onClick = { onSelectDate(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 이 달에 몇 날을 찍었고 그중 몇 편을 브이로그로 만들었는지. 아직 안 만든 날이 몇인지가 곧 남은 일이다.
 */
@Composable
private fun MonthSummary(month: YearMonth, marks: CalendarMarks) {
    val shot = marks.datesWithClips.count { YearMonth.from(it) == month }
    val made = marks.datesWithVlog.count { YearMonth.from(it) == month }
    Text(
        text = if (shot == 0) {
            stringResource(R.string.calendar_summary_empty)
        } else {
            stringResource(R.string.calendar_summary, shot, made)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.sm),
    )
}

/**
 * 날짜 한 칸 (#20). 영상이 있는 날은 그날 첫 클립이 칸을 채운다 — 점 하나보다 "이날 뭘 찍었지"가 바로 보인다.
 * 브이로그를 만든 날은 오른쪽 아래에 노란 영상 배지가 붙는다. 영상이 없는 날은 숫자만 흐리게 둔다.
 *
 * 보고 있는 날짜는 흰 테두리, 오늘은 노란 숫자 — 표지·배지와 겹쳐도 서로 가리지 않게 표시 수단을 다르게 둔다.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isViewed: Boolean,
    cover: Uri?,
    hasVlog: Boolean,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    // 영상이 없는 날도 고를 수 있다 — 빈 목록이 "그날 안 찍었다"는 답이다 (#15).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(shape)
            .background(if (cover != null) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .border(2.dp, if (isViewed) MaterialTheme.colorScheme.onSurface else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .testTag(dayCellTag(date)),
    ) {
        if (cover != null) {
            rememberClipThumbnail(cover)?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 밝은 장면에서도 날짜 숫자가 읽히게 위쪽을 어둡게 깐다.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to CameraScrim, 0.7f to Color.Transparent)),
            )
        }

        Text(
            text = "${date.dayOfMonth}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (cover != null || isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isToday -> MaterialTheme.colorScheme.secondary
                cover != null -> CameraControlTint
                else -> date.dayOfWeek.labelColor().copy(alpha = EMPTY_DAY_ALPHA)
            },
            modifier = Modifier
                .align(if (cover != null) Alignment.TopStart else Alignment.Center)
                .padding(horizontal = Spacing.xs + 1.dp, vertical = 2.dp),
        )

        if (hasVlog) {
            VlogMark(modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp))
        }
    }
}

/** 브이로그를 만든 날의 표시. 목록의 「브이로그 보기」 버튼과 같은 영상 아이콘이라 뜻이 이어진다. */
@Composable
private fun VlogMark(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(VlogBadge)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_movie),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(10.dp),
        )
    }
}

/** 표지와 배지가 무엇을 뜻하는지 달력 안에서 바로 읽히게 한다. 범례도 실제 칸과 같은 모양으로 그린다. */
@Composable
private fun MarkerLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(R.string.calendar_legend_clips) {
            Box(
                Modifier
                    .size(width = 14.dp, height = 18.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Brush.verticalGradient(listOf(Saturday, CameraHighlight.copy(alpha = 0.7f)))),
            )
        }
        LegendItem(R.string.calendar_legend_vlog) { VlogMark() }
    }
}

@Composable
private fun LegendItem(@StringRes labelRes: Int, swatch: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = Spacing.sm + Spacing.xs, vertical = Spacing.xs + 2.dp),
    ) {
        swatch()
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val EMPTY_DAY_ALPHA = 0.55f

/** 주말은 색으로 구분한다 — 하루가 곧 브이로그 한 편이라 주말 단위로 훑는 일이 잦다. */
@Composable
private fun DayOfWeek.labelColor() = when (this) {
    DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
    DayOfWeek.SATURDAY -> Saturday
    else -> MaterialTheme.colorScheme.onSurface
}
