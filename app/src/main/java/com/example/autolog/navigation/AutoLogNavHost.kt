package com.example.autolog.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.autolog.camera.CameraScreen
import com.example.autolog.edit.EditScreen
import com.example.autolog.list.ClipListScreen
import com.example.autolog.preview.PreviewScreen
import com.example.autolog.vlog.VlogScreen
import java.time.LocalDate

@Composable
fun AutoLogNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Camera,
        modifier = modifier,
        enterTransition = { slideIn(SlideDirection.Start, isTop = true) },
        exitTransition = { slideOut(SlideDirection.Start, isTop = false) },
        popEnterTransition = { slideIn(SlideDirection.End, isTop = false) },
        popExitTransition = { slideOut(SlideDirection.End, isTop = true) },
    ) {
        composable<Camera> {
            CameraScreen(
                onOpenClipList = { navController.navigate(ClipList(LocalDate.now().toString())) },
                onOpenLatestClip = {
                    navController.navigate(Preview(LocalDate.now().toString(), Preview.LATEST_CLIP))
                },
                onRecorded = { clipId -> navController.navigate(Edit(LocalDate.now().toString(), clipId)) },
            )
        }
        composable<ClipList> { entry ->
            val route = entry.toRoute<ClipList>()
            ClipListScreen(
                date = route.date,
                onOpenClip = { clipIndex -> navController.navigate(Preview(route.date, clipIndex)) },
                // 날짜를 고르면 목록을 갈아 끼운다 — 쌓으면 뒤로가기가 날짜 이력을 되짚게 된다.
                onSelectDate = { selected ->
                    navController.navigate(ClipList(selected.toString())) {
                        popUpTo<ClipList> { inclusive = true }
                    }
                },
                onCreateVlog = { navController.navigate(Vlog(route.date)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Preview> { entry ->
            val route = entry.toRoute<Preview>()
            PreviewScreen(
                date = route.date,
                clipIndex = route.clipIndex,
                onBack = { navController.popBackStack() },
                onEdit = { clipId -> navController.navigate(Edit(route.date, clipId)) },
            )
        }
        composable<Edit> {
            EditScreen(onDone = { navController.popBackStack() })
        }
        composable<Vlog> { entry ->
            VlogScreen(date = entry.toRoute<Vlog>().date, onBack = { navController.popBackStack() })
        }
    }
}

// 기본 전환은 700ms 페이드라 느리고 어디로 가는지 알 수 없다. 위에 쌓이는 화면은 오른쪽에서 밀려 들어오고
// 뒤로 가면 그대로 오른쪽으로 빠진다. 밑에 깔린 화면은 조금만 밀리고 어두워져 깊이가 보인다.
private const val TRANSITION_MS = 300
private const val REPLACE_MS = 150
private const val UNDER_DIM_ALPHA = 0.6f

private fun <T> slideSpec() = tween<T>(TRANSITION_MS, easing = FastOutSlowInEasing)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideIn(
    direction: SlideDirection,
    isTop: Boolean,
): EnterTransition = when {
    isSameScreen() -> fadeIn(tween(REPLACE_MS))
    isTop -> slideIntoContainer(direction, slideSpec())
    else -> slideIntoContainer(direction, slideSpec()) { it / 4 } + fadeIn(slideSpec(), UNDER_DIM_ALPHA)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOut(
    direction: SlideDirection,
    isTop: Boolean,
): ExitTransition = when {
    isSameScreen() -> fadeOut(tween(REPLACE_MS))
    isTop -> slideOutOfContainer(direction, slideSpec())
    else -> slideOutOfContainer(direction, slideSpec()) { it / 4 } + fadeOut(slideSpec(), UNDER_DIM_ALPHA)
}

/** 목록에서 날짜를 바꾸면 같은 화면을 갈아 끼운다 — 밀어 넣으면 다른 화면으로 간 것처럼 보인다. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isSameScreen(): Boolean =
    initialState.destination.hasRoute<ClipList>() && targetState.destination.hasRoute<ClipList>()
