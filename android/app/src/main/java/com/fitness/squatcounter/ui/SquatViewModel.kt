package com.fitness.squatcounter.ui

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.squatcounter.audio.SquatAudioManager
import com.fitness.squatcounter.data.CreditConfig
import com.fitness.squatcounter.data.SessionStatus
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
import com.fitness.squatcounter.session.FeedMonitor
import com.fitness.squatcounter.session.SessionConfig
import com.fitness.squatcounter.session.SessionEndReason
import com.fitness.squatcounter.session.SessionEvent
import com.fitness.squatcounter.session.SessionPhase
import com.fitness.squatcounter.session.SessionSignals
import com.fitness.squatcounter.session.SessionStateMachine
import com.fitness.squatcounter.session.SetupProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil

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
    val creditBalanceSeconds: Int = 0,
    // Session lifecycle
    val sessionPhase: SessionPhase = SessionPhase.IDLE,
    val setupProblem: SetupProblem = SetupProblem.NONE,
    val countdownSecondsLeft: Int = 0,
    val sessionEarnedSeconds: Int = 0,
    val sessionEndReason: SessionEndReason? = null,
    val feedHealthy: Boolean = true
) {
    /** Screen stays on only while a session is live. */
    val keepScreenOn: Boolean get() = sessionPhase.isLive
}

