package com.example.autolog.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.autolog.R
import com.example.autolog.data.subtitle.Subtitle
import com.example.autolog.ui.theme.CameraBackground
import com.example.autolog.ui.theme.CameraControlTint
import com.example.autolog.ui.theme.Spacing
import java.util.Locale

const val TAG_SUBTITLE_ADD = "subtitle-add"
const val TAG_SUBTITLE_TEXT = "subtitle-text"
const val TAG_SUBTITLE_SAVE = "subtitle-save"

private val Dim = CameraControlTint.copy(alpha = 0.6f)

@Composable
internal fun SubtitleList(
    subtitles: List<Subtitle>,
    onAdd: () -> Unit,
    onSelect: (Subtitle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.subtitle_section),
                style = MaterialTheme.typography.titleSmall,
                color = CameraControlTint,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CameraControlTint,
                    contentColor = CameraBackground,
                ),
                modifier = Modifier.testTag(TAG_SUBTITLE_ADD),
            ) {
                Text(stringResource(R.string.subtitle_add))
            }
        }

        if (subtitles.isEmpty()) {
            Text(
                text = stringResource(R.string.subtitle_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
        } else {
            // 영상이 화면 대부분을 차지해야 하므로 목록은 몇 줄만 보이고 나머지는 스크롤한다.
            LazyColumn(Modifier.heightIn(max = 160.dp)) {
                items(subtitles, key = { it.id }) { subtitle ->
                    SubtitleRow(subtitle = subtitle, onClick = { onSelect(subtitle) })
                }
            }
        }
    }
}

@Composable
private fun SubtitleRow(subtitle: Subtitle, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(
                R.string.subtitle_range,
                formatSubtitleTime(subtitle.startMs),
                formatSubtitleTime(subtitle.endMs),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = Dim,
        )
        Text(
            text = subtitle.text,
            style = MaterialTheme.typography.bodyMedium,
            color = CameraControlTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 자막 한 줄을 만들거나 고친다. 시작·끝 버튼은 **지금 재생 위치**를 그 자리에 찍는다 —
 * 시크바로 위치를 옮기고 누르는 식이라 숫자를 입력할 일이 없다.
 */
@Composable
internal fun SubtitleEditor(
    draft: SubtitleDraft,
    onMarkStart: () -> Unit,
    onMarkEnd: () -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MarkButton(
                label = stringResource(R.string.subtitle_mark_start, formatSubtitleTime(draft.startMs)),
                onClick = onMarkStart,
                modifier = Modifier.weight(1f),
            )
            MarkButton(
                label = stringResource(R.string.subtitle_mark_end, formatSubtitleTime(draft.endMs)),
                onClick = onMarkEnd,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = draft.text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.subtitle_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CameraControlTint,
                unfocusedTextColor = CameraControlTint,
                focusedBorderColor = CameraControlTint,
                unfocusedBorderColor = Dim,
                cursorColor = CameraControlTint,
                focusedPlaceholderColor = Dim,
                unfocusedPlaceholderColor = Dim,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_SUBTITLE_TEXT),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (draft.id != 0L) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.subtitle_delete), color = Color(0xFFFF8A80))
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.subtitle_cancel), color = CameraControlTint)
                }
                Button(
                    onClick = onSave,
                    enabled = draft.canSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CameraControlTint,
                        contentColor = CameraBackground,
                        disabledContainerColor = CameraControlTint.copy(alpha = 0.2f),
                        disabledContentColor = Dim,
                    ),
                    modifier = Modifier.testTag(TAG_SUBTITLE_SAVE),
                ) {
                    Text(stringResource(R.string.subtitle_save))
                }
            }
        }
    }
}

@Composable
private fun MarkButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.subtitle_mark_description)
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CameraControlTint),
        modifier = modifier.semantics { onClick(label = description) { onClick(); true } },
    ) {
        Text(label)
    }
}

/** 자막 경계는 초 단위로는 거칠어서 0.1초까지 보여 준다 — `0:02.4`. */
fun formatSubtitleTime(ms: Long): String {
    val tenths = (ms.coerceAtLeast(0) / 100)
    val minutes = tenths / 600
    val seconds = (tenths % 600) / 10
    return String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths % 10)
}
