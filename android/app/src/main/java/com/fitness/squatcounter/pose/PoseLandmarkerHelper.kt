package com.fitness.squatcounter.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.fitness.squatcounter.domain.FullBodyPose
import com.fitness.squatcounter.domain.LandmarkPoint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Manages MediaPipe Pose Landmarker lifecycle and execution in LIVE_STREAM mode.
 * Throttles input to 10-15 fps, dropping frames promptly to avoid memory pressure on Redmi 10.
 */
class PoseLandmarkerHelper(
    private val context: Context,
    private val targetFps: Int = 14,
    private val minConfidence: Float = 0.5f,
    private val onPoseDetected: (FullBodyPose?, List<LandmarkPoint>, Long) -> Unit,
    private val onError: (String) -> Unit
) {
    private var poseLandmarker: PoseLandmarker? = null
    private var lastAnalyzedTimestamp: Long = 0L
    private val minFrameIntervalMs = 1000L / targetFps

    @Volatile
    private var isAnalyzing = false

    init {
        setupPoseLandmarker()
    }

    private fun setupPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(minConfidence)
                .setMinPosePresenceConfidence(minConfidence)
                .setMinTrackingConfidence(minConfidence)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: PoseLandmarkerResult, inputImage: MPImage ->
                    handleDetectionResult(result)
                }
                .setErrorListener { error: RuntimeException ->
                    isAnalyzing = false
                    onError(error.message ?: "Unknown MediaPipe error")
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            onError("Failed to initialize MediaPipe Pose Landmarker: ${e.message}")
        }
    }

    /**
     * Accepts a frame from CameraX ImageAnalysis.
     * Drops frames if incoming faster than target FPS (~14 fps) or if previous frame is still in inference.
     */
    fun analyzeFrame(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val currentTime = SystemClock.uptimeMillis()

        // Throttle frames to target 10-15 fps, and drop if detector is busy
        if (isAnalyzing || (currentTime - lastAnalyzedTimestamp < minFrameIntervalMs)) {
            imageProxy.close()
            return
        }

        val landmarker = poseLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        try {
            isAnalyzing = true
            lastAnalyzedTimestamp = currentTime

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()

            // Close imageProxy immediately so CameraX buffer is returned to pool promptly
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                if (isFrontCamera) {
                    // Mirror horizontally for selfie camera feel
                    postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            landmarker.detectAsync(mpImage, currentTime)
        } catch (e: Exception) {
            isAnalyzing = false
            imageProxy.close()
        }
    }

    private fun handleDetectionResult(result: PoseLandmarkerResult) {
        isAnalyzing = false

        val landmarksList = result.landmarks()
        if (landmarksList.isNullOrEmpty() || landmarksList[0].isEmpty()) {
            onPoseDetected(null, emptyList(), SystemClock.uptimeMillis())
            return
        }

        val landmarks = landmarksList[0]
        val allPoints = landmarks.map { lm ->
            LandmarkPoint(
                x = lm.x(),
                y = lm.y(),
                z = lm.z(),
                visibility = lm.visibility().orElse(1.0f)
            )
        }

        // MediaPipe Pose 33 landmarks indices:
        // 11: left_shoulder, 12: right_shoulder
        // 23: left_hip, 24: right_hip
        // 25: left_knee, 26: right_knee
        // 27: left_ankle, 28: right_ankle
        if (allPoints.size > 28) {
            val fullBodyPose = FullBodyPose(
                leftShoulder = allPoints[11],
                rightShoulder = allPoints[12],
                leftHip = allPoints[23],
                rightHip = allPoints[24],
                leftKnee = allPoints[25],
                rightKnee = allPoints[26],
                leftAnkle = allPoints[27],
                rightAnkle = allPoints[28],
                isWorldCoordinates = false
            )
            onPoseDetected(fullBodyPose, allPoints, SystemClock.uptimeMillis())
        } else {
            onPoseDetected(null, allPoints, SystemClock.uptimeMillis())
        }
    }

    fun clear() {
        poseLandmarker?.close()
        poseLandmarker = null
    }
}
