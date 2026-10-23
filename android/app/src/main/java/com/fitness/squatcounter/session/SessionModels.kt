package com.fitness.squatcounter.session

/**
 * All session thresholds in one place. Values are starting guesses; tune on the phone.
 */
data class SessionConfig(
    /** Full body must be visible, with a healthy camera feed, for this long before the countdown starts. */
    val setupHoldMs: Long = 2_000L,

    /** "Get ready" countdown. Reps are not counted during it. */
    val countdownMs: Long = 5_000L,

    /** If a problem appears during the countdown for this long, go back to the setup check. */
    val countdownProblemGraceMs: Long = 1_000L,

    /** If a session stays paused this long, it ends as INTERRUPTED. */
    val pausedTimeoutMs: Long = 30_000L,

    /** No new pose result for this long means the camera feed is lost. */
    val staleFrameTimeoutMs: Long = 2_000L,

    /** This many consecutive identical-landmark frames means the feed is frozen. */
    val frozenFrameCount: Int = 20,

    /** Max per-axis difference (normalized units) still treated as "identical". */
    val frozenLandmarkEpsilon: Float = 1e-6f,

    /** How often the watchdog re-checks the session while no frames arrive. */
    val tickMs: Long = 250L
)

enum class SessionPhase {
    IDLE,
    SETUP_CHECK,
    COUNTDOWN,
    ACTIVE,
    PAUSED,
    ENDED;

    /** True while a session is in progress (camera analysis on, screen kept awake). */
    val isLive: Boolean get() = this != IDLE && this != ENDED
}

enum class SessionEndReason {
    /** User tapped Stop. Session is saved as COMPLETED. */
    USER_STOP,

    /** Paused too long. Session is saved as INTERRUPTED. */
    PAUSE_TIMEOUT,

    /** App backgrounded, screen off, etc. Session is saved as INTERRUPTED. */
    INTERRUPTED
}

enum class SetupProblem {
    NONE,
    BODY_NOT_VISIBLE,
    FEED_LOST,
    PHONE_MOVING
}

/**
 * Inputs the session needs on every update.
 */
data class SessionSignals(
    val bodyVisible: Boolean,
    val feedHealthy: Boolean,
    val phoneStill: Boolean
)

sealed interface SessionEvent {
    data class PhaseChanged(
        val from: SessionPhase,
        val to: SessionPhase,
        val atMs: Long
    ) : SessionEvent

    /** Fired once, when the countdown finishes and the session first becomes ACTIVE. */
    data object SessionActivated : SessionEvent

    /**
     * @param hadActiveSession false if it ended during setup/countdown, so nothing was ever recorded.
     */
    data class SessionEnded(
        val reason: SessionEndReason,
        val hadActiveSession: Boolean
    ) : SessionEvent
}