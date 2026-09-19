package com.example.autolog.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class CameraViewModel @Inject constructor() : ViewModel() {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest = _surfaceRequest.asStateFlow()

    // 1차는 9:16 세로 고정이라 프리뷰도 16:9 선택 전략으로 못 박는다 (planning 6-1).
    private val previewUseCase = Preview.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build(),
        )
        .build()
        .apply { setSurfaceProvider { request -> _surfaceRequest.value = request } }

    /** 호출한 코루틴이 취소될 때까지 바인딩을 유지한다. 화면을 떠나면 자동으로 해제된다. */
    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(appContext)
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            previewUseCase,
        )
        try {
            awaitCancellation()
        } finally {
            cameraProvider.unbindAll()
        }
    }
}
