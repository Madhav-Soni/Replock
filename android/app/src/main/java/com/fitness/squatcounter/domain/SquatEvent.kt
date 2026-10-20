package com.fitness.squatcounter.domain

/**
 * Event stream emitted by the exercise state machine.
 */
sealed interface SquatEvent {
    data class RepValidated(val repCount: Int) : SquatEvent
    data class RepRejected(val reason: RejectionReason) : SquatEvent
    data object TrackingLost : SquatEvent
    data object TrackingRegained : SquatEvent
    data class StateChanged(val oldState: SquatState, val newState: SquatState) : SquatEvent
}
