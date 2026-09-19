package com.example.autolog.list

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.autolog.R
import com.example.autolog.permission.MediaAccess
import com.example.autolog.ui.theme.Spacing

const val TAG_LIST_LOADING = "clip-list-loading"
const val TAG_EMPTY_NONE = "clip-list-empty"
const val TAG_EMPTY_DENIED = "clip-list-denied"
const val TAG_EMPTY_PARTIAL = "clip-list-partial"
const val TAG_ACCESS_BANNER = "clip-list-access-banner"

@Composable
fun ClipListLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_LIST_LOADING),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * 0건일 때 **왜** 0건인지까지 말한다.
 *
 * 셋은 사용자가 할 일이 다르다 — 찍으면 되는지, 권한을 주면 되는지, 항목을 더 고르면 되는지.
 * 하나의 "영상이 없습니다"로 뭉뚱그리면 권한 문제를 앱 버그로 읽게 된다 (#15, #35).
 */
@Composable
fun ClipListEmpty(
    access: MediaAccess,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (access) {
        MediaAccess.Full -> EmptyMessage(
            title = R.string.clip_list_empty_title,
            hint = R.string.clip_list_empty_hint,
            testTag = TAG_EMPTY_NONE,
            modifier = modifier,
        )

        MediaAccess.Partial -> EmptyMessage(
            title = R.string.clip_list_partial_title,
            hint = R.string.clip_list_partial_hint,
            testTag = TAG_EMPTY_PARTIAL,
            modifier = modifier,
        ) {
            Button(onClick = onRequestAccess) {
                Text(stringResource(R.string.clip_list_action_select_more))
            }
        }

        MediaAccess.Denied -> EmptyMessage(
            title = R.string.clip_list_denied_title,
            hint = R.string.clip_list_denied_hint,
            testTag = TAG_EMPTY_DENIED,
            modifier = modifier,
        ) {
            Button(onClick = onRequestAccess) {
                Text(stringResource(R.string.clip_list_action_allow))
            }
            // 영구 거부면 요청 다이얼로그가 아예 안 뜬다. 설정으로 가는 길을 항상 같이 둔다.
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.clip_list_action_settings))
            }
        }
    }
}

/**
 * 목록이 비지 않았어도 **다 보이는 게 아닐 수 있다**는 사실을 알린다.
 * 권한 없이 읽히는 것은 이번 설치에서 직접 찍은 클립뿐이라, 재설치 전 촬영분은 조용히 빠진다.
 */
@Composable
fun MediaAccessBanner(
    access: MediaAccess,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (access == MediaAccess.Full) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = Spacing.md, end = Spacing.sm)
            .testTag(TAG_ACCESS_BANNER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(
                if (access == MediaAccess.Partial) {
                    R.string.clip_list_banner_partial
                } else {
                    R.string.clip_list_banner_denied
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = onRequestAccess) {
            Text(
                stringResource(
                    if (access == MediaAccess.Partial) {
                        R.string.clip_list_action_select_more
                    } else {
                        R.string.clip_list_action_allow
                    },
                ),
            )
        }
    }
}

@Composable
private fun EmptyMessage(
    @StringRes title: Int,
    @StringRes hint: Int,
    testTag: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            actions()
        }
    }
}
