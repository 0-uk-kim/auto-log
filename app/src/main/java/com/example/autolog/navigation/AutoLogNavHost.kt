package com.example.autolog.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.autolog.camera.CameraScreen
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
    ) {
        composable<Camera> {
            CameraScreen(
                onOpenClipList = { navController.navigate(ClipList(LocalDate.now().toString())) },
                onOpenLatestClip = {
                    navController.navigate(Preview(LocalDate.now().toString(), Preview.LATEST_CLIP))
                },
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
            )
        }
        composable<Vlog> { entry ->
            VlogScreen(date = entry.toRoute<Vlog>().date, onBack = { navController.popBackStack() })
        }
    }
}