class SquatViewModel(
    context: Context,
    val config: SquatConfig = SquatConfig(),
    private val sessionConfig: SessionConfig = SessionConfig()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SquatUiState())
    val uiState: StateFlow<SquatUiState> = _uiState.asStateFlow()

    private val kneeSmoother = AngleSmoother(config.smoothingAlpha)
    private val hipSmoother = AngleSmoother(config.smoothingAlpha)
    val stateMachine = SquatStateMachine(config)
    private val audioManager = SquatAudioManager(context)

    // Session lifecycle
    private val session = SessionStateMachine(sessionConfig)
    private val feedMonitor = FeedMonitor(sessionConfig)
    private var activeSessionId: String? = null
    private var sessionEarnedSeconds = 0
    private var lastBodyVisible = false
    private var lastSpokenCountdown = -1

    // Read on the camera thread, written on the main thread.
    @Volatile
    private var analysisEnabled = false

    // Room local ledger repository
    private val creditRepository = com.fitness.squatcounter.data.repository.CreditRepository(
        database = com.fitness.squatcounter.data.db.RepLockDatabase.getInstance(context),
        timeProvider = com.fitness.squatcounter.data.AndroidTimeProvider(context)
    )

    // All ledger writes go through one queue, so order is guaranteed:
    // start session -> reps -> end session. A rep can never be written after its session ends.
    private val ledgerWrites = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    // Not cancelled in onCleared: viewModelScope is already cancelled by then.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // FPS calculation tracking
    private var frameCount = 0
    private var fpsStartTime = System.currentTimeMillis()
    private var currentFps = 0

    private val poseLandmarkerHelper = PoseLandmarkerHelper(
        context = context,
        targetFps = 14,
        minConfidence = config.visibilityConfidenceThreshold,
        onPoseDetected = { fullBodyPose, allLandmarks, timestampMs ->
            onPoseResults(fullBodyPose, allLandmarks, timestampMs)
        },
        onError = { error ->
            _uiState.update { it.copy(errorMessage = error) }
        }
    )

    init {
        // Synchronous listeners: the session phase at the moment of the event is exact.
        stateMachine.onEventListener = { event -> handleSquatEvent(event) }
        session.onEventListener = { event -> handleSessionEvent(event) }

        // Single consumer for ledger writes.
        viewModelScope.launch {
            for (write in ledgerWrites) {
                try {
                    write()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failed write must not kill the queue.
                }
            }
        }

        // Close sessions left open by a previous run (crash, kill, swipe-away).
        enqueueLedgerWrite { creditRepository.closeStaleOpenSessions() }

        // Collect derived credit balance
        viewModelScope.launch {
            creditRepository.balanceFlow.collect { balance ->
                _uiState.update { it.copy(creditBalanceSeconds = balance) }
            }
        }

        // Watchdog: notices a dead camera even when no frames arrive.
        viewModelScope.launch {
            while (true) {
                delay(sessionConfig.tickMs)
                onTick()
            }
        }
    }

    // ---- Controls (call from the main thread) ----

    fun startSession() {
        val now = SystemClock.uptimeMillis()
        lastBodyVisible = false
        session.start(now)
        publishSessionUi(now)
    }

    fun stopSession() {
        val now = SystemClock.uptimeMillis()
        session.stop(now)
        publishSessionUi(now)
    }

    /** App paused, screen off, etc. Ends a live session as INTERRUPTED. */
    fun onAppInterrupted() {
        val now = SystemClock.uptimeMillis()
        session.interrupt(now)
        publishSessionUi(now)
    }

    fun resetCounter() {
        val now = SystemClock.uptimeMillis()
        session.reset(now)
        kneeSmoother.reset()
        hipSmoother.reset()
        stateMachine.reset()
        sessionEarnedSeconds = 0
        _uiState.update {
            it.copy(
                repCount = 0,
                currentState = SquatState.STANDING,
                lastRejectionReason = RejectionReason.NONE,
                sessionEarnedSeconds = 0,
                landmarks = emptyList(),
                isFullBodyVisible = false
            )
        }
        publishSessionUi(now)
    }

    // ---- Camera / pose ----

    fun onCameraFrame(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (!analysisEnabled) {
            // No session running: skip inference to save battery and heat.
            imageProxy.close()
            return
        }
        poseLandmarkerHelper.analyzeFrame(imageProxy, isFrontCamera)
    }

    /** Called on MediaPipe's thread. Hop to the main thread so all state is single-threaded. */
    private fun onPoseResults(
        pose: FullBodyPose?,
        allLandmarks: List<LandmarkPoint>,
        timestampMs: Long
    ) {
        viewModelScope.launch {
            handlePose(pose, allLandmarks, timestampMs)
        }
    }

    /**
     * @param timestampMs monotonic time (SystemClock.uptimeMillis) from the pose helper.
     */
    private fun handlePose(
        pose: FullBodyPose?,
        allLandmarks: List<LandmarkPoint>,
        timestampMs: Long
    ) {
        if (!analysisEnabled) return
        updateFps()

        feedMonitor.onFrame(timestampMs, allLandmarks)
        lastBodyVisible = pose != null &&
            pose.isFullBodyVisible(config.visibilityConfidenceThreshold)
        session.update(timestampMs, currentSignals(timestampMs))
        publishSessionUi(timestampMs)

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
                ),
                timestampMs = timestampMs
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

        stateMachine.processFrame(features, timestampMs = timestampMs)

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

    // ---- Session plumbing ----

    private fun onTick() {
        if (!session.phase.isLive) return
        val now = SystemClock.uptimeMillis()
        session.update(now, currentSignals(now))
        publishSessionUi(now)
    }

    private fun currentSignals(nowMs: Long) = SessionSignals(
        bodyVisible = lastBodyVisible,
        feedHealthy = feedMonitor.isHealthy(nowMs),
        phoneStill = true // Step B2 replaces this with the accelerometer/gyro check
    )

    private fun handleSessionEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.PhaseChanged -> {
                analysisEnabled = event.to.isLive
                if (event.to == SessionPhase.SETUP_CHECK) {
                    feedMonitor.reset(event.atMs)
                }
                if (event.to == SessionPhase.COUNTDOWN) {
                    lastSpokenCountdown = -1
                }
                // Body loss already beeps via TrackingLost. Beep for other pause causes
                // (dead or frozen feed) so the user still hears it.
                if (event.from == SessionPhase.ACTIVE &&
                    event.to == SessionPhase.PAUSED &&
                    session.problem != SetupProblem.BODY_NOT_VISIBLE
                ) {
                    audioManager.playTrackingLostBeep()
                }
            }

            is SessionEvent.SessionActivated -> {
                val id = java.util.UUID.randomUUID().toString()
                activeSessionId = id
                sessionEarnedSeconds = 0
                // Fresh counters: rep numbers start at 1 for every session.
                kneeSmoother.reset()
                hipSmoother.reset()
                stateMachine.reset()
                enqueueLedgerWrite {
                    creditRepository.startSession(id, CreditConfig.EXERCISE_SQUATS)
                }
            }

            is SessionEvent.SessionEnded -> {
                val id = activeSessionId
                if (event.hadActiveSession && id != null) {
                    val status = if (event.reason == SessionEndReason.USER_STOP) {
                        SessionStatus.COMPLETED
                    } else {
                        SessionStatus.INTERRUPTED
                    }
                    enqueueLedgerWrite { creditRepository.endSession(id, status) }
                }
                activeSessionId = null
            }
        }
    }

    private fun handleSquatEvent(event: SquatEvent) {
        val phase = session.phase
        when (event) {
            is SquatEvent.RepValidated -> {
                val sessionId = activeSessionId ?: return
                // Reps only count while ACTIVE. Never during setup, countdown or pause.
                if (phase != SessionPhase.ACTIVE) return
                audioManager.speakRep(event.repCount)
                val repIndex = event.repCount
                enqueueLedgerWrite {
                    val earned = creditRepository.recordValidatedRep(
                        sessionId,
                        repIndex,
                        CreditConfig.EXERCISE_SQUATS
                    )
                    sessionEarnedSeconds += earned
                    _uiState.update { it.copy(sessionEarnedSeconds = sessionEarnedSeconds) }
                }
            }

            is SquatEvent.RepRejected -> {
                val sessionId = activeSessionId ?: return
                if (phase != SessionPhase.ACTIVE) return
                enqueueLedgerWrite { creditRepository.recordRejectedRep(sessionId) }
            }

            is SquatEvent.TrackingLost -> {
                if (phase == SessionPhase.ACTIVE || phase == SessionPhase.PAUSED) {
                    audioManager.playTrackingLostBeep()
                }
            }

            else -> Unit
        }
    }

    private fun enqueueLedgerWrite(block: suspend () -> Unit) {
        ledgerWrites.trySend(block)
    }

    private fun publishSessionUi(nowMs: Long) {
        val phase = session.phase

        val countdownLeft = if (phase == SessionPhase.COUNTDOWN) {
            ceil((sessionConfig.countdownMs - (nowMs - session.phaseStartedAtMs)) / 1000.0)
                .toInt()
                .coerceAtLeast(0)
        } else {
            0
        }

        // Speak each countdown digit once. Reuses the rep-number voice.
        if (phase == SessionPhase.COUNTDOWN && countdownLeft > 0 && countdownLeft != lastSpokenCountdown) {
            lastSpokenCountdown = countdownLeft
            audioManager.speakRep(countdownLeft)
        }

        _uiState.update {
            it.copy(
                sessionPhase = phase,
                setupProblem = session.problem,
                countdownSecondsLeft = countdownLeft,
                sessionEarnedSeconds = sessionEarnedSeconds,
                sessionEndReason = session.endReason,
                feedHealthy = feedMonitor.isHealthy(nowMs)
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

    override fun onCleared() {
        super.onCleared()
        // If a session is still open, close it as INTERRUPTED. Uses ioScope because
        // viewModelScope is already cancelled and would silently drop this.
        val id = activeSessionId
        if (id != null) {
            ioScope.launch { creditRepository.endSession(id, SessionStatus.INTERRUPTED) }
        }
        ledgerWrites.close()
        poseLandmarkerHelper.clear()
        audioManager.release()
    }
}