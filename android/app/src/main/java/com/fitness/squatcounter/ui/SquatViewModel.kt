package com.fitness.squatcounter.ui

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.squatcounter.audio.SquatAudioManager
import com.fitness.squatcounter.domain.AngleCalculator
import com.fitness.squatcounter.domain.AngleSmoother
import com.fitness.squatcounter.domain.FullBodyPose
import com.fitness.squatcounter.domain.LandmarkPoint
import com.fitness.squatcounter.domain.PoseFeatures
import com.fitness.squatcounter.domain.RejectionReason
import com.fitness.squatcounter.domain.SquatConfig
import com.fitness.squatcounter.domain.SquatEvent
import com.fitness.squatcounter.domain.SquatState
import com.fitness.squatcounter.domain.SquatStateMachine
import com.fitness.squatcounter.pose.PoseLandmarkerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SquatUiState(
    val repCount: Int = 0,
    val currentState: SquatState = SquatState.STANDING,
    val lastRejectionReason: RejectionReason = RejectionReason.NONE,
    val kneeAngle: Float = 0f,
    val hipAngle: Float = 0f,
    val fps: Int = 0,
    val isFullBodyVisible: Boolean = false,
    val landmarks: List<LandmarkPoint> = emptyList(),
    val errorMessage: String? = null,
    val creditBalanceSeconds: Int = 0
)

class SquatViewModel(
    context: Context,
    val config: SquatConfig = SquatConfig()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SquatUiState())
    val uiState: StateFlow<SquatUiState> = _uiState.asStateFlow()

    private val kneeSmoother = AngleSmoother(config.smoothingAlpha)
    private val hipSmoother = AngleSmoother(config.smoothingAlpha)
    val stateMachine = SquatStateMachine(config)
    private val audioManager = SquatAudioManager(context)

    // Room local ledger repository
    private val creditRepository = com.fitness.squatcounter.data.repository.CreditRepository(
        database = com.fitness.squatcounter.data.db.RepLockDatabase.getInstance(context),
        timeProvider = com.fitness.squatcounter.data.AndroidTimeProvider(context)
    )
    private var currentSessionId: String = java.util.UUID.randomUUID().toString()

    // FPS calculation tracking
    private var frameCount = 0
    private var fpsStartTime = System.currentTimeMillis()
    private var currentFps = 0

    private val poseLandmarkerHelper = PoseLandmarkerHelper(
        context = context,
        targetFps = 14,
        minConfidence = config.visibilityConfidenceThreshold,
        onPoseDetected = { fullBodyPose, allLandmarks, _ ->
            onPoseResults(fullBodyPose, allLandmarks)
        },
        onError = { error ->
            _uiState.update { it.copy(errorMessage = error) }
        }
    )

    init {
        // Start initial workout session
        viewModelScope.launch {
            creditRepository.startSession(currentSessionId, "squats")
        }

        // Collect derived credit balance
        viewModelScope.launch {
            creditRepository.balanceFlow.collect { balance ->
                _uiState.update { it.copy(creditBalanceSeconds = balance) }
            }
        }

        // Observe State Machine events to trigger audio, UI reactions, and ledger credits
        viewModelScope.launch {
            stateMachine.events.collect { event ->
                when (event) {
                    is SquatEvent.RepValidated -> {
                        audioManager.speakRep(event.repCount)
                        creditRepository.recordValidatedRep(currentSessionId, event.repCount, "squats")
                    }
                    is SquatEvent.TrackingLost -> {
                        audioManager.playTrackingLostBeep()
                    }
                    is SquatEvent.RepRejected -> {
                        creditRepository.recordRejectedRep(currentSessionId)
                    }
                    is SquatEvent.TrackingRegained -> {
                        // Tracking restored
                    }
                    is SquatEvent.StateChanged -> {
                        // State transition handled in UI state
                    }
                }
            }
        }
    }

    fun onCameraFrame(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        poseLandmarkerHelper.analyzeFrame(imageProxy, isFrontCamera)
    }

    private fun onPoseResults(pose: FullBodyPose?, allLandmarks: List<LandmarkPoint>) {
        updateFps()

        if (pose == null) {
            kneeSmoother.reset()
            hipSmoother.reset()
            stateMachine.processFrame(
                PoseFeatures(
                    rawKneeAngle = 0f,
                    smoothedKneeAngle = 0f,
                    rawHipAngle = 0f,
                    smoothedHipAngle = 0f,
                    areHipsAtOrBelowKnees = false,
                    isFullBodyVisible = false
                )
            )

            _uiState.update {
                it.copy(
                    currentState = stateMachine.currentState,
                    repCount = stateMachine.repCount,
                    lastRejectionReason = stateMachine.lastRejectionReason,
                    isFullBodyVisible = false,
                    landmarks = emptyList(),
                    fps = currentFps
                )
            }
            return
        }

        val isFullBodyVisible = pose.isFullBodyVisible(config.visibilityConfidenceThreshold)
        val (rawKnee, rawHip) = AngleCalculator.extractAngles(pose, use3D = false)
        val smoothedKnee = kneeSmoother.filter(rawKnee)
        val smoothedHip = hipSmoother.filter(rawHip)
        val hipsAtOrBelowKnees = pose.areHipsAtOrBelowKnees()

        val features = PoseFeatures(
            rawKneeAngle = rawKnee,
            smoothedKneeAngle = smoothedKnee,
            rawHipAngle = rawHip,
            smoothedHipAngle = smoothedHip,
            areHipsAtOrBelowKnees = hipsAtOrBelowKnees,
            isFullBodyVisible = isFullBodyVisible
        )

        stateMachine.processFrame(features)

        _uiState.update {
            it.copy(
                repCount = stateMachine.repCount,
                currentState = stateMachine.currentState,
                lastRejectionReason = stateMachine.lastRejectionReason,
                kneeAngle = smoothedKnee,
                hipAngle = smoothedHip,
                fps = currentFps,
                isFullBodyVisible = isFullBodyVisible,
                landmarks = allLandmarks
            )
        }
    }

    private fun updateFps() {
        frameCount++
        val now = System.currentTimeMillis()
        val elapsed = now - fpsStartTime
        if (elapsed >= 1000L) {
            currentFps = (frameCount * 1000L / elapsed).toInt()
            frameCount = 0
            fpsStartTime = now
        }
    }

    fun resetCounter() {
        val oldSessionId = currentSessionId
        currentSessionId = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            creditRepository.completeSession(oldSessionId)
            creditRepository.startSession(currentSessionId, "squats")
        }

        kneeSmoother.reset()
        hipSmoother.reset()
        stateMachine.reset()
        _uiState.update {
            it.copy(
                repCount = 0,
                currentState = SquatState.STANDING,
                lastRejectionReason = RejectionReason.NONE
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        val sessionToClose = currentSessionId
        viewModelScope.launch {
            creditRepository.completeSession(sessionToClose)
        }
        poseLandmarkerHelper.clear()
        audioManager.release()
    }
}
