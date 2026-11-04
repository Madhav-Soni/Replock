package com.fitness.squatcounter.ui.components

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * CameraX live preview and ImageAnalysis pipeline.
 * Uses the front or back camera, keeps only the latest frame, drops frames promptly.
 *
 * The preview is shown letterboxed (FIT_CENTER), so everything the camera sees is visible
 * on screen and the skeleton overlay lines up exactly.
 *
 * @param useFrontCamera true = front camera, false = back camera. Changing it rebinds the camera.
 * @param onFrame called with each frame and whether it came from the front camera
 */
@Composable
fun CameraPreview(
    useFrontCamera: Boolean,
    modifier: Modifier = Modifier,
    onFrame: (ImageProxy, Boolean) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentOnFrame by rememberUpdatedState(onFrame)

    // Remembers which lens is currently bound, so we only rebind when it actually changes.
    val boundLens = remember { arrayOfNulls<Boolean>(1) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
        },
        update = { previewView ->
            if (boundLens[0] != useFrontCamera) {
                boundLens[0] = useFrontCamera
                bindCamera(
                    previewView = previewView,
                    lifecycleOwner = lifecycleOwner,
                    executor = cameraExecutor,
                    useFront = useFrontCamera,
                    onFrame = { proxy, front -> currentOnFrame(proxy, front) }
                )
            }
        }
    )
}

private fun bindCamera(
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    executor: Executor,
    useFront: Boolean,
    onFrame: (ImageProxy, Boolean) -> Unit
) {
    val context = previewView.context
    val providerFuture = ProcessCameraProvider.getInstance(context)

    providerFuture.addListener({
        val provider = providerFuture.get()

        fun tryBind(front: Boolean): Boolean {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(executor) { proxy -> onFrame(proxy, front) }
                }

            val selector = if (front) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            return try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                true
            } catch (e: Exception) {
                false
            }
        }

        // If the requested camera is unavailable, fall back to the other one.
        if (!tryBind(useFront)) {
            tryBind(!useFront)
        }
    }, ContextCompat.getMainExecutor(context))
}