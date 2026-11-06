package com.fitness.squatcounter.domain

/**
 * One exercise's counting logic. Every new exercise implements this, and the rest of the app
 * (session, audio, credits, UI) works with it unchanged.
 *
 * Detectors reuse [SquatState], [SquatEvent] and [RejectionReason]. For rep exercises
 * STANDING means "at the start position" (standing, or the top of a push-up).
 * Not thread-safe: call from one thread only.
 */
interface ExerciseDetector {
    val repCount: Int
    val currentState: SquatState
    val lastRejectionReason: RejectionReason

    var onEventListener: ((SquatEvent) -> Unit)?

    /** Label under the big number on screen. */
    val countLabel: String
        get() = "REPS"

    /** The big number on screen: reps for rep exercises, seconds for a hold. */
    val displayCount: Int
        get() = repCount

    /** The number spoken aloud when the Nth counted unit is completed. */
    fun spokenNumber(unitCount: Int): Int = unitCount

    /** True when everything this exercise needs is clearly visible in the frame. */
    fun isBodyVisible(landmarks: List<LandmarkPoint>, visibilityThreshold: Float): Boolean

    /** Feed one frame of the 33 pose landmarks. Events go to [onEventListener]. */
    fun processFrame(landmarks: List<LandmarkPoint>, timestampMs: Long, visibilityThreshold: Float)

    /** Start of a session: counters cleared and the starting position measured again. */
    fun reset()

    /** For a rejected rep: how far it got versus what is needed. Null if not applicable. */
    fun rejectionDetail(): String?

    /** Extra lines for the Debug panel. */
    fun debugInfo(): String
}

object ExerciseRegistry {

    /**
     * Returns the detector for an exercise, or null for squats, which still use their own
     * dedicated full-body and close-up detectors inside the ViewModel.
     */
    fun create(type: ExerciseType): ExerciseDetector? = when (type) {
        ExerciseType.SQUATS -> null
        ExerciseType.PUSHUPS -> PushUpDetector()
        ExerciseType.PLANK -> PlankDetector()
        ExerciseType.LUNGES -> LungeDetector()
        ExerciseType.JUMPING_JACKS -> JumpingJackDetector()
    }
}