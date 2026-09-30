package com.example.autolog.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.autolog.ui.theme.AccentDeep
import com.example.autolog.ui.theme.CameraHighlight
import com.example.autolog.ui.theme.Glass
import com.example.autolog.ui.theme.GlassBorder
import com.example.autolog.ui.theme.ListDimens
import com.example.autolog.ui.theme.Spacing

// 화면마다 버튼 자리를 같게 둔다 — 위 왼쪽은 나가기, 위 오른쪽은 이 화면에서 할 수 있는 부가 동작,
// 아래 가운데는 이 화면을 끝내는 주 동작. 어느 화면에 있든 엄지가 같은 자리를 찾는다.

private val AccentGradient = Brush.horizontalGradient(listOf(CameraHighlight, AccentDeep))

/** 위 바. 가운데 제목은 양옆 버튼 폭과 무관하게 화면 가운데에 선다. */
@Composable
fun ScreenTopBar(
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit = {},
    title: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ListDimens.topBar)
            .padding(horizontal = Spacing.sm + Spacing.xs),
    ) {
        Box(Modifier.align(Alignment.CenterStart)) { navigation() }
        Box(Modifier.align(Alignment.Center)) { title() }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = actions,
        )
    }
}

/** 반투명 원형 버튼. 영상 위든 빈 바탕 위든 같은 모양으로 읽힌다. */
@Composable
fun GlassIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(ListDimens.glassButton)
            .clip(CircleShape)
            .background(Glass)
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 글자가 붙은 반투명 알약 버튼. 아이콘만으로는 뜻이 갈리는 부가 동작에 쓴다. */
@Composable
fun GlassPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(ListDimens.glassPill)
            .clip(CircleShape)
            .background(Glass)
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.xs + 2.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

enum class ActionStyle { Primary, Secondary, Destructive }

/**
 * 화면을 끝내는 주 동작. 주 동작은 노랑 그라데이션, 나란히 서는 보조 동작은 유리, 되돌릴 수 없는 삭제는 빨강이다.
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    style: ActionStyle = ActionStyle.Primary,
) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when {
        !enabled -> Brush.linearGradient(listOf(colors.surfaceContainerHighest, colors.surfaceContainerHighest)) to
            colors.onSurfaceVariant
        style == ActionStyle.Primary -> AccentGradient to colors.onSecondary
        style == ActionStyle.Destructive -> Brush.linearGradient(listOf(colors.error, colors.error)) to colors.onError
        else -> Brush.linearGradient(listOf(Glass, Glass)) to colors.onSurface
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(ListDimens.primaryButton)
            .clip(CircleShape)
            .background(background)
            .then(if (style == ActionStyle.Secondary) Modifier.border(BorderStroke(1.dp, GlassBorder), CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg),
    ) {
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = content)
    }
}

/**
 * 목록 위에 떠 있는 아래 버튼 자리. 위쪽이 바탕색으로 서서히 짙어져, 밑으로 흘러가는 목록과 버튼이 겹쳐도 글자가 묻히지 않는다.
 */
@Composable
fun BottomDock(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to background))
            .navigationBarsPadding()
            .padding(start = Spacing.md, end = Spacing.md, top = Spacing.xl, bottom = Spacing.sm + Spacing.xs),
        content = content,
    )
}

/** 빈 화면·실패 화면 맨 위의 큰 아이콘. 노란 빛이 은은하게 번져 빈 화면이 비어 보이지 않는다. */
@Composable
fun GlowIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, tint: Color = CameraHighlight) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(96.dp)
            .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.22f), Color.Transparent)), CircleShape),
    ) {
        GlowCore { Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun GlowCore(content: @Composable BoxScope.() -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Glass)
            .border(1.dp, GlassBorder, CircleShape),
        content = content,
    )
}

private const val DISABLED_ALPHA = 0.35f
