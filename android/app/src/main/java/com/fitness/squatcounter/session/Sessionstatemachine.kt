package com.fitness.squatcounter.session

/**
 * Session lifecycle: IDLE -> SETUP_CHECK -> COUNTDOWN -> ACTIVE <-> PAUSED -> ENDED.
 *
 * Pure Kotlin. Time is always passed in (monotonic milliseconds), so it is fully
 * testable with fake time. Not thread-safe: call it from one thread only.
 */
class SessionStateMachine(
    val config: SessionConfig = SessionConfig()
) {
    var phase: SessionPhase = SessionPhase.IDLE
        private set

    /** What is currently blocking the session (NONE if nothing). */
    var problem: SetupProblem = SetupProblem.NONE
        private set

    var endReason: SessionEndReason? = null
        private set

    /** When the current phase began. Used to draw the countdown. */
    var phaseStartedAtMs: Long = 0L
        private set

    var onEventListener: ((SessionEvent) -> Unit)? = null

    private var okSinceMs: Long? = null
    private var problemSinceMs: Long? = null
    private var pausedSinceMs: Long = 0L
    private var activated: Boolean = false

    /** User tapped Start. Works from IDLE or ENDED. */
    fun start(nowMs: Long) {
        if (phase.isLive) return
        endReason = null
        problem = SetupProblem.NONE
        okSinceMs = null
        problemSinceMs = null
        activated = false
        transitionTo(SessionPhase.SETUP_CHECK, nowMs)
    }

    /** User tapped Stop (or Cancel during setup). */
    fun stop(nowMs: Long) {
        if (phase.isLive) end(SessionEndReason.USER_STOP, nowMs)
    }

    /** App backgrounded, screen off, camera error, etc. */
    fun interrupt(nowMs: Long) {
        if (phase.isLive) end(SessionEndReason.INTERRUPTED, nowMs)
    }

    /** Back to IDLE. A live session is ended as a user stop first. */
    fun reset(nowMs: Long) {
        if (phase.isLive) end(SessionEndReason.USER_STOP, nowMs)
        endReason = null
        problem = SetupProblem.NONE
        transitionTo(SessionPhase.IDLE, nowMs)
    }

    /**
     * Call on every pose result AND on a regular timer, so a dead camera is still noticed.
     */
    fun update(nowMs: Long, signals: SessionSignals) {
        if (!phase.isLive) return

        val current = problemOf(signals)
        problem = current

        when (phase) {
            SessionPhase.SETUP_CHECK -> updateSetup(nowMs, current)
            SessionPhase.COUNTDOWN -> updateCountdown(nowMs, current)
            SessionPhase.ACTIVE -> {
                if (current != SetupProblem.NONE) {
                    pausedSinceMs = nowMs
                    transitionTo(SessionPhase.PAUSED, nowMs)
                }
            }
            SessionPhase.PAUSED -> {
                if (current == SetupProblem.NONE) {
                    transitionTo(SessionPhase.ACTIVE, nowMs)
                } else if (nowMs - pausedSinceMs >= config.pausedTimeoutMs) {
                    end(SessionEndReason.PAUSE_TIMEOUT, nowMs)
                }
            }
            SessionPhase.IDLE, SessionPhase.ENDED -> Unit
        }
    }

    private fun updateSetup(nowMs: Long, current: SetupProblem) {
        if (current == SetupProblem.NONE) {
            val since = okSinceMs ?: nowMs
            okSinceMs = since
            if (nowMs - since >= config.setupHoldMs) {
                okSinceMs = null
                problemSinceMs = null
                transitionTo(SessionPhase.COUNTDOWN, nowMs)
            }
        } else {
            okSinceMs = null
        }
    }

    private fun updateCountdown(nowMs: Long, current: SetupProblem) {
        if (current != SetupProblem.NONE) {
            val since = problemSinceMs ?: nowMs
            problemSinceMs = since
            if (nowMs - since >= config.countdownProblemGraceMs) {
                problemSinceMs = null
                okSinceMs = null
                transitionTo(SessionPhase.SETUP_CHECK, nowMs)
                return
            }
        } else {
            problemSinceMs = null
        }

        if (nowMs - phaseStartedAtMs >= config.countdownMs) {
            activated = true
            transitionTo(SessionPhase.ACTIVE, nowMs)
            emit(SessionEvent.SessionActivated)
        }
    }

    private fun end(reason: SessionEndReason, nowMs: Long) {
        endReason = reason
        val hadActiveSession = activated
        transitionTo(SessionPhase.ENDED, nowMs)
        emit(SessionEvent.SessionEnded(reason, hadActiveSession))
    }

    private fun problemOf(signals: SessionSignals): SetupProblem = when {
        !signals.feedHealthy -> SetupProblem.FEED_LOST
        !signals.bodyVisible -> SetupProblem.BODY_NOT_VISIBLE
        !signals.phoneStill -> SetupProblem.PHONE_MOVING
        else -> SetupProblem.NONE
    }

    private fun transitionTo(newPhase: SessionPhase, nowMs: Long) {
        if (phase == newPhase) return
        val old = phase
        phase = newPhase
        phaseStartedAtMs = nowMs
        emit(SessionEvent.PhaseChanged(old, newPhase, nowMs))
    }

    private fun emit(event: SessionEvent) {
        onEventListener?.invoke(event)
    }
}